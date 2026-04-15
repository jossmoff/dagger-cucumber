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
 * @param userScopedModules User modules that contain {@code @Provides @ScenarioScope} methods.
 * @param scopedProvisionMethods Style-B scoped provision methods: return {@link TypeName} → method
 *     name. Backed by a {@link LinkedHashMap} to preserve insertion order for deterministic output.
 * @param stepDefMethods Step-definition provision methods: return {@link TypeName} → method name.
 *     Backed by a {@link LinkedHashMap}.
 * @param userModules Module types declared in the user's {@code @Component(modules = {...})}
 *     attribute.
 * @param scopeAnnotations Scope annotations (e.g. {@code @Singleton}) present on {@link
 *     #rootComponent()}, to be copied onto the generated wrapper component.
 * @param rootProvisionMethods Provision methods declared on the root component interface: return
 *     {@link TypeName} → method name. Used to generate the {@code resolveRoot} dispatch in {@code
 *     GeneratedComponentResolver}. Backed by a {@link LinkedHashMap}.
 */
record ProcessingModel(
    TypeElement rootComponent,
    String rootPackage,
    List<TypeElement> userScopedModules,
    Map<TypeName, String> scopedProvisionMethods,
    Map<TypeName, String> stepDefMethods,
    List<TypeMirror> userModules,
    List<AnnotationMirror> scopeAnnotations,
    Map<TypeName, String> rootProvisionMethods) {}
