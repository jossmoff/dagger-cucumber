package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Pipeline step 3 - assembles the final {@link ProcessingModel} from the data collected by earlier
 * steps.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Scans each module listed in the root {@code @Component} for
 *       {@code @Provides @ScenarioScope} methods and builds scoped provision methods.
 *   <li>Rejects qualified ({@code @Named} etc.) {@code @ScenarioScope} provider methods with a
 *       compile error.
 *   <li>Collects zero-argument provision methods declared on the root component interface for use
 *       in the generated {@code resolveRoot} dispatch.
 *   <li>Copies scope annotations (e.g. {@code @Singleton}) from the root component to the model.
 * </ul>
 */
final class BuildProcessingModelStep
    implements ProcessingStep<ProcessingContext, CollectedStepDefs, ProcessingModel> {

  @Override
  public StepResult<ProcessingModel> execute(ProcessingContext ctx, CollectedStepDefs input) {

    boolean hasErrors = false;

    // Scan @Component modules for @Provides @ScenarioScope methods
    Map<TypeName, String> scopedProvisionMethods = new LinkedHashMap<>();
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
        if (!ctx.annotationUtils.hasAnnotation(method, ctx.knownTypes.scenarioScope)) continue;

        if (!ctx.annotationUtils
            .findMetaAnnotated(method.getAnnotationMirrors(), ctx.knownTypes.jakartaQualifier)
            .isEmpty()) {
          ctx.messager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "Qualified @ScenarioScope provider methods are not currently supported: "
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

    // Collect abstract zero-argument provision methods on the root component interface for
    // resolveRoot dispatch in GeneratedComponentResolver.
    Map<TypeName, String> rootProvisionMethods = new LinkedHashMap<>();
    for (Element member :
        ctx.processingEnv.getElementUtils().getAllMembers(input.rootComponent())) {
      if (member.getKind() != ElementKind.METHOD) continue;
      ExecutableElement method = (ExecutableElement) member;
      if (!method.getModifiers().contains(Modifier.ABSTRACT)) continue;
      if (!method.getParameters().isEmpty()) continue;
      if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) continue;
      TypeElement declaringType = (TypeElement) method.getEnclosingElement();
      if (declaringType.getQualifiedName().contentEquals("java.lang.Object")) continue;
      rootProvisionMethods.put(
          TypeName.get(method.getReturnType()), method.getSimpleName().toString());
    }

    List<AnnotationMirror> scopeAnnotations =
        ctx.annotationUtils.findMetaAnnotated(
            input.rootComponent().getAnnotationMirrors(), ctx.knownTypes.jakartaScope);

    // Exclude step-def types already covered by a scoped provision method to avoid duplicate
    // provision methods in GeneratedScopedComponent.
    Map<TypeName, String> stepDefMethods = new LinkedHashMap<>(input.stepDefMethods());
    scopedProvisionMethods.keySet().forEach(stepDefMethods::remove);

    return StepResult.succeeded(
        new ProcessingModel(
            input.rootComponent(),
            input.rootPackage(),
            userScopedModules,
            scopedProvisionMethods,
            stepDefMethods,
            userModules,
            scopeAnnotations,
            rootProvisionMethods));
  }
}
