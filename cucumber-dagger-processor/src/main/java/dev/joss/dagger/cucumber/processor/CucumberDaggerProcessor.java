package dev.joss.dagger.cucumber.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Annotation processor that generates the Dagger subcomponent wiring needed by the cucumber-dagger
 * runtime.
 *
 * <p>Triggered by {@code @CucumberDaggerConfiguration}. For each annotated root component interface
 * it generates the following source files in the same package:
 *
 * <ul>
 *   <li><strong>{@code GeneratedScopedModule}</strong> — a Dagger {@code @Module} that optionally
 *       includes any user modules that contain {@code @Provides @CucumberScoped} methods (Style B).
 *   <li><strong>{@code GeneratedScopedComponent}</strong> — a Dagger {@code @Subcomponent} scoped
 *       with {@code @CucumberScoped} that declares provision methods for all scenario-scoped types
 *       and step-definition classes discovered in the glue package.
 *   <li><strong>{@code CucumberDaggerModule}</strong> — the module that users must include in their
 *       root {@code @Component}. Declares the subcomponent and provides a raw-type binding so that
 *       the root component can expose {@code CucumberScopedComponent.Builder} via {@code
 *       CucumberDaggerComponent#scopedComponentBuilder()}.
 *   <li><strong>{@code dev.joss.dagger.cucumber.generated.CucumberScopedComponentAccessor}</strong>
 *       — a simple accessor class that returns the {@code GeneratedScopedComponent} class literal,
 *       allowing the runtime to avoid a classpath scan at startup.
 *   <li><strong>{@code META-INF/services/…CucumberDaggerComponent}</strong> — service file entry
 *       pointing to the Dagger-generated root component factory ({@code DaggerXxx}).
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public final class CucumberDaggerProcessor extends AbstractProcessor {

  private static final String API_PKG = "dev.joss.dagger.cucumber.api";
  private static final String GENERATED_PKG = "dev.joss.dagger.cucumber.generated";
  private static final String CUCUMBER_SCOPED = API_PKG + ".CucumberScoped";
  private static final String DAGGER_COMPONENT = "dagger.Component";
  private static final String DAGGER_PROVIDES = "dagger.Provides";
  private static final String JAVAX_INJECT = "javax.inject.Inject";

  @Override
  public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) return false;

    TypeElement configAnnotation =
        processingEnv.getElementUtils().getTypeElement(API_PKG + ".CucumberDaggerConfiguration");
    if (configAnnotation == null) return false;

    Set<? extends Element> configElements = roundEnv.getElementsAnnotatedWith(configAnnotation);
    if (configElements.isEmpty()) return false;

    if (configElements.size() > 1) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Only one @CucumberDaggerConfiguration is allowed");
      return true;
    }

    TypeElement rootComponent = (TypeElement) configElements.iterator().next();
    String rootPackage =
        processingEnv.getElementUtils().getPackageOf(rootComponent).getQualifiedName().toString();

    // Step A: find @CucumberScoped classes in the glue package
    TypeElement cucumberScopedType =
        processingEnv.getElementUtils().getTypeElement(CUCUMBER_SCOPED);
    List<TypeElement> scopedClasses = new ArrayList<>();
    if (cucumberScopedType != null) {
      for (Element e : roundEnv.getElementsAnnotatedWith(cucumberScopedType)) {
        if (e.getKind() != ElementKind.CLASS) continue;
        TypeElement te = (TypeElement) e;
        String pkg = processingEnv.getElementUtils().getPackageOf(te).getQualifiedName().toString();
        if (pkg.equals(rootPackage) || pkg.startsWith(rootPackage + ".")) {
          scopedClasses.add(te);
        }
      }
    }

    // Step B: find user modules on @Component with @Provides @CucumberScoped methods
    List<TypeElement> userScopedModules = new ArrayList<>();
    // Map from return TypeName -> method name for deduplication across Style A and Style B
    Map<TypeName, String> scopedProvisionMethods = new LinkedHashMap<>();

    // Populate Style A first
    for (TypeElement scopedClass : scopedClasses) {
      TypeName typeName = TypeName.get(scopedClass.asType());
      String methodName = decapitalize(scopedClass.getSimpleName().toString());
      scopedProvisionMethods.put(typeName, methodName);
    }

    TypeElement daggerComponentType =
        processingEnv.getElementUtils().getTypeElement(DAGGER_COMPONENT);
    TypeElement daggerProvidesType =
        processingEnv.getElementUtils().getTypeElement(DAGGER_PROVIDES);
    TypeElement cucumberScopedAnnoType =
        processingEnv.getElementUtils().getTypeElement(CUCUMBER_SCOPED);

    if (daggerComponentType != null) {
      List<TypeMirror> moduleTypes =
          extractModulesFromComponent(rootComponent, daggerComponentType);
      for (TypeMirror moduleMirror : moduleTypes) {
        TypeElement moduleElement =
            (TypeElement) processingEnv.getTypeUtils().asElement(moduleMirror);
        if (moduleElement == null) continue;
        boolean isUserScopedModule = false;
        for (Element enclosed : moduleElement.getEnclosedElements()) {
          if (enclosed.getKind() != ElementKind.METHOD) continue;
          ExecutableElement method = (ExecutableElement) enclosed;
          boolean hasProvides = hasAnnotation(method, daggerProvidesType);
          boolean hasCucumberScoped = hasAnnotation(method, cucumberScopedAnnoType);
          if (hasProvides && hasCucumberScoped) {
            isUserScopedModule = true;
            TypeName returnTypeName = TypeName.get(method.getReturnType());
            String methodName =
                decapitalize(
                    method.getReturnType().toString().replaceAll(".*\\.", "")); // simple name
            scopedProvisionMethods.putIfAbsent(returnTypeName, methodName);
          }
        }
        if (isUserScopedModule) {
          userScopedModules.add(moduleElement);
        }
      }
    }

    // Find step defs: @Inject constructors in glue package, excluding @CucumberScoped classes
    TypeElement injectType = processingEnv.getElementUtils().getTypeElement(JAVAX_INJECT);
    Map<TypeName, String> stepDefMethods = new LinkedHashMap<>();
    if (injectType != null) {
      for (Element e : roundEnv.getElementsAnnotatedWith(injectType)) {
        if (e.getKind() != ElementKind.CONSTRUCTOR) continue;
        TypeElement enclosing = (TypeElement) e.getEnclosingElement();
        if (scopedClasses.contains(enclosing)) continue;
        String pkg =
            processingEnv.getElementUtils().getPackageOf(enclosing).getQualifiedName().toString();
        if (!pkg.equals(rootPackage) && !pkg.startsWith(rootPackage + ".")) continue;
        TypeName typeName = TypeName.get(enclosing.asType());
        String methodName = decapitalize(enclosing.getSimpleName().toString());
        stepDefMethods.put(typeName, methodName);
      }
    }

    // Validate: every @CucumberScoped class must have an @Inject constructor
    if (injectType != null) {
      for (TypeElement scopedClass : scopedClasses) {
        boolean hasInjectConstructor = false;
        for (Element enclosed : scopedClass.getEnclosedElements()) {
          if (enclosed.getKind() != ElementKind.CONSTRUCTOR) continue;
          if (hasAnnotation((ExecutableElement) enclosed, injectType)) {
            hasInjectConstructor = true;
            break;
          }
        }
        if (!hasInjectConstructor) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "@CucumberScoped class must have an @Inject constructor",
                  scopedClass);
        }
      }
    }

    // Generate files
    generateGeneratedScopedModule(rootPackage, userScopedModules);
    generateGeneratedScopedComponent(rootPackage, scopedProvisionMethods, stepDefMethods);
    generateCucumberDaggerModule(rootPackage);
    generateCucumberScopedComponentAccessor(rootPackage);
    generateServiceFile(rootPackage, rootComponent.getSimpleName().toString());

    return true;
  }

  /**
   * Generates {@code GeneratedScopedModule} — a Dagger {@code @Module} used by {@code
   * GeneratedScopedComponent}. If the user has provided Style-B scoped modules (containing
   * {@code @Provides @CucumberScoped} methods), they are listed in the {@code includes} attribute.
   */
  private void generateGeneratedScopedModule(
      String rootPackage, List<TypeElement> userScopedModules) {
    AnnotationSpec.Builder moduleAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Module"));
    if (!userScopedModules.isEmpty()) {
      CodeBlock.Builder includes = CodeBlock.builder().add("{");
      for (int i = 0; i < userScopedModules.size(); i++) {
        if (i > 0) includes.add(", ");
        includes.add("$T.class", ClassName.get(userScopedModules.get(i)));
      }
      includes.add("}");
      moduleAnnotation.addMember("includes", includes.build());
    }

    TypeSpec moduleType =
        TypeSpec.classBuilder("GeneratedScopedModule")
            .addAnnotation(moduleAnnotation.build())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();

    writeJavaFile(rootPackage, moduleType);
  }

  /**
   * Generates {@code GeneratedScopedComponent} — a {@code @CucumberScoped @Subcomponent} that
   * exposes a provision method for every scenario-scoped type and every step-definition class. Also
   * generates the inner {@code Builder} interface.
   *
   * @param scopedProvisionMethods map of return type → method name for Style-A and Style-B objects
   * @param stepDefMethods map of return type → method name for step-definition classes
   */
  private void generateGeneratedScopedComponent(
      String rootPackage,
      Map<TypeName, String> scopedProvisionMethods,
      Map<TypeName, String> stepDefMethods) {

    ClassName generatedScopedModuleName = ClassName.get(rootPackage, "GeneratedScopedModule");
    ClassName generatedScopedComponentName = ClassName.get(rootPackage, "GeneratedScopedComponent");
    ClassName cucumberScopedComponentName = ClassName.get(API_PKG, "CucumberScopedComponent");

    AnnotationSpec cucumberScopedAnnotation =
        AnnotationSpec.builder(ClassName.get(API_PKG, "CucumberScoped")).build();
    AnnotationSpec subcomponentAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"))
            .addMember("modules", "$T.class", generatedScopedModuleName)
            .build();

    TypeSpec.Builder componentBuilder =
        TypeSpec.interfaceBuilder("GeneratedScopedComponent")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(cucumberScopedAnnotation)
            .addAnnotation(subcomponentAnnotation)
            .addSuperinterface(cucumberScopedComponentName);

    // Style A + Style B provision methods
    for (Map.Entry<TypeName, String> entry : scopedProvisionMethods.entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    // Factory methods for step defs
    for (Map.Entry<TypeName, String> entry : stepDefMethods.entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    // Inner Builder interface
    ParameterizedTypeName builderSuperInterface =
        ParameterizedTypeName.get(
            ClassName.get(API_PKG, "CucumberScopedComponent", "Builder"),
            generatedScopedComponentName);
    TypeSpec builderInterface =
        TypeSpec.interfaceBuilder("Builder")
            .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
            .addSuperinterface(builderSuperInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .build();

    componentBuilder.addType(builderInterface);

    writeJavaFile(rootPackage, componentBuilder.build());
  }

  /**
   * Generates {@code CucumberDaggerModule} — the module users must include in their root
   * {@code @Component}. Declares {@code GeneratedScopedComponent} as a subcomponent and provides a
   * raw-type {@code CucumberScopedComponent.Builder} binding that bridges the generated builder to
   * the interface method declared on {@code CucumberDaggerComponent}.
   */
  private void generateCucumberDaggerModule(String rootPackage) {
    ClassName generatedScopedComponentName = ClassName.get(rootPackage, "GeneratedScopedComponent");
    ClassName generatedScopedBuilderName =
        ClassName.get(rootPackage, "GeneratedScopedComponent", "Builder");
    ClassName cucumberScopedBuilderName =
        ClassName.get(API_PKG, "CucumberScopedComponent", "Builder");

    AnnotationSpec moduleAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Module"))
            .addMember("subcomponents", "$T.class", generatedScopedComponentName)
            .build();

    AnnotationSpec suppressWarnings =
        AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "rawtypes").build();

    MethodSpec providesMethod =
        MethodSpec.methodBuilder("provideScopedBuilder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(ClassName.get("dagger", "Provides"))
            .addAnnotation(suppressWarnings)
            .returns(cucumberScopedBuilderName)
            .addParameter(generatedScopedBuilderName, "builder")
            .addStatement("return builder")
            .build();

    TypeSpec moduleType =
        TypeSpec.classBuilder("CucumberDaggerModule")
            .addJavadoc(
                "Generated by cucumber-dagger-processor."
                    + " Include in your @Component modules list.")
            .addAnnotation(moduleAnnotation)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addMethod(providesMethod)
            .build();

    writeJavaFile(rootPackage, moduleType);
  }

  /**
   * Generates {@code dev.joss.dagger.cucumber.generated.CucumberScopedComponentAccessor} — a simple
   * class with a {@code getScopedComponentClass()} method that returns the {@code
   * GeneratedScopedComponent} class literal. The runtime uses this to avoid a classpath scan on
   * every test run.
   */
  private void generateCucumberScopedComponentAccessor(String rootPackage) {
    ClassName cucumberScopedComponentName = ClassName.get(API_PKG, "CucumberScopedComponent");
    ClassName generatedScopedComponentName = ClassName.get(rootPackage, "GeneratedScopedComponent");

    ParameterizedTypeName returnType =
        ParameterizedTypeName.get(
            ClassName.get(Class.class), WildcardTypeName.subtypeOf(cucumberScopedComponentName));

    MethodSpec method =
        MethodSpec.methodBuilder("getScopedComponentClass")
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType)
            .addStatement("return $T.class", generatedScopedComponentName)
            .build();

    TypeSpec accessorType =
        TypeSpec.classBuilder("CucumberScopedComponentAccessor")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(method)
            .build();

    writeJavaFile(GENERATED_PKG, accessorType);
  }

  /**
   * Writes the {@code META-INF/services/…CucumberDaggerComponent} service file containing the
   * fully-qualified name of the Dagger-generated component factory ({@code DaggerXxx}). Silently
   * ignores a {@link javax.annotation.processing.FilerException} because the file may already exist
   * from a prior processing round.
   */
  private void generateServiceFile(String rootPackage, String rootComponentSimpleName) {
    String serviceFile = "META-INF/services/" + API_PKG + ".CucumberDaggerComponent";
    String implementation = rootPackage + ".Dagger" + rootComponentSimpleName;
    try {
      FileObject fo =
          processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", serviceFile);
      try (Writer w = fo.openWriter()) {
        w.write(implementation);
        w.write(System.lineSeparator());
      }
    } catch (FilerException e) {
      // idempotent — already written in a prior round
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Failed to write service file: " + e.getMessage());
    }
  }

  /**
   * Writes a generated Java file to the filer. Silently ignores {@link
   * javax.annotation.processing.FilerException} (file already exists from a prior round) and
   * reports other {@link java.io.IOException}s as compile errors.
   */
  private void writeJavaFile(String packageName, TypeSpec typeSpec) {
    JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();
    try {
      javaFile.writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
      // idempotent — file already generated in a prior round
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR, "Failed to write " + typeSpec.name + ": " + e.getMessage());
    }
  }

  /**
   * Returns the list of module {@link TypeMirror}s declared in the {@code modules} attribute of the
   * {@code @Component} annotation on {@code rootComponent}.
   *
   * <p>During the first annotation-processing round, types that haven't been generated yet (such as
   * the generated {@code CucumberDaggerModule}) are not yet resolvable and appear as raw strings
   * rather than {@link TypeMirror} instances. These are silently skipped.
   */
  private List<TypeMirror> extractModulesFromComponent(
      TypeElement rootComponent, TypeElement daggerComponentType) {
    List<TypeMirror> result = new ArrayList<>();
    for (AnnotationMirror am : rootComponent.getAnnotationMirrors()) {
      if (!processingEnv
          .getTypeUtils()
          .isSameType(am.getAnnotationType(), daggerComponentType.asType())) continue;
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
          am.getElementValues().entrySet()) {
        if (!entry.getKey().getSimpleName().contentEquals("modules")) continue;
        @SuppressWarnings("unchecked")
        List<? extends AnnotationValue> moduleValues =
            (List<? extends AnnotationValue>) entry.getValue().getValue();
        for (AnnotationValue av : moduleValues) {
          Object val = av.getValue();
          if (val instanceof TypeMirror) {
            result.add((TypeMirror) val);
          }
          // else: unresolvable type (e.g. CucumberDaggerModule before generation) — skip
        }
      }
    }
    return result;
  }

  /**
   * Returns {@code true} if {@code element} carries the annotation represented by {@code
   * annotationType}.
   */
  private boolean hasAnnotation(ExecutableElement element, TypeElement annotationType) {
    if (annotationType == null) return false;
    for (AnnotationMirror am : element.getAnnotationMirrors()) {
      if (processingEnv
          .getTypeUtils()
          .isSameType(am.getAnnotationType(), annotationType.asType())) {
        return true;
      }
    }
    return false;
  }

  /** Returns {@code name} with its first character lower-cased, for use as a method name. */
  private static String decapitalize(String name) {
    if (name.isEmpty()) return name;
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }
}
