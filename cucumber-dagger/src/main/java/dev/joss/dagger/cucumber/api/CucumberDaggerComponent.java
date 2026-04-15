package dev.joss.dagger.cucumber.api;

/**
 * Marker interface extended by the processor-generated wrapper component to integrate with the
 * cucumber-dagger runtime.
 *
 * <p>User-defined components annotated with {@link CucumberDaggerConfiguration} do <em>not</em>
 * need to extend this interface. The annotation processor generates a wrapper component (e.g.
 * {@code GeneratedCucumberIntegrationTestConfig}) that extends {@code CucumberDaggerComponent} and
 * includes the generated {@code CucumberDaggerModule} automatically.
 *
 * <p>The {@link #scopedComponentBuilder()} method is satisfied at runtime by Dagger using the
 * binding generated in {@code CucumberDaggerModule}. It returns a builder for the per-scenario
 * subcomponent so that the runtime can create a fresh {@link ScenarioScopedComponent} at the start
 * of each scenario.
 */
public interface CucumberDaggerComponent {

  /**
   * Returns a builder for the generated per-scenario {@link ScenarioScopedComponent}.
   *
   * <p>The concrete binding is provided by the generated {@code CucumberDaggerModule}. Callers
   * should invoke {@link ScenarioScopedComponent.Builder#build()} on the returned builder to obtain
   * a fresh scoped component instance.
   *
   * @return a builder for the generated per-scenario subcomponent
   * @see ScenarioScopedComponent.Builder
   */
  @SuppressWarnings("rawtypes")
  ScenarioScopedComponent.Builder scopedComponentBuilder();
}
