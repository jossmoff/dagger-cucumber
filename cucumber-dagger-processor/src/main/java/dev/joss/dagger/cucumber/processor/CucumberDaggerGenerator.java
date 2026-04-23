package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/**
 * Generates all source files and resources needed by the cucumber-dagger runtime.
 *
 * <p>Each file is generated at most once per processor lifetime, tracked via an explicit set of
 * qualified names rather than relying on {@link javax.annotation.processing.FilerException} as a
 * sentinel.
 */
final class CucumberDaggerGenerator {

  private static final String API_PKG = KnownTypes.API_PKG;
  private static final String GENERATED_PKG = KnownTypes.GENERATED_PKG;

  private final ProcessingEnvironment processingEnv;

  /** Qualified names of Java sources already written; guards against duplicate generation. */
  private final Set<String> generatedFiles = new HashSet<>();

  CucumberDaggerGenerator(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  /**
   * Generates all outputs for the given model. Invoked once per processing round in which a
   * {@code @CucumberDaggerConfiguration} element is found.
   */
  void generate(ProcessingModel model) {
    generateGeneratedScopedModule(model);
    generateGeneratedScopedComponent(model);
    generateCucumberDaggerModule(model);
    generateComponentResolver(model);
    generateCucumberRootComponent(model);
    generateServiceFile(model);
  }

  /**
   * Generates {@code GeneratedScopedModule} - a Dagger {@code @Module} used by {@code
   * GeneratedScopedComponent}. If the user has provided scoped modules (containing
   * {@code @Provides @ScenarioScope} methods), they are listed in the {@code includes} attribute.
   */
  private void generateGeneratedScopedModule(ProcessingModel model) {
    AnnotationSpec.Builder moduleAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Module"));
    if (!model.userScopedModules().isEmpty()) {
      CodeBlock.Builder includes = CodeBlock.builder().add("{");
      for (int i = 0; i < model.userScopedModules().size(); i++) {
        if (i > 0) includes.add(", ");
        includes.add("$T.class", ClassName.get(model.userScopedModules().get(i)));
      }
      includes.add("}");
      moduleAnnotation.addMember("includes", includes.build());
    }

    TypeSpec moduleType =
        TypeSpec.classBuilder("GeneratedScopedModule")
            .addAnnotation(moduleAnnotation.build())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .build();

    writeJavaFile(model.rootPackage(), moduleType, model.rootComponent());
  }

  /**
   * Generates {@code GeneratedScopedComponent} - a {@code @ScenarioScope @Subcomponent} that
   * exposes a provision method for every scenario-scoped type and every step-definition class. Also
   * generates the inner {@code Builder} interface.
   */
  private void generateGeneratedScopedComponent(ProcessingModel model) {
    ClassName generatedScopedModuleName =
        ClassName.get(model.rootPackage(), "GeneratedScopedModule");
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");
    ClassName scenarioScopedComponentName = ClassName.get(API_PKG, "ScenarioScopedComponent");

    AnnotationSpec scenarioScopeAnnotation =
        AnnotationSpec.builder(ClassName.get(API_PKG, "ScenarioScope")).build();
    AnnotationSpec subcomponentAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Subcomponent"))
            .addMember("modules", "$T.class", generatedScopedModuleName)
            .build();

    TypeSpec.Builder componentBuilder =
        TypeSpec.interfaceBuilder("GeneratedScopedComponent")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(scenarioScopeAnnotation)
            .addAnnotation(subcomponentAnnotation)
            .addSuperinterface(scenarioScopedComponentName);

    for (Map.Entry<TypeName, String> entry : model.scopedProvisionMethods().entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    for (Map.Entry<TypeName, String> entry : model.stepDefMethods().entrySet()) {
      componentBuilder.addMethod(
          MethodSpec.methodBuilder(entry.getValue())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .returns(entry.getKey())
              .build());
    }

    ParameterizedTypeName builderSuperInterface =
        ParameterizedTypeName.get(
            ClassName.get(API_PKG, "ScenarioScopedComponent", "Builder"),
            generatedScopedComponentName);

    TypeSpec builderInterface =
        TypeSpec.interfaceBuilder("Builder")
            .addAnnotation(ClassName.get("dagger", "Subcomponent", "Builder"))
            .addSuperinterface(builderSuperInterface)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .build();

    componentBuilder.addType(builderInterface);

    writeJavaFile(model.rootPackage(), componentBuilder.build(), model.rootComponent());
  }

  /**
   * Generates {@code CucumberDaggerModule} - declares {@code GeneratedScopedComponent} as a
   * subcomponent and provides the raw-type {@code ScenarioScopedComponent.Builder} binding that
   * bridges the generated builder to the interface method on {@code CucumberDaggerComponent}.
   */
  private void generateCucumberDaggerModule(ProcessingModel model) {
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");
    ClassName generatedScopedBuilderName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent", "Builder");
    ClassName scenarioScopedBuilderName =
        ClassName.get(API_PKG, "ScenarioScopedComponent", "Builder");

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
            .returns(scenarioScopedBuilderName)
            .addParameter(generatedScopedBuilderName, "builder")
            .addStatement("return builder")
            .build();

    TypeSpec moduleType =
        TypeSpec.classBuilder("CucumberDaggerModule")
            .addJavadoc(
                "Generated by cucumber-dagger-processor."
                    + " This module is included automatically by the generated wrapper component;"
                    + " you do not need to add it to your @Component modules list.")
            .addAnnotation(moduleAnnotation)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addMethod(providesMethod)
            .build();

    writeJavaFile(model.rootPackage(), moduleType, model.rootComponent());
  }

  /**
   * Generates {@code dev.joss.dagger.cucumber.generated.GeneratedComponentResolver} - a
   * type-dispatching implementation of {@link dev.joss.dagger.cucumber.api.ComponentResolver} that
   * replaces all runtime reflection. The generated class:
   *
   * <ul>
   *   <li>{@code createScoped} - delegates to {@code root.scopedComponentBuilder().build()}.
   *   <li>{@code resolveScoped} - casts to {@code GeneratedScopedComponent} and dispatches via
   *       {@code if (type == X.class)} chains for scoped and step-def provision methods.
   *   <li>{@code resolveRoot} - casts to the user's root component interface and dispatches via
   *       {@code if (type == X.class)} chains for root provision methods.
   * </ul>
   *
   * <p>Root component instantiation is handled at runtime by {@link
   * dev.joss.dagger.cucumber.internal.DaggerBackend}; the service file identifies the generated
   * factory class, and the runtime determines which instantiation path to use.
   */
  private void generateComponentResolver(ProcessingModel model) {
    ClassName componentResolverName = ClassName.get(API_PKG, "ComponentResolver");
    ClassName cucumberDaggerComponentName = ClassName.get(API_PKG, "CucumberDaggerComponent");
    ClassName scenarioScopedComponentName = ClassName.get(API_PKG, "ScenarioScopedComponent");
    ClassName generatedScopedComponentName =
        ClassName.get(model.rootPackage(), "GeneratedScopedComponent");
    ClassName rootInterfaceName = ClassName.get(model.rootComponent());

    ParameterizedTypeName classWildcard =
        ParameterizedTypeName.get(
            ClassName.get(Class.class), WildcardTypeName.subtypeOf(ClassName.OBJECT));

    // createScoped
    MethodSpec createScoped =
        MethodSpec.methodBuilder("createScoped")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(scenarioScopedComponentName)
            .addParameter(cucumberDaggerComponentName, "root")
            .addStatement("return root.scopedComponentBuilder().build()")
            .build();

    // resolveScoped
    MethodSpec.Builder resolveScopedBuilder =
        MethodSpec.methodBuilder("resolveScoped")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(classWildcard, "type")
            .addParameter(scenarioScopedComponentName, "scoped");

    resolveScopedBuilder.addStatement(
        "$T comp = ($T) scoped", generatedScopedComponentName, generatedScopedComponentName);
    for (Map.Entry<TypeName, String> entry : model.scopedProvisionMethods().entrySet()) {
      resolveScopedBuilder.addStatement(
          "if (type == $T.class) return comp.$L()", entry.getKey(), entry.getValue());
    }
    for (Map.Entry<TypeName, String> entry : model.stepDefMethods().entrySet()) {
      resolveScopedBuilder.addStatement(
          "if (type == $T.class) return comp.$L()", entry.getKey(), entry.getValue());
    }
    resolveScopedBuilder.addStatement("return null");

    // resolveRoot
    MethodSpec.Builder resolveRootBuilder =
        MethodSpec.methodBuilder("resolveRoot")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object.class)
            .addParameter(classWildcard, "type")
            .addParameter(cucumberDaggerComponentName, "root");

    if (!model.rootProvisionMethods().isEmpty()) {
      resolveRootBuilder.addStatement("$T comp = ($T) root", rootInterfaceName, rootInterfaceName);
      for (Map.Entry<TypeName, String> entry : model.rootProvisionMethods().entrySet()) {
        resolveRootBuilder.addStatement(
            "if (type == $T.class) return comp.$L()", entry.getKey(), entry.getValue());
      }
    }
    resolveRootBuilder.addStatement("return null");

    TypeSpec resolverType =
        TypeSpec.classBuilder("GeneratedComponentResolver")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(componentResolverName)
            .addMethod(createScoped)
            .addMethod(resolveScopedBuilder.build())
            .addMethod(resolveRootBuilder.build())
            .build();

    writeJavaFile(GENERATED_PKG, resolverType, model.rootComponent());
  }

  /**
   * Generates {@code GeneratedCucumber{UserSimpleName}} - a wrapper {@code @Component} that
   * combines the user's modules with the generated {@code CucumberDaggerModule}. The wrapper also
   * extends the user's root component interface so that Dagger generates implementations for its
   * provision methods, enabling type-safe dispatch in {@code GeneratedComponentResolver}.
   *
   * <p>When the user's root component declares a {@code @Component.Builder} inner interface, the
   * generated wrapper includes a matching {@code @Component.Builder} that extends the user's
   * builder with a covariant {@code build()} return type. This causes Dagger to generate a {@code
   * builder()} factory on the {@code DaggerXxx} class; at runtime {@link
   * dev.joss.dagger.cucumber.internal.DaggerBackend} falls back to {@code builder().build()} when
   * no {@code create()} is present.
   */
  private void generateCucumberRootComponent(ProcessingModel model) {
    ClassName cucumberDaggerComponentName = ClassName.get(API_PKG, "CucumberDaggerComponent");
    ClassName cucumberDaggerModuleName = ClassName.get(model.rootPackage(), "CucumberDaggerModule");

    // Scoped modules are included by GeneratedScopedModule (subcomponent) - exclude them here to
    // avoid Dagger's "module repeated in subcomponent" error.
    Set<String> scopedModuleNames = new HashSet<>();
    for (TypeElement m : model.userScopedModules()) {
      scopedModuleNames.add(m.getQualifiedName().toString());
    }

    CodeBlock.Builder modulesBlock = CodeBlock.builder().add("{");
    boolean first = true;
    for (TypeMirror moduleMirror : model.userModules()) {
      TypeElement moduleElement =
          (TypeElement) processingEnv.getTypeUtils().asElement(moduleMirror);
      if (moduleElement != null
          && scopedModuleNames.contains(moduleElement.getQualifiedName().toString())) continue;
      if (!first) modulesBlock.add(", ");
      first = false;
      modulesBlock.add("$T.class", TypeName.get(moduleMirror));
    }
    if (!first) modulesBlock.add(", ");
    modulesBlock.add("$T.class}", cucumberDaggerModuleName);

    AnnotationSpec componentAnnotation =
        AnnotationSpec.builder(ClassName.get("dagger", "Component"))
            .addMember("modules", modulesBlock.build())
            .build();

    String simpleName = model.rootComponent().getSimpleName().toString();
    String wrapperSimpleName = "GeneratedCucumber" + simpleName;
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(wrapperSimpleName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(componentAnnotation)
            .addSuperinterface(cucumberDaggerComponentName)
            .addSuperinterface(ClassName.get(model.rootComponent()));

    for (AnnotationMirror scope : model.scopeAnnotations()) {
      builder.addAnnotation(AnnotationSpec.get(scope));
    }

    // If the user declared @Component.Builder, generate a matching builder on the wrapper that
    // extends the user's builder with a covariant terminal-method return type. This ensures Dagger
    // generates builder() on DaggerGeneratedCucumber{Name} instead of only create().
    if (model.componentBuilder() != null) {
      ClassName wrapperName = ClassName.get(model.rootPackage(), wrapperSimpleName);
      ClassName userBuilderName = ClassName.get(model.componentBuilder());
      TypeMirror rootComponentType = model.rootComponent().asType();
      // Detect the terminal method: the zero-arg method on the builder returning the root
      // component.
      String terminalMethodName =
          model.componentBuilder().getEnclosedElements().stream()
              .filter(e -> e.getKind() == ElementKind.METHOD)
              .map(e -> (ExecutableElement) e)
              .filter(
                  e ->
                      e.getParameters().isEmpty()
                          && processingEnv
                              .getTypeUtils()
                              .isAssignable(e.getReturnType(), rootComponentType))
              .map(e -> e.getSimpleName().toString())
              .findFirst()
              .orElse(null);
      if (terminalMethodName == null) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@Component.Builder has no zero-arg terminal method returning the root component."
                    + " Add a method returning "
                    + model.rootComponent().getSimpleName()
                    + " with no parameters.",
                model.componentBuilder());
        return;
      }
      TypeSpec wrapperBuilderInterface =
          TypeSpec.interfaceBuilder("Builder")
              .addAnnotation(
                  AnnotationSpec.builder(ClassName.get("dagger", "Component", "Builder")).build())
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .addSuperinterface(userBuilderName)
              .addMethod(
                  MethodSpec.methodBuilder(terminalMethodName)
                      .addAnnotation(Override.class)
                      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                      .returns(wrapperName)
                      .build())
              .build();
      builder.addType(wrapperBuilderInterface);
    }

    writeJavaFile(model.rootPackage(), builder.build(), model.rootComponent());
  }

