package dev.joss.dagger.cucumber.internal;

import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.ScenarioScopedComponent;
import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Container;
import io.cucumber.core.backend.Glue;
import io.cucumber.core.backend.Lookup;
import io.cucumber.core.backend.Snippet;
import io.cucumber.core.resource.ClasspathScanner;
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
 *       DaggerXxx}) and calls its static {@code create()} method to obtain the root component
 *       instance.
 *   <li>Resolves the generated {@code GeneratedScopedComponent} interface, preferring the
 *       annotation-processor-generated {@code ScenarioScopedComponentAccessor} and falling back to
 *       a classpath scan.
 *   <li>Passes both to {@link DaggerObjectFactory#configure} so the object factory can set up
 *       method handles for provision and scenario-scoped objects.
 * </ol>
 *
 * <p>{@link #buildWorld()} and {@link #disposeWorld()} delegate directly to {@link
 * DaggerObjectFactory} to manage the per-scenario subcomponent lifecycle.
 */
class DaggerBackend implements Backend {

  private static final String ACCESSOR_CLASS =
      "dev.joss.dagger.cucumber.generated.ScenarioScopedComponentAccessor";
  private static final String COMPONENT_SERVICE_FILE =
      "META-INF/services/dev.joss.dagger.cucumber.api.CucumberDaggerComponent";

  private final Container container;
  private final ClasspathScanner classPathScanner;
  private final Supplier<ClassLoader> classLoaderSupplier;

  /** Creates a new backend. The {@code _lookup} parameter is required by the SPI but not used. */
  DaggerBackend(Lookup _lookup, Container container, Supplier<ClassLoader> classLoaderSupplier) {
    this.container = container;
    this.classLoaderSupplier = classLoaderSupplier;
    this.classPathScanner = new ClasspathScanner(classLoaderSupplier);
  }

  /**
   * Loads the root Dagger component and scoped-component interface, then configures the {@link
   * DaggerObjectFactory}. Called once by Cucumber before any scenarios run.
   */
  @Override
  public void loadGlue(Glue _glue, List<URI> gluePaths) {
    CucumberDaggerComponent component = loadComponent();
    Class<? extends ScenarioScopedComponent> scopedInterface = resolveScopedInterface(gluePaths);
    container.addClass(component.getClass());
    getFactory().configure(component, scopedInterface);
  }

  /** Creates a fresh per-scenario Dagger subcomponent. Called before each scenario. */
  @Override
  public void buildWorld() {
    getFactory().buildWorld();
  }

  /**
   * Disposes the per-scenario subcomponent and clears cached instances. Called after each scenario.
   */
  @Override
  public void disposeWorld() {
    getFactory().disposeWorld();
  }

  /**
   * Returns the registered {@link DaggerObjectFactory}, failing fast if none has been registered.
   *
   * @throws IllegalStateException if the {@code ObjectFactory} SPI has not been loaded — this
   *     indicates the service-file entry is missing or Cucumber's service-loader did not run before
   *     the backend lifecycle methods were called
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

  /** Returns {@code null}; snippet generation is not supported by this backend. */
  @Override
  public Snippet getSnippet() {
    return null;
  }

  /**
   * Reads the component service file, loads the Dagger-generated factory class by name, and calls
   * its static {@code create()} method to obtain the root {@link CucumberDaggerComponent} instance.
   *
   * @throws IllegalStateException if no service entry is found, more than one is found, or the
   *     factory cannot be invoked
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
      Method createMethod = factoryClass.getMethod("create");
      return (CucumberDaggerComponent) createMethod.invoke(null);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Could not load component class: " + factoryClassName, e);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          factoryClassName
              + " does not have a static create() method. "
              + "Ensure CucumberDaggerModule is in your @Component modules list.",
          e);
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new IllegalStateException("Failed to invoke " + factoryClassName + ".create()", e);
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

  /**
   * Resolves the generated {@code GeneratedScopedComponent} interface class.
   *
   * <p>Tries to load the annotation-processor-generated {@code ScenarioScopedComponentAccessor}
   * first. If that class is not on the classpath (e.g., the processor has not run), falls back to
   * {@link #scanForScopedComponent(List)}.
   */
  @SuppressWarnings("unchecked")
  private Class<? extends ScenarioScopedComponent> resolveScopedInterface(List<URI> gluePaths) {
    try {
      Class<?> accessorClass = Class.forName(ACCESSOR_CLASS, true, classLoaderSupplier.get());
      Object accessor = accessorClass.getDeclaredConstructor().newInstance();
      return (Class<? extends ScenarioScopedComponent>)
          accessorClass.getMethod("getScopedComponentClass").invoke(accessor);
    } catch (ClassNotFoundException e) {
      return scanForScopedComponent(gluePaths);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to load ScenarioScopedComponentAccessor", e);
    }
  }

  /**
   * Fallback: scans the given glue-path packages for a non-interface subclass of {@link
   * ScenarioScopedComponent}.
   *
   * @throws IllegalStateException if no implementation is found in any of the glue paths
   */
  private Class<? extends ScenarioScopedComponent> scanForScopedComponent(List<URI> gluePaths) {
    for (URI uri : gluePaths) {
      if (!"classpath".equals(uri.getScheme())) continue;
      String path = uri.getSchemeSpecificPart();
      if (path.startsWith("/")) path = path.substring(1);
      String packageName = path.replace('/', '.');
      List<Class<? extends ScenarioScopedComponent>> found =
          classPathScanner.scanForSubClassesInPackage(packageName, ScenarioScopedComponent.class);
      if (!found.isEmpty()) {
        return found.get(0);
      }
    }
    throw new IllegalStateException(
        "No ScenarioScopedComponent implementation found in glue paths: "
            + gluePaths
            + ". Please ensure cucumber-dagger-processor has run.");
  }
}
