package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import java.util.Map;
import javax.lang.model.element.TypeElement;

/**
 * Output of {@link CollectStepDefsStep}: step-definition provision methods (return type → method
 * name) for classes that have an {@code @Inject} constructor in the glue package.
 */
record CollectedStepDefs(
    TypeElement rootComponent, String rootPackage, Map<TypeName, String> stepDefMethods) {}
