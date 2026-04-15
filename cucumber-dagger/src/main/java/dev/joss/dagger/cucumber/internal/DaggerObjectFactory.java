package dev.joss.dagger.cucumber.internal;

import dev.joss.dagger.cucumber.api.ComponentResolver;
import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.ScenarioScopedComponent;
import io.cucumber.core.backend.ObjectFactory;

/**
 * Cucumber {@link ObjectFactory} implementation that resolves step definitions and scoped objects
 * from a Dagger component graph.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li><strong>Construction</strong> - registers itself with {@link ObjectFactoryHolder} so that
 *       {@link DaggerBackend} can obtain this instance.
 *   <li><strong>{@link #configure}</strong> - called once by {@link DaggerBackend#loadGlue} with
 *       the root component and the generated {@link ComponentResolver}.
 *   <li><strong>{@link #buildWorld()}</strong> - called before each scenario. Delegates to {@link
 *       ComponentResolver#createScoped} to create a fresh per-scenario subcomponent.
 *   <li><strong>{@link #getInstance}</strong> - resolves a step-definition or scoped object via the
 *       resolver, checking the scoped component first then the root component.
 *   <li><strong>{@link #disposeWorld()}</strong> - called after each scenario. Clears the
 *       per-scenario subcomponent so the next scenario starts clean.
 * </ol>
 */
public final class DaggerObjectFactory implements ObjectFactory {

  private CucumberDaggerComponent rootComponent;
  private ComponentResolver resolver;

  @SuppressWarnings(
      "ThreadLocalUsage") // intentional instance field - prevents cross-factory leakage
  private final ThreadLocal<ScenarioScopedComponent> currentScoped = new ThreadLocal<>();

  /** Creates the factory and registers it with {@link ObjectFactoryHolder}. */
  public DaggerObjectFactory() {
    ObjectFactoryHolder.register(this);
  }

  /**
   * Configures this factory with the root Dagger component and the generated component resolver.
   *
   * @param root the root Dagger component instance
   * @param resolver the generated {@link ComponentResolver} for type dispatch
   */
  void configure(CucumberDaggerComponent root, ComponentResolver resolver) {
    this.rootComponent = root;
    this.resolver = resolver;
  }

  /** No-op: the root Dagger component is created once and lives for the whole test run. */
  @Override
  public void start() {}

  /** No-op: root component and resolver are long-lived for the entire test run. */
  @Override
  public void stop() {}

  /**
   * Always returns {@code true}; step-definition classes are resolved lazily via the Dagger graph
   * rather than being eagerly instantiated.
   */
  @Override
  public boolean addClass(Class<?> _clazz) {
    return true;
  }

  /**
   * Returns the instance of {@code type} for the current scenario. Checks the scoped component
   * first, then falls back to the root component. Scoped and root caching is delegated to Dagger's
   * own scope machinery.
   *
   * @throws IllegalStateException if {@code type} is not provided by either component
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getInstance(Class<T> type) {
    ScenarioScopedComponent scoped = currentScoped.get();
    Object instance = scoped != null ? resolver.resolveScoped(type, scoped) : null;
    if (instance == null) {
      instance = resolver.resolveRoot(type, rootComponent);
    }
    if (instance == null) {
      throw new IllegalStateException(
          "Type "
              + type.getName()
              + " is not provided by any component."
              + " Ensure it is a step-definition class with an @Inject constructor,"
              + " or is bound via @Provides @ScenarioScope in a module listed on your"
              + " @CucumberDaggerConfiguration component.");
    }
    return (T) instance;
  }

  /**
   * Called by {@link DaggerBackend#buildWorld()} before each scenario. Creates a fresh {@link
   * ScenarioScopedComponent} via the resolver.
   */
  void buildWorld() {
    if (resolver == null) {
      throw new IllegalStateException(
          "The cucumber-dagger-processor has not run. "
              + "Please ensure the annotationProcessor dependency is configured "
              + "and @CucumberDaggerConfiguration is present on your root component.");
    }
    currentScoped.set(resolver.createScoped(rootComponent));
  }

  /**
   * Called by {@link DaggerBackend#disposeWorld()} after each scenario. Clears the per-scenario
   * subcomponent so that the next scenario starts clean.
   */
  void disposeWorld() {
    currentScoped.remove();
  }
}
