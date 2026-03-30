package dev.joss.dagger.cucumber.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Dagger {@link dagger.Component} interface as the root component for a Cucumber test
 * suite.
 *
 * <p>Exactly one class in the test classpath must be annotated with this annotation. The annotated
 * interface must:
 *
 * <ul>
 *   <li>Extend {@link CucumberDaggerComponent}
 *   <li>Be annotated with {@link dagger.Component} whose {@code modules} list includes the
 *       generated {@code CucumberDaggerModule}
 * </ul>
 *
 * <p>The annotation processor uses this annotation to locate the root component and generate the
 * supporting Dagger subcomponent, module, and service-file entries needed by the runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CucumberDaggerConfiguration {}
