# Configuration reference

## @CucumberDaggerConfiguration

Apply to exactly one `@Component` interface in test sources.

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {AppModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {

    @Component.Builder
    interface Builder {
        @BindsInstance Builder baseUrl(@Nullable String baseUrl);
        @BindsInstance Builder apiKey(@Nullable String apiKey);
        IntegrationTestConfig build();
    }
}
```

| Rule | Detail |
|---|---|
| Interfaces only | Placing on a class → compile error |
| Exactly one | Second annotated interface → compile error |
| `@Singleton` recommended | Applies singleton scope to root bindings |

## @ScenarioScope

Apply to `@Provides` methods in `@Module` classes listed on your root component.

```java
@Provides
@ScenarioScope
static Basket provideBasket(PriceList priceList) {
    return new Basket(priceList);
}
```

| Rule | Detail |
|---|---|
| Method-level only | Class-level `@ScenarioScope` has no effect |
| No qualifiers | `@ScenarioScope` + `@Named` → compile error |
| Lifecycle | New instance per scenario; same instance at all injection sites within a scenario |

## @Component.Builder and @BindsInstance

Declare a `@Component.Builder` inner interface on your root component to inject static values at construction time — base URLs, credentials, environment names read from system properties or env vars.

```java
@Component.Builder
interface Builder {
    @BindsInstance Builder baseUrl(@Nullable String baseUrl);
    @BindsInstance Builder apiKey(@Nullable String apiKey);
    IntegrationTestConfig build();
}
```

> **No-arg builder contract:** every `@BindsInstance` parameter must be `@Nullable`. The runtime calls `builder().build()` with no setters — non-nullable parameters cause Dagger to throw.

Inject bound values anywhere in the graph:

```java
@Provides
@Singleton
static ApiClient provideApiClient(@Nullable String baseUrl, @Nullable String apiKey) {
    String url = baseUrl != null ? baseUrl : System.getProperty("base.url", "http://localhost:8080");
    String key = apiKey != null ? apiKey : System.getenv("API_KEY");
    return new ApiClient(url, key);
}
```

## Module registration

List all modules — root and scenario-scoped — in `@Component(modules = {...})`. The processor separates them automatically.

```java
@Component(modules = {AppModule.class, ScenarioModule.class})
```

Modules with `@Provides @ScenarioScope` methods are moved to the generated subcomponent; all others remain on the root component.

## Root provision methods

Declare zero-argument abstract methods on the root component to expose root-scoped bindings for resolution:

```java
PriceList priceList();
```

Step definition classes with `@Inject` constructors are discovered automatically — no provision method needed.

## Step definition discovery

| Condition | Required |
|---|---|
| `@Inject`-annotated constructor | Yes |
| Package = root component package or a sub-package | Yes |

Example: root component in `com.example.tests` → discovers `com.example.tests`, `com.example.tests.checkout`, etc. Not `com.example.other`.

## Supported features

| Feature | Scope | Example |
|---|---|---|
| `@Provides` | Root or scenario | `@Provides @Singleton static PriceList provide(...)` |
| `@Binds` | Root or scenario | `@Binds @Singleton abstract Formatter bind(PlainFormatter impl)` |
| `@BindsOptionalOf` | Root | `@BindsOptionalOf abstract VoucherService optional()` |
| `@IntoSet` | Root | `@Provides @IntoSet @Singleton static String provideCard()` |
| `Provider<T>` | Root or scenario | `Provider<ReceiptFormatter>` in constructor |
| `@Singleton` | Root | `@Provides @Singleton static PriceList provide(...)` |
| `@ScenarioScope` | Scenario | `@Provides @ScenarioScope static Basket provide(...)` |
| `@Component.Builder` | Root | Inner `Builder` with `@Nullable @BindsInstance` params |

Qualified `@ScenarioScope` bindings (`@ScenarioScope` + `@Named`) are not supported.
