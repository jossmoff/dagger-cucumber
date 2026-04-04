package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

/**
 * Pipeline step 3 — collects step-definition classes.
 *
 * <p>A step-definition class is any class in the glue package that has an {@code @Inject}
 * constructor but is <em>not</em> already annotated with {@code @CucumberScoped} (those are handled
 * by step 2).
 *
 * <p>This step performs no validation: having zero step-definition classes is valid.
 */
final class CollectStepDefsStep
    implements ProcessingStep<ProcessingContext, CollectedScopedClasses, CollectedStepDefs> {

  @Override
  public StepResult<CollectedStepDefs> execute(
      ProcessingContext ctx, CollectedScopedClasses input) {

    Map<TypeName, String> stepDefMethods =
        ctx.knownTypes.jakartaInject == null
            ? new LinkedHashMap<>()
            : ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.jakartaInject).stream()
                .filter(CollectStepDefsStep::isConstructor)
                .map(CollectStepDefsStep::enclosingType)
                .filter(enclosing -> isNotScoped(enclosing, input))
                .filter(enclosing -> isInGluePackage(enclosing, ctx, input))
                .collect(
                    Collectors.toMap(
                        CollectStepDefsStep::toTypeName,
                        NamingStrategy::provisionMethodName,
                        (left, _right) -> left,
                        LinkedHashMap::new));

    CollectedStepDefs result =
        new CollectedStepDefs(
            input.rootComponent(), input.rootPackage(), input.scopedClasses(), stepDefMethods);
    return StepResult.succeeded(result);
  }

  private static boolean isConstructor(Element element) {
    return element.getKind() == ElementKind.CONSTRUCTOR;
  }

  private static TypeElement enclosingType(Element element) {
    return (TypeElement) element.getEnclosingElement();
  }

  private static boolean isNotScoped(TypeElement enclosing, CollectedScopedClasses input) {
    return !input.scopedClasses().contains(enclosing);
  }

  private static boolean isInGluePackage(
      TypeElement enclosing, ProcessingContext ctx, CollectedScopedClasses input) {
    return ctx.inGluePackage(enclosing, input.rootPackage());
  }

  private static TypeName toTypeName(TypeElement enclosing) {
    return TypeName.get(enclosing.asType());
  }
}
