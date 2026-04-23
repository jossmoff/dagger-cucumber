package dev.joss.dagger.cucumber.processor;

import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

/**
 * Pipeline step 2 - detects an inner {@code @Component.Builder} interface on the root component.
 *
 * <p>When the user's {@code @CucumberDaggerConfiguration} interface declares an inner interface
 * annotated with {@code @dagger.Component.Builder}, this step captures that inner type and
 * propagates it to subsequent steps via {@link FoundRootComponent#componentBuilder()}.
 *
 * <p>Downstream effects when a builder is detected:
 *
 * <ul>
 *   <li>The generated {@code GeneratedCucumber{Name}} wrapper component gains a matching
 *       {@code @Component.Builder} inner interface that extends the user's builder (covariant
 *       {@code build()} return type). Dagger then generates a {@code builder()} factory on the
 *       {@code DaggerGeneratedCucumber{Name}} class.
 *   <li>At runtime, {@link dev.joss.dagger.cucumber.internal.DaggerBackend} detects that {@code
 *       create()} is absent and falls back to {@code builder().build()}.
 * </ul>
 *
 * <p><strong>Assumption - no-arg builder contract:</strong> the builder must be usable without
 * setting any {@code @BindsInstance} values explicitly. Concretely, {@code builder().build()} must
 * succeed at runtime without any intermediate setter calls. If your builder has
 * {@code @BindsInstance} parameters, annotate them with {@code @Nullable} so that Dagger allows
 * {@code build()} to be called without invoking those setters.
 *
 * <p>This step never fails: the absence of a {@code @Component.Builder} is valid (the generated
 * resolver will fall back to {@code create()}).
 */
final class DetectComponentBuilderStep
    implements ProcessingStep<ProcessingContext, FoundRootComponent, FoundRootComponent> {

  @Override
  public StepResult<FoundRootComponent> execute(ProcessingContext ctx, FoundRootComponent input) {
    TypeElement builder = findComponentBuilder(ctx, input.rootComponent());
    if (builder == null) {
      return StepResult.succeeded(input);
    }
    return StepResult.succeeded(
        new FoundRootComponent(input.rootComponent(), input.rootPackage(), builder));
  }

  /**
   * Scans the enclosed elements of {@code rootComponent} for an inner interface annotated with
   * {@code @dagger.Component.Builder}. Returns the first match, or {@code null} if none is found.
   */
  private static TypeElement findComponentBuilder(
      ProcessingContext ctx, TypeElement rootComponent) {
    if (ctx.knownTypes.daggerComponentBuilder == null) return null;
    for (Element enclosed : rootComponent.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.INTERFACE) continue;
      TypeElement inner = (TypeElement) enclosed;
      if (ctx.annotationUtils.hasAnnotation(inner, ctx.knownTypes.daggerComponentBuilder)) {
        return inner;
      }
    }
    return null;
  }
}
