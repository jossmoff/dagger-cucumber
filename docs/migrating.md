# Migrating to cucumber-dagger

## From cucumber-spring

### Conceptual mapping

| cucumber-spring | cucumber-dagger |
|---|---|
| `@SpringBootTest` + `@CucumberContextConfiguration` on a class | `@CucumberDaggerConfiguration @Component` on an interface |
| `@Autowired` field or constructor injection | `@Inject` constructor injection |
| `@Scope("cucumber-glue")` on a bean | `@Provides @ScenarioScope` in a `@Module` |
| `@MockBean` in a `@Configuration` class | `@Binds` binding a mock implementation in a test `@Module` |
| Spring profiles | Different `@Module` classes on `@Component` |
| `@Value("${property}")` | `@Nullable @BindsInstance` + `@Provides @Singleton` reading from env |

### Key differences

- No application context lifecycle — wire services explicitly via `@Provides`.
- No property-file injection — read configuration in `@Provides @Singleton` methods.
- Mocking — create mocks manually and bind via `@Binds` or `@Provides` in a test module.

### Step-by-step

1. **Remove Spring dependencies:**
   ```kotlin
   // Remove
   testImplementation("io.cucumber:cucumber-spring")
   testImplementation("org.springframework.boot:spring-boot-starter-test")
   ```

2. **Add cucumber-dagger** — see [getting-started.md](getting-started.md).

3. **Replace `@CucumberContextConfiguration` with a root component:**
   ```java
   // Before (Spring)
   @CucumberContextConfiguration
   @SpringBootTest
   public class CucumberSpringConfig {}

   // After (Dagger)
   @CucumberDaggerConfiguration
   @Singleton
   @Component(modules = {AppModule.class, ScenarioModule.class})
   public interface IntegrationTestConfig {

       @Component.Builder
       interface Builder {
           @BindsInstance Builder baseUrl(@Nullable String baseUrl);
           IntegrationTestConfig build();
       }
   }
   ```

4. **Convert `@Value` properties to `@BindsInstance` + `@Provides`:**
   ```java
   @Provides
   @Singleton
   static ApiClient provideApiClient(@Nullable String baseUrl) {
       String url = baseUrl != null ? baseUrl : System.getProperty("base.url", "http://localhost:8080");
       return new ApiClient(url);
   }
   ```

5. **Convert `@Scope("cucumber-glue")` beans to `@Provides @ScenarioScope`:**
   ```java
   // Before (Spring)
   @Component
   @Scope("cucumber-glue")
   public class Basket { ... }

   // After (Dagger) — in ScenarioModule
   @Provides
   @ScenarioScope
   static Basket provideBasket(ApiClient client) {
       return new Basket(client);
   }
   ```

6. **Convert field injection to `@Inject` constructors:**
   ```java
   // Before (Spring)
   public class CheckoutSteps {
       @Autowired private Basket basket;
   }

   // After (Dagger)
   public final class CheckoutSteps {
       private final Basket basket;

       @Inject
       public CheckoutSteps(Basket basket) { this.basket = basket; }
   }
   ```

7. Move step definitions to the root component's package or a sub-package.
8. Update `junit-platform.properties` to set `cucumber.glue`.

---

## From cucumber-picocontainer

### Key difference

picocontainer discovers any single-constructor class automatically. cucumber-dagger requires the constructor to be annotated with `@Inject`.

### Step-by-step

1. **Remove picocontainer:**
   ```kotlin
   testImplementation("io.cucumber:cucumber-picocontainer")  // Remove
   ```

2. **Add cucumber-dagger** — see [getting-started.md](getting-started.md).

3. **Create a root component:**
   ```java
   @CucumberDaggerConfiguration
   @Singleton
   @Component(modules = {AppModule.class, ScenarioModule.class})
   public interface IntegrationTestConfig {

       @Component.Builder
       interface Builder {
           @BindsInstance Builder baseUrl(@Nullable String baseUrl);
           IntegrationTestConfig build();
       }
   }
   ```

4. **Add `@Inject` to every step definition constructor:**
   ```java
   // Before (picocontainer)
   public class CheckoutSteps {
       public CheckoutSteps(Basket basket) { ... }
   }

   // After (Dagger)
   public final class CheckoutSteps {
       @Inject
       public CheckoutSteps(Basket basket) { ... }
   }
   ```

5. **Move shared mutable state to `@Provides @ScenarioScope`:**
   ```java
   // Before (picocontainer) — shared state on a step def
   public class SharedState {
       public Basket basket = new Basket();
   }

   // After (Dagger)
   @Provides
   @ScenarioScope
   static Basket provideBasket(ApiClient client) {
       return new Basket(client);
   }
   ```

6. Add `@Provides @Singleton` for any singleton dependencies previously auto-instantiated by picocontainer.

---

## From cucumber-guice

### Conceptual mapping

| cucumber-guice | cucumber-dagger |
|---|---|
| `@InjectorSource` class | `@CucumberDaggerConfiguration @Component` interface |
| `@ScenarioScoped` on a class | `@Provides @ScenarioScope` in a `@Module` |
| Guice `@Singleton` on a class | `@Provides @Singleton` in a `@Module` |
| `AbstractModule` with `bind()` calls | `@Module` with `@Provides` / `@Binds` methods |

### Key difference

Guice resolves `@ScenarioScoped` from class annotations. Dagger does not — scope is declared on `@Provides` methods in modules.

### Step-by-step

1. **Replace Guice dependencies:**
   ```kotlin
   // Remove
   testImplementation("io.cucumber:cucumber-guice")
   testImplementation("com.google.inject:guice")

   // Add
   testImplementation("dev.joss:cucumber-dagger")
   testAnnotationProcessor("dev.joss:cucumber-dagger-processor")
   testAnnotationProcessor("com.google.dagger:dagger-compiler:<version>")
   ```

2. **Replace `@InjectorSource` with a root component:**
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
   public interface IntegrationTestConfig {

       @Component.Builder
       interface Builder {
           @BindsInstance Builder baseUrl(@Nullable String baseUrl);
           IntegrationTestConfig build();
       }
   }
   ```

3. **Move `@ScenarioScoped` class annotations to `@Provides` methods:**
   ```java
   // Before (Guice)
   @ScenarioScoped
   public class Basket {
       @Inject public Basket(ApiClient client) { ... }
   }

   // After (Dagger)
   @Provides
   @ScenarioScope
   static Basket provideBasket(ApiClient client) {
       return new Basket(client);
   }
   ```

4. **Convert Guice modules to Dagger modules:**
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
       @Binds @Singleton
       abstract ReceiptFormatter bindReceiptFormatter(PlainReceiptFormatter impl);
   }
   ```

5. **Replace field injection with `@Inject` constructors.** Guice supports `@Inject` on fields; Dagger requires constructor injection.
