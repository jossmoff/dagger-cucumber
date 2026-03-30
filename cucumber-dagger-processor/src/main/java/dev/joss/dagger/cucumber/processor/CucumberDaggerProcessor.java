package dev.joss.dagger.cucumber.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

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
 *   <li><strong>{@code CucumberDaggerModule}</strong> — the module that declares the subcomponent
 *       and provides the raw-type {@code CucumberScopedComponent.Builder} binding needed by the
 *       runtime.
 *   <li><strong>{@code GeneratedCucumber{UserSimpleName}}</strong> — a wrapper {@code @Component}
 *       that combines the user's modules with {@code CucumberDaggerModule}. Users do <em>not</em>
 *       need to list {@code CucumberDaggerModule} themselves; the processor adds it automatically.
 *   <li><strong>{@code dev.joss.dagger.cucumber.generated.CucumberScopedComponentAccessor}</strong>
 *       — a simple accessor class that returns the {@code GeneratedScopedComponent} class literal,
 *       allowing the runtime to avoid a classpath scan at startup.
 *   <li><strong>{@code META-INF/services/…CucumberDaggerComponent}</strong> — service file entry
 *       pointing to the Dagger-generated factory for the wrapper component ({@code
 *       DaggerGeneratedCucumber{UserSimpleName}}).
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration")
public final class CucumberDaggerProcessor extends AbstractProcessor {

  private KnownTypes knownTypes;
  private AnnotationUtils annotationUtils;
  private CucumberDaggerGenerator generator;

  @Override
  public synchronized void init(javax.annotation.processing.ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    knownTypes = new KnownTypes(processingEnv);
    annotationUtils = new AnnotationUtils(processingEnv);
    generator = new CucumberDaggerGenerator(processingEnv);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return processingEnv.getSourceVersion();
  }

  @Override
  public boolean process(Set<? extends TypeElement> _annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) return false;
    if (knownTypes.cucumberDaggerConfiguration == null) return false;

    Set<? extends Element> configElements =
        roundEnv.getElementsAnnotatedWith(knownTypes.cucumberDaggerConfiguration);
    if (configElements.isEmpty()) return false;

