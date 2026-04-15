# Migrating to cucumber-dagger

This page covers migration paths from three common Cucumber integration frameworks: cucumber-spring, cucumber-picocontainer, and cucumber-guice. Each section explains the conceptual mapping, highlights key differences, and gives step-by-step instructions.

---

## From cucumber-spring

### Conceptual mapping

| cucumber-spring | cucumber-dagger |
|---|---|
| `@SpringBootTest` + `@CucumberContextConfiguration` on a class | `@CucumberDaggerConfiguration` + `@Component` on an interface |
| `@Autowired` field or constructor injection | `@Inject` constructor injection |
| `@Scope("cucumber-glue")` on a bean class | `@Provides @ScenarioScope` method in a `@Module` |
| `@MockBean` in a `@Configuration` class | `@Binds` in a test `@Module` that binds a mock implementation |
| Spring profiles to switch between configurations | Listing different `@Module` classes on the `@Component` |
| `@Value("${property}")` injection | `@Provides @Singleton` method reading the value from the environment |

### Key differences

- **No application context lifecycle.** cucumber-dagger does not start a Spring application context or any embedded server. If you rely on Spring Boot's auto-configuration you need to wire those services explicitly via `@Provides` methods.
- **No property-file injection.** There is no equivalent of `@Value`. Read configuration values in a `@Provides @Singleton` method and return a typed object or a string.
- **Mocking.** `@MockBean` replaces a bean in the Spring context. In Dagger you create a mock manually and bind it via a `@Binds` method (or a `@Provides` method that returns the mock) in a test module.

### Step-by-step migration

1. **Remove Spring dependencies** from your build file:
   ```kotlin
   // Remove
   testImplementation("io.cucumber:cucumber-spring")
   testImplementation("org.springframework.boot:spring-boot-starter-test")
   ```

2. **Add cucumber-dagger dependencies** (see [getting-started.md](getting-started.md) for the full snippet).

3. **Convert the configuration class to a root component.** Replace the Spring `@Configuration` class annotated with `@CucumberContextConfiguration` with a `@CucumberDaggerConfiguration @Component` interface:
   ```java
   // Before (Spring)
   @CucumberContextConfiguration
   @SpringBootTest
   public class CucumberSpringConfig {}

   // After (Dagger)
   @CucumberDaggerConfiguration
   @Singleton
   @Component(modules = {AppModule.class, ScenarioModule.class})
   public interface IntegrationTestConfig {}
   ```

4. **Convert scoped beans.** Find every Spring bean annotated with `@Scope("cucumber-glue")` and move the construction logic to a `@Provides @ScenarioScope` method in a new module:
   ```java
   // Before (Spring)
   @Component
   @Scope("cucumber-glue")
   public class Basket { ... }

   // After (Dagger) - in ScenarioModule
   @Provides
   @ScenarioScope
   static Basket provideBasket(PriceList priceList) {
       return new Basket(priceList);
   }
   ```

5. **Convert autowired fields to `@Inject` constructors.** Spring supports field injection and setter injection; Dagger requires constructor injection:
   ```java
   // Before (Spring)
   public class CheckoutSteps {
       @Autowired
       private Basket basket;
   }

   // After (Dagger)
   public final class CheckoutSteps {
       private final Basket basket;

       @Inject
       public CheckoutSteps(Basket basket) {
           this.basket = basket;
       }
   }
   ```

6. **Move step definitions to the glue package.** Ensure that every step definition class lives in the same package as `IntegrationTestConfig` or in a sub-package of it.

7. **Update `junit-platform.properties`** to point `cucumber.glue` at the package that contains your root component.

---

## From cucumber-picocontainer

### Key difference

cucumber-picocontainer injects any class that has a single constructor, regardless of whether it is annotated. cucumber-dagger requires the constructor to be annotated with `@Inject`. This is the only structural difference for most projects.

### Step-by-step migration

1. **Remove the picocontainer dependency:**
   ```kotlin
   // Remove
   testImplementation("io.cucumber:cucumber-picocontainer")
   ```

2. **Add cucumber-dagger dependencies** (see [getting-started.md](getting-started.md)).

3. **Create a root component.** picocontainer needs no configuration class; Dagger does. Create an interface annotated with `@CucumberDaggerConfiguration`, `@Singleton`, and `@Component`:
   ```java
   @CucumberDaggerConfiguration
   @Singleton
   @Component(modules = {ScenarioModule.class})
   public interface IntegrationTestConfig {}
   ```

