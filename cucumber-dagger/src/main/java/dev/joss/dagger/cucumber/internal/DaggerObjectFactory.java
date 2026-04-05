package dev.joss.dagger.cucumber.internal;

import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.CucumberScopedComponent;
import io.cucumber.core.backend.ObjectFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cucumber {@link ObjectFactory} implementation that resolves step definitions and scoped objects
 * from a Dagger component graph.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li><strong>Construction</strong> — registers itself with {@link ObjectFactoryHolder} so that
 *       {@link DaggerBackend} can obtain this instance.
 *   <li><strong>{@link #configure}</strong> — called once by {@link DaggerBackend#loadGlue} with
 *       the root component and the generated scoped-component interface. Sets up method handles for
 *       root-component provision methods and scoped-component provision/step-def methods.
 *   <li><strong>{@link #buildWorld()}</strong> — called before each scenario. Calls {@link
 *       CucumberDaggerComponent#scopedComponentBuilder()} to create a fresh {@link
 *       CucumberScopedComponent} and binds per-scenario suppliers.
 *   <li><strong>{@link #getInstance}</strong> — resolves a step-definition or scoped object,
 *       caching the result within the current scenario.
 *   <li><strong>{@link #disposeWorld()}</strong> — called after each scenario. Clears the
 *       per-scenario supplier and instance caches.
 * </ol>
 */
public final class DaggerObjectFactory implements ObjectFactory {

  private CucumberDaggerComponent rootComponent;
  private final Map<Class<?>, MethodHandle> rootHandles = new HashMap<>();
  private final Map<Class<?>, MethodHandle> scopedHandles = new HashMap<>();
  private static final ThreadLocal<Map<Class<?>, Supplier<Object>>> scopedSuppliers =
      new ThreadLocal<>();
  private final ThreadLocal<Map<Class<?>, Object>> instances =
      ThreadLocal.withInitial(HashMap::new);

  /** Creates the factory and registers it with {@link ObjectFactoryHolder}. */
  public DaggerObjectFactory() {
    ObjectFactoryHolder.register(this);
  }

  /**
   * Configures this factory with the root Dagger component and the generated scoped-component
   * interface.
   *
   * <p>Reflectively inspects {@code root}'s interface for zero-argument provision methods and
   * stores bound method handles for them. Does the same for {@code scopedInterface}, leaving those
   * handles unbound so they can be rebound to a fresh subcomponent instance each scenario.
   *
   * @param root the root Dagger component instance
   * @param scopedInterface the generated {@code GeneratedScopedComponent} interface class
   */
  void configure(
      CucumberDaggerComponent root, Class<? extends CucumberScopedComponent> scopedInterface) {
    this.rootComponent = root;
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    Class<?> rootInterface =
        Arrays.stream(root.getClass().getInterfaces())
            .filter(
                i ->
                    CucumberDaggerComponent.class.isAssignableFrom(i)
                        && !i.equals(CucumberDaggerComponent.class))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Root component "
                            + root.getClass()
                            + " does not implement a CucumberDaggerComponent sub-interface"));
    for (Method method : rootInterface.getMethods()) {
      if (method.getParameterCount() != 0) continue;
      if (method.getReturnType().equals(Void.TYPE)) continue;
      if (CucumberScopedComponent.Builder.class.isAssignableFrom(method.getReturnType())) continue;
      try {
        rootHandles.put(method.getReturnType(), lookup.unreflect(method).bindTo(root));
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Failed to unreflect root method: " + method, e);
      }
    }

    for (Method method : scopedInterface.getMethods()) {
      if (method.getParameterCount() != 0) continue;
      if (method.getReturnType().equals(Void.TYPE)) continue;
      if (CucumberScopedComponent.Builder.class.isAssignableFrom(method.getReturnType())) continue;
      try {
        scopedHandles.put(method.getReturnType(), lookup.unreflect(method));
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Failed to unreflect scoped method: " + method, e);
      }
    }
  }

  /** No-op: the root Dagger component is created once and lives for the whole test run. */
  @Override
  public void start() {
    // no-op
  }

  /** No-op: root component and handles are long-lived for the entire test run. */
  @Override
  public void stop() {
    // no-op: root component and handles are long-lived for the entire test run
  }

  /**
   * Always returns {@code true}; step-definition classes are resolved lazily via the Dagger graph
   * rather than being eagerly instantiated.
   */
  @Override
  public boolean addClass(Class<?> _clazz) {
    return true;
  }

  /**
   * Returns the instance of {@code type} for the current scenario, creating it via the Dagger graph
   * on first access and caching it for the remainder of the scenario.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getInstance(Class<T> type) {
    return (T) instances.get().computeIfAbsent(type, this::resolve);
  }

  /**
   * Called by {@link DaggerBackend#buildWorld()} before each scenario. Creates a fresh {@link
   * CucumberScopedComponent} via the root component's builder and binds the scoped provision method
   * handles to it.
   */
  @SuppressWarnings({"rawtypes"})
  void buildWorld() {
    if (rootComponent == null) {
      throw new IllegalStateException(
          "The cucumber-dagger-processor has not run. "
              + "Please add the annotationProcessor dependency and include "
              + "CucumberDaggerModule in your @Component modules list.");
    }
    CucumberScopedComponent.Builder builder = rootComponent.scopedComponentBuilder();
    CucumberScopedComponent scoped = builder.build();
    Map<Class<?>, Supplier<Object>> bound = new HashMap<>();
    for (Map.Entry<Class<?>, MethodHandle> entry : scopedHandles.entrySet()) {
      MethodHandle boundHandle = entry.getValue().bindTo(scoped);
      bound.put(
          entry.getKey(),
          () -> {
            try {
              return boundHandle.invoke();
            } catch (Throwable t) {
              throw new RuntimeException("Failed to invoke scoped provision method", t);
            }
          });
    }
    scopedSuppliers.set(bound);
    instances.get().clear();
  }

  /**
   * Called by {@link DaggerBackend#disposeWorld()} after each scenario. Clears the per-scenario
   * supplier map and instance cache so that the next scenario starts clean.
   */
  void disposeWorld() {
    scopedSuppliers.remove();
    instances.remove();
  }

  /**
   * Resolves {@code type} by checking the scoped suppliers first (scenario-scoped objects and step
   * definitions), then falling back to root-component provision methods.
   *
   * @throws IllegalStateException if {@code type} is not provided by either component
   */
  private Object resolve(Class<?> type) {
    Map<Class<?>, Supplier<Object>> scoped = scopedSuppliers.get();
    if (scoped != null && scoped.containsKey(type)) {
      return scoped.get(type).get();
    }
    if (rootHandles.containsKey(type)) {
      try {
        return rootHandles.get(type).invoke();
      } catch (Throwable t) {
        throw new RuntimeException("Failed to invoke root provision method for " + type, t);
      }
    }
    throw new IllegalStateException(
        "Type "
            + type.getName()
            + " is not declared on either component."
            + " Available scoped types: "
            + (scoped != null ? scoped.keySet() : "[]")
            + ". Available root types: "
            + rootHandles.keySet());
  }
}
