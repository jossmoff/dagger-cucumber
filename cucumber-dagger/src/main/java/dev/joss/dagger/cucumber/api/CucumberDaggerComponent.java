package dev.joss.dagger.cucumber.api;

/**
 * Marker interface that user-defined root Dagger components must extend to integrate with Cucumber.
 *
 * <p>A typical declaration looks like:
 *
 * <pre>{@code
 * @CucumberDaggerConfiguration
 * @Singleton
 * @Component(modules = {CucumberDaggerModule.class})
 * public interface AppComponent extends CucumberDaggerComponent {}
 * }</pre>
 *
 * <p>The {@link #scopedComponentBuilder()} method is satisfied at runtime by Dagger using the
 * binding generated in {@code CucumberDaggerModule}. It returns a builder for the per-scenario
 * subcomponent so that the runtime can create a fresh {@link CucumberScopedComponent} at the start
 * of each scenario.
 */
public interface CucumberDaggerComponent {

  /**
   * Returns a builder for the generated per-scenario {@link CucumberScopedComponent}.
   *
   * <p>The concrete binding is provided by the generated {@code CucumberDaggerModule}. Callers
   * should invoke {@link CucumberScopedComponent.Builder#build()} on the returned builder to obtain
   * a fresh scoped component instance.
   */
  @SuppressWarnings("rawtypes")
  CucumberScopedComponent.Builder scopedComponentBuilder();
}