4. **Add `@Inject` to every step definition constructor.** picocontainer discovered any single-constructor class automatically. Dagger requires the `@Inject` annotation:
   ```java
   // Before (picocontainer)
   public class CheckoutSteps {
       private final Basket basket;
       public CheckoutSteps(Basket basket) {
           this.basket = basket;
       }
   }

   // After (Dagger)
   public final class CheckoutSteps {
       private final Basket basket;

       @Inject
       public CheckoutSteps(Basket basket) {
           this.basket = basket;
       }
   }
   ```

5. **Extract shared mutable state into scoped bindings.** picocontainer creates step definition instances for each scenario; shared state was sometimes stored in mutable fields on a shared step definition class. Move that state into `@Provides @ScenarioScope` bindings in a module:
   ```java
   // Before (picocontainer) - shared state in a step def
   public class SharedState {
       public Basket basket = new Basket();
   }

   // After (Dagger) - explicit scoped binding
   @Module
   public final class ScenarioModule {
       @Provides
       @ScenarioScope
       static Basket provideBasket(PriceList priceList) {
           return new Basket(priceList);
       }
   }
   ```

6. **Declare singleton dependencies explicitly.** picocontainer could instantiate any class; Dagger requires all dependencies to be declared via `@Provides` methods or `@Inject` constructors. Add a `@Provides @Singleton` method to a root module for any dependency that should be shared across scenarios.

---

## From cucumber-guice

### Conceptual mapping

| cucumber-guice | cucumber-dagger |
|---|---|
| `@InjectorSource` on a class that creates a Guice `Injector` | `@CucumberDaggerConfiguration @Component` interface |
| `@ScenarioScoped` annotation on a class | `@Provides @ScenarioScope` method in a `@Module` |
| Guice `@Singleton` annotation on a class | `@Provides @Singleton` method in a `@Module` (or `@Singleton` on the `@Component`) |
| Guice `Module` implementing `AbstractModule` | Dagger `@Module` class with `@Provides` and `@Binds` methods |

### Key difference

cucumber-guice allows you to annotate a class directly with `@ScenarioScoped` and Guice manages the scope. cucumber-dagger enforces scope declarations in modules: you declare the scope on a `@Provides` method, not on the class. This means you cannot rely on field injection or class-level scope annotations from Guice; you must use constructor injection and explicit factory methods.

### Step-by-step migration

1. **Replace the Guice dependency:**
   ```kotlin
   // Remove
   testImplementation("io.cucumber:cucumber-guice")
   testImplementation("com.google.inject:guice")

   // Add
   testImplementation("dev.joss:cucumber-dagger")
   testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
   testAnnotationProcessor("com.google.dagger:dagger-compiler:<version>")
   ```

2. **Replace `@InjectorSource` with `@CucumberDaggerConfiguration`.** The Guice `@InjectorSource` class (or method) is the entry point for Guice's integration. Replace it with a Dagger `@Component` interface:
   ```java
   // Before (Guice)
   @InjectorSource
   public class CucumberGuiceContext implements InjectorSource {
       @Override
       public Injector getInjector() {
           return Guice.createInjector(new AppModule());
       }
   }

   // After (Dagger)
   @CucumberDaggerConfiguration
   @Singleton
   @Component(modules = {AppModule.class, ScenarioModule.class})
   public interface IntegrationTestConfig {}
   ```

3. **Move `@ScenarioScoped` class annotations to `@Provides` methods in a new module.** Guice resolves `@ScenarioScoped` by looking at the class annotation. Dagger does not use class-level scope annotations for scope management. For each class annotated with `@ScenarioScoped`, add a `@Provides @ScenarioScope` factory method:
   ```java
   // Before (Guice) - scope declared on the class
   @ScenarioScoped
   public class Basket {
       @Inject
       public Basket(PriceList priceList) { ... }
   }

   // After (Dagger) - scope declared in a module
   @Module
   public final class ScenarioModule {
       @Provides
       @ScenarioScope
       static Basket provideBasket(PriceList priceList) {
           return new Basket(priceList);
       }
   }
   ```

4. **Convert Guice modules to Dagger modules.** Guice modules extend `AbstractModule` and use `bind()` calls. Dagger modules use `@Provides` and `@Binds` methods:
   ```java
   // Before (Guice)
   public class AppModule extends AbstractModule {
       @Override
       protected void configure() {
           bind(ReceiptFormatter.class).to(PlainReceiptFormatter.class).in(Singleton.class);
       }
   }

   // After (Dagger)
   @Module
   public abstract class AppModule {
       @Binds
       @Singleton
       abstract ReceiptFormatter bindReceiptFormatter(PlainReceiptFormatter impl);
   }
   ```

5. **Add `@Inject` to any constructor that relies on Guice field injection.** Guice supports field injection (`@Inject` on a field); Dagger requires constructor injection. Consolidate all injected fields into a single `@Inject` constructor.
