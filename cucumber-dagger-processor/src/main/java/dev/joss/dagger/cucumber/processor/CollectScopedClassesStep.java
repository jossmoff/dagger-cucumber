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
 * Pipeline step 2 — collects all {@code @ScenarioScoped} classes in the glue package and validates
 * each one.
 *
 * <p>Validation rules (all errors are accumulated before halting):
 *
 * <ul>
 *   <li>Interfaces, abstract classes, and enums may not be annotated with {@code @ScenarioScoped};
 *       this is checked for all annotated type elements regardless of package.
 *   <li>Every concrete {@code @ScenarioScoped} class must declare an {@code @Inject} constructor so
 *       Dagger can create it inside the generated scoped subcomponent.
 * </ul>
 *
 * <p>Methods annotated with {@code @ScenarioScoped} (Style B) are handled by {@link
 * BuildProcessingModelStep} and are not processed here. Concrete classes outside the glue package
 * are silently ignored.
 */
final class CollectScopedClassesStep
    implements ProcessingStep<ProcessingContext, FoundRootComponent, CollectedScopedClasses> {

  @Override
  public StepResult<CollectedScopedClasses> execute(
      ProcessingContext ctx, FoundRootComponent input) {

    if (ctx.knownTypes.scenarioScoped == null) {
      return StepResult.succeeded(
          new CollectedScopedClasses(input.rootComponent(), input.rootPackage(), List.of()));
    }

    boolean hasErrors = false;

    List<Element> annotatedElements =
        new ArrayList<>(ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.scenarioScoped));

    // Validate ALL non-method type elements regardless of package — invalid type targets such as
    // interfaces, abstract classes, and enums should be reported wherever they appear, not silently
    // ignored just because they are outside the glue package.
    List<Element> typeElements =
        annotatedElements.stream()
            .filter(e -> e.getKind() != ElementKind.METHOD)
            .collect(Collectors.toList());

    for (Element element : typeElements) {
      if (isInvalidScopedTarget(element)) {
        ctx.messager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                "@ScenarioScoped can only be applied to concrete classes or @Provides methods",
                element);
        hasErrors = true;
      }
    }

    // Collect concrete classes from the glue package only.
    List<TypeElement> scopedClasses =
        typeElements.stream()
            .filter(e -> isClass(e) && !isAbstractClass(e))
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
                  "@ScenarioScoped class "
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
    return isInterface(element) || isAbstractClass(element) || isEnum(element);
  }

  private static boolean isEnum(Element element) {
    return element.getKind() == ElementKind.ENUM;
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
