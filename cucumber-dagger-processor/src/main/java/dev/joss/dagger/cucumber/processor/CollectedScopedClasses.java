package dev.joss.dagger.cucumber.processor;

import java.util.List;
import javax.lang.model.element.TypeElement;

/**
 * Output of {@link CollectScopedClassesStep}: all validated {@code @ScenarioScoped} classes found
 * in the glue package, each confirmed to have an {@code @Inject} constructor.
 */
record CollectedScopedClasses(
    TypeElement rootComponent, String rootPackage, List<TypeElement> scopedClasses) {}
