package dev.joss.dagger.cucumber.processor;

import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;

/**
 * Output of {@link CollectStepDefsStep}: step-definition provision methods (return type → method
 * name) for classes that have an {@code @Inject} constructor but are not {@code @CucumberScoped}.
 */
record CollectedStepDefs(
    TypeElement rootComponent,
    String rootPackage,
    List<TypeElement> scopedClasses,
    Map<TypeName, String> stepDefMethods) {}
