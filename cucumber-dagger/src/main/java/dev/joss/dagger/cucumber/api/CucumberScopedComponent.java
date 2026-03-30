package dev.joss.dagger.cucumber.api;

/**
 * Marker interface for the Dagger subcomponent that is created once per Cucumber scenario.
 *
 * <p>The annotation processor generates a concrete implementation ({@code
 * GeneratedScopedComponent}) in the same package as the {@link
 * CucumberDaggerConfiguration}-annotated component. That generated interface extends this one and
 * declares provision methods for every {@link CucumberScoped} type and every step-definition class
 * found in the glue package.
 *
 * <p>User code does not normally implement or reference this interface directly.
 */
public interface CucumberScopedComponent {

  /**
   * Factory for creating a {@link CucumberScopedComponent} instance.
   *
   * <p>Dagger generates a concrete implementation of this builder for each
   * {@code @Subcomponent}-annotated interface that extends {@link CucumberScopedComponent}.
   *
   * @param <T> the concrete subcomponent type
   */
  interface Builder<T extends CucumberScopedComponent> {

    /**
     * Creates and returns a new scoped component instance.
     *
     * @return a new instance of the per-scenario subcomponent
     */
    T build();
  }
}
