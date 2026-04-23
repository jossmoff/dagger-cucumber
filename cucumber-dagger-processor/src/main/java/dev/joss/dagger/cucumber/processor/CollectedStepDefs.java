package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;
import java.util.Map;
import javax.lang.model.element.TypeElement;

/**
 * Output of {@link CollectStepDefsStep}: step-definition provision methods (return type → method
 * name) for classes that have an {@code @Inject} constructor in the glue package.
 *
 * @param rootComponent the root component {@link TypeElement}
 * @param rootPackage package of {@code rootComponent}
 * @param stepDefMethods provision methods keyed by return type
 * @param componentBuilder the inner {@code @Component.Builder} interface, or {@code null} if none
 */
record CollectedStepDefs(
    TypeElement rootComponent,
    String rootPackage,
    Map<TypeName, String> stepDefMethods,
    TypeElement componentBuilder) {}
