package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;
import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

/**
 * Pipeline step 3 - collects step-definition classes.
 *
 * <p>A step-definition class is any class in the glue package that has an {@code @Inject}
 * constructor. This step performs no validation: having zero step-definition classes is valid.
 */
final class CollectStepDefsStep
    implements ProcessingStep<ProcessingContext, FoundRootComponent, CollectedStepDefs> {

  @Override
  public StepResult<CollectedStepDefs> execute(ProcessingContext ctx, FoundRootComponent input) {

    if (ctx.knownTypes.jakartaInject == null) {
      return StepResult.succeeded(
          new CollectedStepDefs(
              input.rootComponent(),
              input.rootPackage(),
              new LinkedHashMap<>(),
              input.componentBuilder()));
    }

    List<TypeElement> stepDefTypes =
        ctx.roundEnv.getElementsAnnotatedWith(ctx.knownTypes.jakartaInject).stream()
            .filter(CollectStepDefsStep::isConstructor)
            .map(CollectStepDefsStep::enclosingType)
            .filter(enclosing -> isInGluePackage(enclosing, ctx, input))
            .collect(Collectors.toList());

    // Build initial TypeName → simple-method-name and TypeName → TypeElement maps in tandem.
    Map<TypeName, String> rawMethods = new LinkedHashMap<>();
    Map<TypeName, TypeElement> typeElements = new LinkedHashMap<>();
    for (TypeElement type : stepDefTypes) {
      TypeName typeName = toTypeName(type);
      rawMethods.putIfAbsent(typeName, NamingStrategy.provisionMethodName(type));
      typeElements.putIfAbsent(typeName, type);
    }

    Map<TypeName, String> stepDefMethods =
        NamingStrategy.deduplicateMethodNames(rawMethods, typeElements);

    return StepResult.succeeded(
        new CollectedStepDefs(
            input.rootComponent(), input.rootPackage(), stepDefMethods, input.componentBuilder()));
  }

  private static boolean isConstructor(Element element) {
    return element.getKind() == ElementKind.CONSTRUCTOR;
  }

  private static TypeElement enclosingType(Element element) {
    return (TypeElement) element.getEnclosingElement();
  }

  private static boolean isInGluePackage(
      TypeElement enclosing, ProcessingContext ctx, FoundRootComponent input) {
    return ctx.inGluePackage(enclosing, input.rootPackage());
  }

  private static TypeName toTypeName(TypeElement enclosing) {
    return TypeName.get(enclosing.asType());
  }
}