    if (configElements.size() > 1) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Only one @CucumberDaggerConfiguration is allowed");
      return false;
    }

    TypeElement rootComponent = (TypeElement) configElements.iterator().next();

    if (rootComponent.getKind() != ElementKind.INTERFACE) {
      processingEnv
          .getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@CucumberDaggerConfiguration can only be applied to interfaces",
              rootComponent);
      return false;
    }

    String rootPackage =
        processingEnv.getElementUtils().getPackageOf(rootComponent).getQualifiedName().toString();

    ProcessingModel model = buildModel(rootComponent, rootPackage, roundEnv);
    if (model == null) return false;

    generator.generate(model);
    return false;
  }

  // ---------------------------------------------------------------------------
  // Discovery
  // ---------------------------------------------------------------------------

  /**
   * Discovers all elements needed for generation and validates them. Returns a fully-populated
   * {@link ProcessingModel}, or {@code null} if any validation error was emitted (to avoid
   * generating broken files downstream).
   */
  private ProcessingModel buildModel(
      TypeElement rootComponent, String rootPackage, RoundEnvironment roundEnv) {

    boolean hasErrors = false;

    // Step A — find @CucumberScoped classes in the glue package
    List<TypeElement> scopedClasses = new ArrayList<>();
    if (knownTypes.cucumberScoped != null) {
      for (Element e : roundEnv.getElementsAnnotatedWith(knownTypes.cucumberScoped)) {
        if (e.getKind() == ElementKind.INTERFACE
            || (e.getKind() == ElementKind.CLASS && e.getModifiers().contains(Modifier.ABSTRACT))) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "@CucumberScoped can only be applied to concrete classes",
                  e);
          hasErrors = true;
          continue;
        }
        if (e.getKind() != ElementKind.CLASS) continue;
        TypeElement te = (TypeElement) e;
        String pkg = processingEnv.getElementUtils().getPackageOf(te).getQualifiedName().toString();
        if (pkg.equals(rootPackage) || pkg.startsWith(rootPackage + ".")) {
          scopedClasses.add(te);
        }
      }
    }

    // Step B — find user modules on @Component with @Provides @CucumberScoped methods
    List<TypeElement> userScopedModules = new ArrayList<>();
    Map<TypeName, String> scopedProvisionMethods = new LinkedHashMap<>();

    // Populate Style A first
    for (TypeElement scopedClass : scopedClasses) {
      scopedProvisionMethods.put(
          TypeName.get(scopedClass.asType()), NamingStrategy.provisionMethodName(scopedClass));
    }

    List<TypeMirror> userModules =
        annotationUtils.getClassArrayValue(rootComponent, knownTypes.daggerComponent, "modules");

    for (TypeMirror moduleMirror : userModules) {
      TypeElement moduleElement =
          (TypeElement) processingEnv.getTypeUtils().asElement(moduleMirror);
      if (moduleElement == null) continue;
      boolean isUserScopedModule = false;
      for (Element enclosed : moduleElement.getEnclosedElements()) {
        if (enclosed.getKind() != ElementKind.METHOD) continue;
        ExecutableElement method = (ExecutableElement) enclosed;
        boolean hasProvides = annotationUtils.hasAnnotation(method, knownTypes.daggerProvides);
        boolean hasCucumberScoped =
            annotationUtils.hasAnnotation(method, knownTypes.cucumberScoped);
        if (!hasProvides || !hasCucumberScoped) continue;

        // Reject qualified @CucumberScoped provider methods
        if (!annotationUtils
            .findMetaAnnotated(method.getAnnotationMirrors(), knownTypes.javaxQualifier)
            .isEmpty()) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "Qualified @CucumberScoped provider methods are not currently supported: "
                      + method.getSimpleName(),
                  method);
          hasErrors = true;
          continue;
        }

        isUserScopedModule = true;
        TypeName returnTypeName = TypeName.get(method.getReturnType());
        TypeElement returnTypeElement =
            (TypeElement) processingEnv.getTypeUtils().asElement(method.getReturnType());
        String methodName =
            returnTypeElement != null
                ? NamingStrategy.provisionMethodName(returnTypeElement)
                : NamingStrategy.decapitalize(
                    method.getReturnType().toString().replaceAll(".*\\.", ""));
        scopedProvisionMethods.putIfAbsent(returnTypeName, methodName);
      }
      if (isUserScopedModule) {
        userScopedModules.add(moduleElement);
      }
    }

    // Step C — find step-def classes: @Inject constructors in the glue package, excluding scoped
    Map<TypeName, String> stepDefMethods = new LinkedHashMap<>();
    if (knownTypes.javaxInject != null) {
      for (Element e : roundEnv.getElementsAnnotatedWith(knownTypes.javaxInject)) {
        if (e.getKind() != ElementKind.CONSTRUCTOR) continue;
        TypeElement enclosing = (TypeElement) e.getEnclosingElement();
        if (scopedClasses.contains(enclosing)) continue;
        String pkg =
            processingEnv.getElementUtils().getPackageOf(enclosing).getQualifiedName().toString();
        if (!pkg.equals(rootPackage) && !pkg.startsWith(rootPackage + ".")) continue;
        stepDefMethods.put(
            TypeName.get(enclosing.asType()), NamingStrategy.provisionMethodName(enclosing));
      }
    }

    // Validation — every @CucumberScoped class must have an @Inject constructor
    if (knownTypes.javaxInject != null) {
      for (TypeElement scopedClass : scopedClasses) {
        boolean hasInjectConstructor = false;
        for (Element enclosed : scopedClass.getEnclosedElements()) {
          if (enclosed.getKind() != ElementKind.CONSTRUCTOR) continue;
          if (annotationUtils.hasAnnotation(enclosed, knownTypes.javaxInject)) {
            hasInjectConstructor = true;
            break;
          }
        }
        if (!hasInjectConstructor) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "@CucumberScoped class "
                      + scopedClass.getSimpleName()
                      + " must declare an @Inject constructor so Dagger can create it"
                      + " in the generated scoped subcomponent",
                  scopedClass);
          hasErrors = true;
        }
      }
    }

    if (hasErrors) return null;

    List<AnnotationMirror> scopeAnnotations =
        annotationUtils.findMetaAnnotated(
            rootComponent.getAnnotationMirrors(), knownTypes.javaxScope);

    return new ProcessingModel(
        rootComponent,
        rootPackage,
        scopedClasses,
        userScopedModules,
        scopedProvisionMethods,
        stepDefMethods,
        userModules,
        scopeAnnotations);
  }
}
