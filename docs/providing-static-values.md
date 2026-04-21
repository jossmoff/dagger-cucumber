# Providing static values and the Scenario object

## Static configuration via @BindsInstance

Use `@Component.Builder` with `@Nullable @BindsInstance` to inject values known before the test run — base URLs, environment names, credentials read from system properties. Annotating a parameter `@Nullable` makes the setter optional so `builder().build()` succeeds without calling it explicitly (the runtime calls the builder with no setters).

```java
@CucumberDaggerConfiguration
@Singleton
@Component(modules = {PriceListModule.class, ScenarioModule.class})
public interface IntegrationTestConfig {

    @Component.Builder
    interface Builder {
        @BindsInstance Builder baseUrl(@Nullable String baseUrl);
        @BindsInstance Builder environment(@Nullable String environment);
        IntegrationTestConfig build();
    }
}
```

Inject the bound value anywhere in the graph:

```java
@Module
public abstract class AppModule {

    @Provides
    @Singleton
    static ApiClient provideApiClient(@Nullable String baseUrl, @Nullable String environment) {
        String url = baseUrl != null ? baseUrl : System.getProperty("base.url", "http://localhost:8080");
        return new ApiClient(url, environment);
    }
}
```

> **No-arg builder contract:** every `@BindsInstance` parameter must be `@Nullable`. The runtime calls `builder().build()` without setting any values — non-nullable parameters cause Dagger to throw at runtime.

## The Scenario object

`buildWorld()` is called before `@Before` hooks, so `io.cucumber.java.Scenario` is not available when the subcomponent is created. Use a mutable holder populated by a `@Before` hook.

### Step 1 — create the holder

```java
@Singleton
public final class ScenarioHolder {

    private Scenario scenario;

    @Inject public ScenarioHolder() {}

    public void set(Scenario scenario) { this.scenario = scenario; }

    public Scenario get() {
        if (scenario == null) throw new IllegalStateException("ScenarioHolder not populated");
        return scenario;
    }
}
```

### Step 2 — populate in a @Before hook

```java
public final class ScenarioHooks {

    private final ScenarioHolder holder;

    @Inject public ScenarioHooks(ScenarioHolder holder) { this.holder = holder; }

    @Before
    public void capture(Scenario scenario) { holder.set(scenario); }
}
```

### Thread safety

For parallel execution replace the field with a `ThreadLocal`:

```java
private final ThreadLocal<Scenario> holder = new ThreadLocal<>();

public void set(Scenario s) { holder.set(s); }

public Scenario get() {
    Scenario scenario = holder.get();
    if (scenario == null) throw new IllegalStateException("ScenarioHolder not populated");
    return scenario;
}

@After
public void clear() { holder.remove(); }
```