  /**
   * Writes the {@code META-INF/services/…CucumberDaggerComponent} service file pointing to the
   * Dagger-generated factory for the wrapper component.
   */
  private void generateServiceFile(ProcessingModel model) {
    String serviceFileName = "META-INF/services/" + API_PKG + ".CucumberDaggerComponent";
    if (!generatedFiles.add(serviceFileName)) return;

    String simpleName = model.rootComponent().getSimpleName().toString();
    String implementation = model.rootPackage() + ".DaggerGeneratedCucumber" + simpleName;
    try {
      FileObject fo =
          processingEnv
              .getFiler()
              .createResource(StandardLocation.CLASS_OUTPUT, "", serviceFileName);
      try (Writer w = fo.openWriter()) {
        w.write(implementation);
        w.write(System.lineSeparator());
      }
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Failed to write service file: " + e.getMessage(),
              model.rootComponent());
    }
  }

  // ---------------------------------------------------------------------------
  // File I/O
  // ---------------------------------------------------------------------------

  /**
   * Writes a generated Java source file to the filer. Skips the filer write silently if a file with
   * the same qualified name has already been generated in a prior processing round. Passes {@code
   * originatingElement} to {@link javax.annotation.processing.Filer#createSourceFile} to improve
   * incremental compilation behaviour in IDEs and build tools.
   */
  private void writeJavaFile(
      String packageName, TypeSpec typeSpec, TypeElement originatingElement) {
    String qualifiedName =
        packageName.isEmpty() ? typeSpec.name() : packageName + "." + typeSpec.name();
    if (!generatedFiles.add(qualifiedName)) return;

    JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();
    try {
      JavaFileObject jfo =
          processingEnv.getFiler().createSourceFile(qualifiedName, originatingElement);
      try (Writer w = jfo.openWriter()) {
        javaFile.writeTo(w);
      }
    } catch (IOException e) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Failed to write " + qualifiedName + ": " + e.getMessage(),
              originatingElement);
    }
  }
}
