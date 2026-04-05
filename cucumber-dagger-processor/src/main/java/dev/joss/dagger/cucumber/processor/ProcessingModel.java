package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Immutable snapshot of everything the processor discovered about the annotated root component in a
 * single processing round. Passed from discovery in {@link CucumberDaggerProcessor} to generation
 * in {@link CucumberDaggerGenerator}.
 *
 * @param rootComponent The {@code @CucumberDaggerConfiguration}-annotated root component interface.
 * @param rootPackage Package of {@link #rootComponent()}; used as the generated-file package.
 * @param scopedClasses Style-A: classes directly annotated with {@code @ScenarioScoped}.
 * @param userScopedModules Style-B: user modules that contain {@code @Provides @ScenarioScoped}
 *     methods.
 * @param scopedProvisionMethods Combined Style-A and Style-B scoped provision methods: return
 *     {@link TypeName} → method name. Backed by a {@link LinkedHashMap} to preserve insertion order
 *     for deterministic output.
 * @param stepDefMethods Step-definition provision methods: return {@link TypeName} → method name.
 *     Backed by a {@link LinkedHashMap}.
 * @param userModules Module types declared in the user's {@code @Component(modules = {...})}
 *     attribute.
 * @param scopeAnnotations Scope annotations (e.g. {@code @Singleton}) present on {@link
 *     #rootComponent()}, to be copied onto the generated wrapper component.
 */
record ProcessingModel(
    TypeElement rootComponent,
    String rootPackage,
    List<TypeElement> scopedClasses,
    List<TypeElement> userScopedModules,
    Map<TypeName, String> scopedProvisionMethods,
    Map<TypeName, String> stepDefMethods,
    List<TypeMirror> userModules,
    List<AnnotationMirror> scopeAnnotations) {}
