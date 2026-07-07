package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;
import dev.joss.dagger.cucumber.processor.pipeline.ProcessingStep;
import dev.joss.dagger.cucumber.processor.pipeline.StepResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Pipeline step 4 - assembles the final {@link ProcessingModel} from the data collected by earlier
 * steps.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Scans each module listed in the root {@code @Component} for
 *       {@code @Provides @ScenarioScope} methods and builds scoped provision methods.
 *   <li>For {@code @Named}-qualified {@code @ScenarioScope} methods: generates a named provision
 *       method (e.g., {@code primaryBasket()} for {@code @Named("primary") Basket}) that is emitted
 *       on {@code GeneratedScopedComponent} with the {@code @Named} annotation. The module is still
 *       included in {@code GeneratedScopedModule} so Dagger resolves the qualified binding for
 *       step-definition dependencies.
 *   <li>For other (non-{@code @Named}) qualified {@code @ScenarioScope} methods: includes the
 *       module in {@code GeneratedScopedModule} so that transitive dependencies are resolved by
 *       Dagger, but does not generate a dedicated provision method.
 *   <li>Collects zero-argument provision methods declared on the root component interface for use
 *       in the generated {@code resolveRoot} dispatch.
 *   <li>Copies scope annotations (e.g. {@code @Singleton}) from the root component to the model.
 * </ul>
 */
final class BuildProcessingModelStep
    implements ProcessingStep<ProcessingContext, CollectedStepDefs, ProcessingModel> {

  @Override
  public StepResult<ProcessingModel> execute(ProcessingContext ctx, CollectedStepDefs input) {

    // Scan @Component modules for @Provides @ScenarioScope methods
    Map<TypeName, String> scopedProvisionMethods = new LinkedHashMap<>();
    List<NamedScopedProvision> namedScopedProvisions = new ArrayList<>();
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

        TypeName returnTypeName = TypeName.get(method.getReturnType());
        TypeElement returnTypeElement =
            (TypeElement) ctx.processingEnv.getTypeUtils().asElement(method.getReturnType());

        List<AnnotationMirror> qualifiers =
            ctx.annotationUtils.findMetaAnnotated(
                method.getAnnotationMirrors(), ctx.knownTypes.jakartaQualifier);

        if (!qualifiers.isEmpty()) {
          // Always mark the module as a user-scoped module so it is included in
          // GeneratedScopedModule; this ensures Dagger can resolve qualified bindings as
          // transitive dependencies of step-definition classes.
          isUserScopedModule = true;

          // For @Named qualifiers, generate a named provision method so that Dagger exposes the
          // binding through a dedicated accessor on GeneratedScopedComponent.
          AnnotationMirror qualifier = qualifiers.get(0);
          if (ctx.knownTypes.jakartaNamed != null
              && ctx.processingEnv
                  .getTypeUtils()
                  .isSameType(qualifier.getAnnotationType(), ctx.knownTypes.jakartaNamed.asType())
              && returnTypeElement != null) {
            String namedValue = getNamedValue(qualifier);
            if (namedValue != null) {
              String methodName =
                  NamingStrategy.decapitalize(namedValue)
                      + returnTypeElement.getSimpleName().toString();
              namedScopedProvisions.add(
                  new NamedScopedProvision(returnTypeName, methodName, namedValue));
            }
          }
          // Non-@Named qualifiers: module is included above; no provision method is generated.
          continue;
        }

        isUserScopedModule = true;
        String methodName =
            returnTypeElement != null
                ? NamingStrategy.provisionMethodName(returnTypeElement)
                : method.getSimpleName().toString();
        scopedProvisionMethods.putIfAbsent(returnTypeName, methodName);
      }
      if (isUserScopedModule) userScopedModules.add(moduleElement);
    }

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
            rootProvisionMethods,
            input.componentBuilder(),
            namedScopedProvisions));
  }

  /**
   * Extracts the string {@code value()} from a {@code @Named} annotation mirror. Returns {@code
   * null} if the value attribute is absent or not a string.
   */
  private static String getNamedValue(AnnotationMirror namedAnnotation) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        namedAnnotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals("value")) {
        Object val = entry.getValue().getValue();
        return val instanceof String s ? s : null;
      }
    }
    return null;
  }
}
