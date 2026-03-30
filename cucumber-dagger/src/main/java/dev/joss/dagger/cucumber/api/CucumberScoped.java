package dev.joss.dagger.cucumber.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Scope;

/**
 * Dagger scope annotation for objects whose lifetime matches a single Cucumber scenario.
 *
 * <p>A new instance is created at the start of each scenario and discarded when the scenario ends.
 * There are two supported usage styles:
 *
 * <ul>
 *   <li><strong>Style A — annotate the class:</strong> Apply {@code @CucumberScoped} directly to a
 *       class that also has an {@code @Inject} constructor. The annotation processor will
 *       automatically generate a provision method for it in the per-scenario subcomponent.
 *   <li><strong>Style B — annotate a {@code @Provides} method:</strong> Place
 *       {@code @Provides @CucumberScoped} on a method inside a Dagger module that is listed in the
 *       root {@code @Component}. The annotation processor will include that module in the generated
 *       scoped subcomponent and expose the return type as a provision method.
 * </ul>
 */
@Scope
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CucumberScoped {}
