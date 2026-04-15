package dev.joss.dagger.cucumber.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Dagger {@link dagger.Component} interface as the root component for a Cucumber test
 * suite.
 *
 * <p>Exactly one interface in the test classpath must carry this annotation. The annotated
 * interface must be annotated with {@link dagger.Component}; only application-specific modules need
 * to be listed in {@code modules} - the processor automatically includes the generated {@code
 * CucumberDaggerModule} in the wrapper component it generates.
 *
 * <p>A typical declaration looks like:
 *
 * <pre>{@code
 * @CucumberDaggerConfiguration
 * @Singleton
 * @Component(modules = {PriceListModule.class})
 * public interface IntegrationTestConfig {}
 * }</pre>
 *
 * <p>The annotation processor uses this annotation to locate the root component and generate the
 * supporting Dagger subcomponent, module, and service-file entries needed by the runtime. The
 * generated wrapper component extends {@link CucumberDaggerComponent}; the annotated interface does
 * not need to do so itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CucumberDaggerConfiguration {}
