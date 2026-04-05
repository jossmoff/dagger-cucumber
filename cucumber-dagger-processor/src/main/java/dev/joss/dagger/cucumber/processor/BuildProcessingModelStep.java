package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Pipeline step 4 — assembles the final {@link ProcessingModel} from the data collected by earlier
 * steps.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Builds Style-A provision methods (one per {@code @ScenarioScoped} class).
 *   <li>Scans each module listed in the root {@code @Component} for
 *       {@code @Provides @ScenarioScoped} methods (Style B) and adds them to the provision-method
 *       map.
 *   <li>Rejects qualified ({@code @Named} etc.) Style-B provider methods with a compile error.
 *   <li>Copies scope annotations (e.g. {@code @Singleton}) from the root component to the model.
 * </ul>
 */
final class BuildProcessingModelStep
    implements ProcessingStep<ProcessingContext, CollectedStepDefs, ProcessingModel> {

  @Override
  public StepResult<ProcessingModel> execute(ProcessingContext ctx, CollectedStepDefs input) {

    boolean hasErrors = false;

    // Style-A: one provision method per @ScenarioScoped class
    Map<TypeName, String> scopedProvisionMethods = new LinkedHashMap<>();
    for (TypeElement scopedClass : input.scopedClasses()) {
      scopedProvisionMethods.put(
          TypeName.get(scopedClass.asType()), NamingStrategy.provisionMethodName(scopedClass));
    }

    // Style-B: scan @Component modules for @Provides @ScenarioScoped methods
    List<TypeElement> userScopedModules = new ArrayList<>();
    List<TypeMirror> userModules =
        ctx.annotationUtils.getClassArrayValue(
            input.rootComponent(), ctx.knownTypes.daggerComponent, "modules");

    for (TypeMirror moduleMirror : userModules) {
      TypeElement moduleElement =
          (TypeElement) ctx.processingEnv.getTypeUtils().asElement(moduleMirror);
      if (moduleElement == null) continue;
      boolean isUserScopedModule = false;

      for (Element enclosed : moduleElement.getEnclosedElements()) {
        if (enclosed.getKind() != ElementKind.METHOD) continue;
        ExecutableElement method = (ExecutableElement) enclosed;
        if (!ctx.annotationUtils.hasAnnotation(method, ctx.knownTypes.daggerProvides)) continue;
        if (!ctx.annotationUtils.hasAnnotation(method, ctx.knownTypes.scenarioScoped)) continue;

        if (!ctx.annotationUtils
            .findMetaAnnotated(method.getAnnotationMirrors(), ctx.knownTypes.jakartaQualifier)
            .isEmpty()) {
          ctx.messager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "Qualified @ScenarioScoped provider methods are not currently supported: "
                      + method.getSimpleName(),
                  method);
          hasErrors = true;
          continue;
        }

        isUserScopedModule = true;
        TypeName returnTypeName = TypeName.get(method.getReturnType());
        TypeElement returnTypeElement =
            (TypeElement) ctx.processingEnv.getTypeUtils().asElement(method.getReturnType());
        String methodName =
            returnTypeElement != null
                ? NamingStrategy.provisionMethodName(returnTypeElement)
                : method.getSimpleName().toString();
        scopedProvisionMethods.putIfAbsent(returnTypeName, methodName);
      }
      if (isUserScopedModule) userScopedModules.add(moduleElement);
    }

    if (hasErrors) return StepResult.failed();

    List<AnnotationMirror> scopeAnnotations =
        ctx.annotationUtils.findMetaAnnotated(
            input.rootComponent().getAnnotationMirrors(), ctx.knownTypes.jakartaScope);

    return StepResult.succeeded(
        new ProcessingModel(
            input.rootComponent(),
            input.rootPackage(),
            input.scopedClasses(),
            userScopedModules,
            scopedProvisionMethods,
            input.stepDefMethods(),
            userModules,
            scopeAnnotations));
  }
}
