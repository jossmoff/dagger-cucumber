package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.joss.dagger.cucumber.api.ComponentResolver;
import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.ScenarioScopedComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DaggerObjectFactoryTest {

  // ---------------------------------------------------------------------------
  // Test doubles - public so the resolver can call their methods
  // ---------------------------------------------------------------------------

  public static class StepDef {}

  public static class ScopedObject {}

  public static class RootService {}

  public interface TestScopedComponent extends ScenarioScopedComponent {
    StepDef stepDef();

    ScopedObject scopedObject();
  }

  public interface TestRootComponent extends CucumberDaggerComponent {
    RootService rootService();

    StepDef stepDef();
  }

  public static final StepDef ROOT_STEP_DEF = new StepDef();

  /** Simulates Dagger's @ScenarioScope caching: one instance per component object. */
  public static class TestScopedComponentImpl implements TestScopedComponent {
    private final StepDef stepDef = new StepDef();
    private final ScopedObject scopedObject = new ScopedObject();

    @Override
    public StepDef stepDef() {
      return stepDef;
    }

    @Override
    public ScopedObject scopedObject() {
      return scopedObject;
    }
  }

  /** Simulates Dagger's @Singleton caching: one instance for the whole root component. */
  public static class TestRootComponentImpl implements TestRootComponent {
    private final RootService rootService = new RootService();

    @Override
    @SuppressWarnings("rawtypes")
    public ScenarioScopedComponent.Builder scopedComponentBuilder() {
      return TestScopedComponentImpl::new;
    }

    @Override
    public RootService rootService() {
      return rootService;
    }

    @Override
    public StepDef stepDef() {
      return ROOT_STEP_DEF;
    }
  }

  /** Generated-style resolver: scoped wins over root when the same type is on both. */
  public static class TestComponentResolver implements ComponentResolver {
    @Override
    public ScenarioScopedComponent createScoped(CucumberDaggerComponent root) {
      return ((TestRootComponent) root).scopedComponentBuilder().build();
    }

    @Override
    public Object resolveScoped(Class<?> type, ScenarioScopedComponent scoped) {
      TestScopedComponentImpl comp = (TestScopedComponentImpl) scoped;
      if (type == StepDef.class) return comp.stepDef();
      if (type == ScopedObject.class) return comp.scopedObject();
      return null;
    }

    @Override
    public Object resolveRoot(Class<?> type, CucumberDaggerComponent root) {
      TestRootComponentImpl comp = (TestRootComponentImpl) root;
      if (type == RootService.class) return comp.rootService();
      if (type == StepDef.class) return comp.stepDef();
      return null;
    }
  }

  @BeforeEach
  @AfterEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  @Test
  void constructionRegistersInHolder() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  @Test
  void startIsNoOp() {
    new DaggerObjectFactory().start();
  }

  @Test
  void stopIsNoOp() {
    new DaggerObjectFactory().stop();
  }

  @Test
  void addClassAlwaysReturnsTrue() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(factory.addClass(String.class)).isTrue();
    assertThat(factory.addClass(Integer.class)).isTrue();
  }

  @Test
  void buildWorldThrowsWhenNotConfigured() {
    DaggerObjectFactory factory = new DaggerObjectFactory();

    assertThatThrownBy(factory::buildWorld)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cucumber-dagger-processor");
  }

  @Test
  void buildWorldCreatesFreshScopedComponentEachScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());

    factory.buildWorld();
    StepDef first = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    factory.buildWorld();
    StepDef second = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    assertThat(first).isNotSameAs(second);
  }

  @Test
  void getInstanceReturnsScopedInstance() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();

    StepDef instance = factory.getInstance(StepDef.class);

    assertThat(instance).isNotNull().isInstanceOf(StepDef.class);
    factory.disposeWorld();
  }

  @Test
  void getInstanceReturnsSameInstanceWithinScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();

    // Dagger's @ScenarioScope caching simulated by the test double: same object each call
    StepDef first = factory.getInstance(StepDef.class);
    StepDef second = factory.getInstance(StepDef.class);

    assertThat(first).isSameAs(second);
    factory.disposeWorld();
  }

  @Test
  void getInstancePrefersScopedOverRootWhenTypeIsOnBoth() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();

    StepDef instance = factory.getInstance(StepDef.class);

    // StepDef is on both root (returns ROOT_STEP_DEF) and scoped; scoped must win.
    assertThat(instance).isNotNull().isNotSameAs(ROOT_STEP_DEF);
    factory.disposeWorld();
  }

  @Test
  void getInstanceReturnsRootInstanceWhenTypeIsOnlyOnRootComponent() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();

    RootService instance = factory.getInstance(RootService.class);

    assertThat(instance).isNotNull().isInstanceOf(RootService.class);
    factory.disposeWorld();
  }

  @Test
  void getInstanceThrowsForUnknownType() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();

    assertThatThrownBy(() -> factory.getInstance(String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("String");
    factory.disposeWorld();
  }

  @Test
  void disposeWorldClearsCurrentScopedComponent() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());

    factory.buildWorld();
    StepDef firstScenario = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    factory.buildWorld();
    StepDef secondScenario = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    assertThat(firstScenario).isNotSameAs(secondScenario);
  }

  @Test
  void disposeWorldMakesScopedOnlyTypesUnresolvable() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), new TestComponentResolver());
    factory.buildWorld();
    factory.disposeWorld();

    // ScopedObject is only on the scoped component; after disposeWorld() it must not resolve.
    assertThatThrownBy(() -> factory.getInstance(ScopedObject.class))
        .isInstanceOf(IllegalStateException.class);
  }
}
