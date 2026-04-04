package dev.joss.dagger.cucumber.processor;

import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Pipeline step 2 — collects all {@code @CucumberScoped} classes in the glue package and validates
 * each one.
 *
 * <p>Validation rules (all errors are accumulated before halting):
 *
 * <ul>
 *   <li>Interfaces and abstract classes may not be annotated with {@code @CucumberScoped}.
 *   <li>Every concrete {@code @CucumberScoped} class must declare an {@code @Inject} constructor so
 *       Dagger can create it inside the generated scoped subcomponent.
 * </ul>
 *
 * <p>Elements outside the glue package ({@link FoundRootComponent#rootPackage()} or any
 * sub-package) are silently ignored.
 */
final class CollectScopedClassesStep
    implements ProcessingStep<ProcessingContext, FoundRootComponent, CollectedScopedClasses> {

  @Override
  public StepResult<CollectedScopedClasses> execute(
      ProcessingContext ctx, FoundRootComponent input) {

    if (ctx.knownTypes.cucumberScoped == null) {
      return StepResult.succeeded(
          new CollectedScopedClasses(input.rootComponent(), input.rootPackage(), List.of()));
    }

    boolean hasErrors = false;

    List<Element> annotatedElements =
        new ArrayList<>(ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.cucumberScoped));

    for (Element element : annotatedElements) {
      if (isInvalidScopedTarget(element)) {
        ctx.messager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@CucumberScoped can only be applied to concrete classes",
                element);
        hasErrors = true;
      }
    }

    List<TypeElement> scopedClasses =
        annotatedElements.stream()
            .filter(CollectScopedClassesStep::isClass)
            .map(CollectScopedClassesStep::asTypeElement)
            .filter(type -> isInGluePackage(type, ctx, input))
            .collect(Collectors.toList());

    // Validate @Inject constructor presence for all collected classes in one pass so all errors are
    // reported before halting.
    if (ctx.knownTypes.jakartaInject != null) {
      for (TypeElement scopedClass : scopedClasses) {
        if (!hasInjectConstructor(ctx, scopedClass)) {
          ctx.messager()
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

    if (hasErrors) return StepResult.failed();
    return StepResult.succeeded(
        new CollectedScopedClasses(input.rootComponent(), input.rootPackage(), scopedClasses));
  }

  private static boolean isInvalidScopedTarget(Element element) {
    return isInterface(element) || isAbstractClass(element);
  }

  private static boolean isInterface(Element element) {
    return element.getKind() == ElementKind.INTERFACE;
  }

  private static boolean isAbstractClass(Element element) {
    return isClass(element) && element.getModifiers().contains(Modifier.ABSTRACT);
  }

  private static boolean isClass(Element element) {
    return element.getKind() == ElementKind.CLASS;
  }

  private static TypeElement asTypeElement(Element element) {
    return (TypeElement) element;
  }

  private static boolean isInGluePackage(
      TypeElement type, ProcessingContext ctx, FoundRootComponent input) {
    return ctx.inGluePackage(type, input.rootPackage());
  }

  private static boolean hasInjectConstructor(ProcessingContext ctx, TypeElement te) {
    return te.getEnclosedElements().stream()
        .anyMatch(enclosed -> isInjectAnnotatedConstructor(enclosed, ctx));
  }

  private static boolean isInjectAnnotatedConstructor(Element element, ProcessingContext ctx) {
    return element.getKind() == ElementKind.CONSTRUCTOR
        && ctx.annotationUtils.hasAnnotation(element, ctx.knownTypes.jakartaInject);
  }
}
