package dev.joss.dagger.cucumber.internal;

import dev.joss.dagger.cucumber.api.ComponentResolver;
import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Container;
import io.cucumber.core.backend.Glue;
import io.cucumber.core.backend.Lookup;
import io.cucumber.core.backend.Snippet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cucumber {@link Backend} implementation that wires Dagger into the Cucumber lifecycle.
 *
 * <p>During {@link #loadGlue} the backend:
 *
 * <ol>
 *   <li>Reads the {@code META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent}
 *       service file to locate the Dagger-generated root component factory class ({@code
 *       DaggerXxx}) and calls its static {@code create()} method, or falls back to {@code
 *       builder().build()} when the component declares a {@code @Component.Builder}.
 *   <li>Loads the annotation-processor-generated {@link ComponentResolver} by its fixed class name
 *       ({@code dev.joss.dagger.cucumber.generated.GeneratedComponentResolver}).
 *   <li>Passes both to {@link DaggerObjectFactory#configure}.
 * </ol>
 *
 * <p>{@link #buildWorld()} and {@link #disposeWorld()} delegate directly to {@link
 * DaggerObjectFactory} to manage the per-scenario subcomponent lifecycle.
 */
class DaggerBackend implements Backend {

  private static final String RESOLVER_CLASS =
      "dev.joss.dagger.cucumber.generated.GeneratedComponentResolver";
  private static final String COMPONENT_SERVICE_FILE =
      "META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent";

  private final Container container;
  private final Supplier<ClassLoader> classLoaderSupplier;

  /** Creates a new backend. The {@code _lookup} parameter is required by the SPI but not used. */
  DaggerBackend(Lookup _lookup, Container container, Supplier<ClassLoader> classLoaderSupplier) {
    this.container = container;
    this.classLoaderSupplier = classLoaderSupplier;
  }

  /**
   * Loads the root Dagger component and component resolver, then configures the {@link
   * DaggerObjectFactory}. Called once by Cucumber before any scenarios run.
   */
  @Override
  public void loadGlue(Glue _glue, List<URI> _gluePaths) {
    CucumberDaggerComponent component = loadComponent();
    ComponentResolver resolver = loadResolver();
    container.addClass(component.getClass());
    getFactory().configure(component, resolver);
  }

  /** Creates a fresh per-scenario Dagger subcomponent. Called before each scenario. */
  @Override
  public void buildWorld() {
    getFactory().buildWorld();
  }

  /** Disposes the per-scenario subcomponent. Called after each scenario. */
  @Override
  public void disposeWorld() {
    getFactory().disposeWorld();
  }

  /** Returns {@code null}; snippet generation is not supported by this backend. */
  @Override
  public Snippet getSnippet() {
    return null;
  }

  /**
   * Returns the registered {@link DaggerObjectFactory}, failing fast if none has been registered.
   *
   * @throws IllegalStateException if the {@code ObjectFactory} SPI has not been loaded
   */
  private DaggerObjectFactory getFactory() {
    DaggerObjectFactory factory = ObjectFactoryHolder.get();
    if (factory == null) {
      throw new IllegalStateException(
          "DaggerObjectFactory has not been registered. "
              + "Ensure the cucumber-dagger ObjectFactory SPI entry is on the classpath "
              + "(META-INF/services/io.cucumber.core.backend.ObjectFactory).");
    }
    return factory;
  }

  /**
   * Reads the component service file, loads the Dagger-generated factory class by name, and
   * instantiates the root component.
   *
   * <p>Two construction strategies are attempted in order:
   *
   * <ol>
   *   <li>{@code create()} - used when the component has no {@code @Component.Builder} (the common
   *       case; Dagger only generates this method when no {@code @BindsInstance} setters are
   *       required).
   *   <li>{@code builder().build()} - used when the component declares a {@code @Component.Builder}
   *       inner interface, causing Dagger to generate a {@code builder()} factory instead of (or in
   *       addition to) {@code create()}. The builder must be usable with no explicit setter calls
   *       (no-arg builder contract: all {@code @BindsInstance} parameters must be {@code @Nullable}
   *       so that {@code build()} succeeds without setting them).
   * </ol>
   *
   * @throws IllegalStateException if no service entry is found, more than one is found, or neither
   *     instantiation strategy succeeds
   */
  private CucumberDaggerComponent loadComponent() {
    ClassLoader cl = classLoaderSupplier.get();
    List<String> classNames = readServiceFile(cl);
    if (classNames.isEmpty()) {
      throw new IllegalStateException(
          "The cucumber-dagger-processor has not run. "
              + "No CucumberDaggerComponent found in service file. "
              + "Please add the annotationProcessor dependency.");
    }
    if (classNames.size() > 1) {
      throw new IllegalStateException(
          "Multiple CucumberDaggerComponent factories found: " + classNames);
    }
    String factoryClassName = classNames.getFirst();
    try {
      Class<?> factoryClass = Class.forName(factoryClassName, true, cl);
      return instantiateComponent(factoryClass, factoryClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Could not load component class: " + factoryClassName, e);
    }
  }

  /**
   * Tries {@code create()} first, then falls back to {@code builder().build()} when the component
   * was generated with a {@code @Component.Builder}.
   */
  private static CucumberDaggerComponent instantiateComponent(
      Class<?> factoryClass, String factoryClassName) {
    // Strategy 1: static create() - present when no @Component.Builder with required @BindsInstance
    try {
      Method createMethod = factoryClass.getMethod("create");
      return (CucumberDaggerComponent) createMethod.invoke(null);
    } catch (NoSuchMethodException ignored) {
      // fall through to builder strategy
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("Failed to invoke " + factoryClassName + ".create()", e);
    }

    // Strategy 2: builder().build() - present when @Component.Builder is declared.
    // build() is looked up on the public Builder interface (the return type of builder()), not on
    // the concrete implementation class, so no setAccessible is needed.
    try {
      Method builderMethod = factoryClass.getMethod("builder");
      Object builder = builderMethod.invoke(null);
      Method buildMethod = builderMethod.getReturnType().getMethod("build");
      return (CucumberDaggerComponent) buildMethod.invoke(builder);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          factoryClassName
              + " has neither a static create() nor a static builder() method. "
              + "Dagger generates create() when all modules have no-arg or static @Provides "
              + "methods and no @BindsInstance parameters are required. "
              + "If your component uses @Component.Builder, ensure it is detectable by the "
              + "cucumber-dagger-processor and that builder().build() succeeds without "
              + "explicit @BindsInstance setter calls (no-arg builder contract).",
          e);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException(
          "Failed to invoke " + factoryClassName + ".builder().build()", e);
    }
  }

  /**
   * Loads the annotation-processor-generated {@link ComponentResolver} by its fixed class name.
   *
   * @throws IllegalStateException if the resolver class is not found on the classpath
   */
  private ComponentResolver loadResolver() {
    try {
      Class<?> resolverClass = Class.forName(RESOLVER_CLASS, true, classLoaderSupplier.get());
      return (ComponentResolver) resolverClass.getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "GeneratedComponentResolver not found. "
              + "Ensure the cucumber-dagger-processor annotationProcessor dependency is configured "
              + "and @CucumberDaggerConfiguration is present on your root component.",
          e);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to instantiate GeneratedComponentResolver", e);
    }
  }

  /**
   * Reads all entries from {@link #COMPONENT_SERVICE_FILE} on the given class loader, skipping
   * blank lines and comment lines.
   */
  private List<String> readServiceFile(ClassLoader cl) {
    List<String> result = new ArrayList<>();
    try {
      Enumeration<URL> urls = cl.getResources(COMPONENT_SERVICE_FILE);
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
              result.add(line);
            }
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read service file: " + COMPONENT_SERVICE_FILE, e);
    }
    return result;
  }
}
