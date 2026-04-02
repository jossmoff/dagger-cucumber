package dev.joss.dagger.cucumber.processor;

import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/**
 * Pipeline step 1 — locates and validates the {@code @CucumberDaggerConfiguration} root component.
 *
 * <p>Validation rules:
 *
 * <ul>
 *   <li>Exactly one element must be annotated; more than one is a compile error.
 *   <li>The annotated element must be an interface; a class is a compile error.
 * </ul>
 *
 * <p>On success, returns the validated root component {@link TypeElement} and its package name.
 */
final class FindRootComponentStep
    implements ProcessingStep<ProcessingContext, Set<? extends Element>, FoundRootComponent> {

  @Override
  public StepResult<FoundRootComponent> execute(
      ProcessingContext ctx, Set<? extends Element> annotated) {

    if (annotated.size() > 1) {
      ctx.messager()
          .printMessage(Diagnostic.Kind.ERROR, "Only one @CucumberDaggerConfiguration is allowed");
      return StepResult.halt();
    }

    TypeElement root = (TypeElement) annotated.iterator().next();

    if (root.getKind() != ElementKind.INTERFACE) {
      ctx.messager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "@CucumberDaggerConfiguration can only be applied to interfaces",
              root);
      return StepResult.halt();
    }

    String pkg =
        ctx.processingEnv.getElementUtils().getPackageOf(root).getQualifiedName().toString();
    return StepResult.success(new FoundRootComponent(root, pkg));
  }
}
