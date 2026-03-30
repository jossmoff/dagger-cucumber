package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Immutable snapshot of everything the processor discovered about the annotated root component in a
 * single processing round. Passed from discovery in {@link CucumberDaggerProcessor} to generation
 * in {@link CucumberDaggerGenerator}.
 */
final class ProcessingModel {

  /** The {@code @CucumberDaggerConfiguration}-annotated root component interface. */
  final TypeElement rootComponent;

  /** Package of {@link #rootComponent}; used as the generated-file package. */
  final String rootPackage;

  /** Style-A: classes directly annotated with {@code @CucumberScoped}. */
  final List<TypeElement> scopedClasses;

  /** Style-B: user modules that contain {@code @Provides @CucumberScoped} methods. */
  final List<TypeElement> userScopedModules;

  /**
   * Combined Style-A and Style-B scoped provision methods: return {@link TypeName} → method name.
   * Backed by a {@link java.util.LinkedHashMap} to preserve insertion order for deterministic
   * output.
   */
  final Map<TypeName, String> scopedProvisionMethods;

  /**
   * Step-definition provision methods: return {@link TypeName} → method name. Backed by a {@link
   * java.util.LinkedHashMap}.
   */
  final Map<TypeName, String> stepDefMethods;

  /** Module types declared in the user's {@code @Component(modules = {...})} attribute. */
  final List<TypeMirror> userModules;

  /**
   * Scope annotations (e.g. {@code @Singleton}) present on {@link #rootComponent}, to be copied
   * onto the generated wrapper component.
   */
  final List<AnnotationMirror> scopeAnnotations;

  ProcessingModel(
      TypeElement rootComponent,
      String rootPackage,
      List<TypeElement> scopedClasses,
      List<TypeElement> userScopedModules,
      Map<TypeName, String> scopedProvisionMethods,
      Map<TypeName, String> stepDefMethods,
      List<TypeMirror> userModules,
      List<AnnotationMirror> scopeAnnotations) {
    this.rootComponent = rootComponent;
    this.rootPackage = rootPackage;
    this.scopedClasses = scopedClasses;
    this.userScopedModules = userScopedModules;
    this.scopedProvisionMethods = scopedProvisionMethods;
    this.stepDefMethods = stepDefMethods;
    this.userModules = userModules;
    this.scopeAnnotations = scopeAnnotations;
  }
}
