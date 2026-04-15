package dev.joss.dagger.cucumber.api;

import jakarta.inject.Scope;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dagger scope annotation for objects whose lifetime matches a single Cucumber scenario.
 *
 * <p>A new instance is created at the start of each scenario and discarded when the scenario ends.
 *
 * <p>Apply {@code @ScenarioScope} to a {@code @Provides} method inside a Dagger {@code @Module}
 * that is listed in the root {@code @Component}. The annotation processor includes that module in
 * the generated scoped subcomponent and exposes the return type as a provision method.
 *
 * <pre>{@code
 * @Module
 * public class ScenarioModule {
 *
 *   @Provides
 *   @ScenarioScope
 *   static Basket provideBasket(PriceList priceList, Discount discount) {
 *     return new Basket(priceList, discount);
 *   }
 * }
 * }</pre>
 *
 * <p><strong>Note on {@code @Target}:</strong> this annotation permits {@code ElementType.TYPE}
 * because Dagger requires scope annotations to be applicable to types in order to annotate the
 * generated {@code GeneratedScopedComponent} subcomponent. User code should only apply
 * {@code @ScenarioScope} to {@code @Provides} methods; placing it on a class has no effect and the
 * annotation processor does not process class-level usage.
 */
@Scope
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ScenarioScope {}
