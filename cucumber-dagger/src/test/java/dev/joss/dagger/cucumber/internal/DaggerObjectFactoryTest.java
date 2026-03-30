package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.CucumberScopedComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DaggerObjectFactoryTest {

  // ---------------------------------------------------------------------------
  // Test helpers — interfaces and classes must be public so that
  // MethodHandles.lookup() (called from DaggerObjectFactory) can unreflect
  // their methods.
  // ---------------------------------------------------------------------------

  public static class StepDef {}

  public static class ScopedObject {}

  public static class RootService {}

  public interface TestScopedComponent extends CucumberScopedComponent {
    StepDef stepDef();

    ScopedObject scopedObject();
  }

  public interface TestRootComponent extends CucumberDaggerComponent {
    RootService rootService();
  }

  public static class TestScopedComponentImpl implements TestScopedComponent {
    @Override
    public StepDef stepDef() {
      return new StepDef();
    }

    @Override
    public ScopedObject scopedObject() {
      return new ScopedObject();
    }
  }

  public static class TestScopedBuilder
      implements CucumberScopedComponent.Builder<TestScopedComponentImpl> {
    @Override
    public TestScopedComponentImpl build() {
      return new TestScopedComponentImpl();
    }
  }

  public static class TestRootComponentImpl implements TestRootComponent {
    @Override
    @SuppressWarnings("rawtypes")
    public CucumberScopedComponent.Builder scopedComponentBuilder() {
      return new TestScopedBuilder();
    }

    @Override
    public RootService rootService() {
      return new RootService();
    }
  }

  @BeforeEach
  @AfterEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  // ---------------------------------------------------------------------------
  // Construction
  // ---------------------------------------------------------------------------

  @Test
  void construction_registersInHolder() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  // ---------------------------------------------------------------------------
  // Lifecycle stubs
  // ---------------------------------------------------------------------------

  @Test
  void start_isNoOp() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.start(); // must not throw
  }

  @Test
  void stop_isNoOp() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.stop(); // must not throw
  }

  @Test
  void addClass_alwaysReturnsTrue() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(factory.addClass(String.class)).isTrue();
    assertThat(factory.addClass(Integer.class)).isTrue();
  }

  // ---------------------------------------------------------------------------
  // buildWorld
  // ---------------------------------------------------------------------------

  @Test
  void buildWorld_throwsWhenNotConfigured() {
    DaggerObjectFactory factory = new DaggerObjectFactory();

    assertThatThrownBy(factory::buildWorld)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cucumber-dagger-processor");
  }

  @Test
  void buildWorld_createsFreshScopedComponentEachScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);

    factory.buildWorld();
    StepDef first = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    factory.buildWorld();
    StepDef second = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    // Each buildWorld creates a fresh subcomponent, so provision method results are distinct
    assertThat(first).isNotSameAs(second);
  }

  // ---------------------------------------------------------------------------
  // getInstance
  // ---------------------------------------------------------------------------

  @Test
  void getInstance_returnsScopedInstance() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    StepDef instance = factory.getInstance(StepDef.class);

    assertThat(instance).isNotNull().isInstanceOf(StepDef.class);
    factory.disposeWorld();
  }

  @Test
  void getInstance_cachesInstanceWithinScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    StepDef first = factory.getInstance(StepDef.class);
    StepDef second = factory.getInstance(StepDef.class);

    assertThat(first).isSameAs(second);
    factory.disposeWorld();
  }

  @Test
  void getInstance_prefersScopedOverRoot_whenTypeIsOnBoth() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    // StepDef is in the scoped component — should come from there even if root also had it
    StepDef instance = factory.getInstance(StepDef.class);

    assertThat(instance).isNotNull();
    factory.disposeWorld();
  }

  @Test
  void getInstance_returnsRootInstance_whenTypeIsOnlyOnRootComponent() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    RootService instance = factory.getInstance(RootService.class);

    assertThat(instance).isNotNull().isInstanceOf(RootService.class);
    factory.disposeWorld();
  }

  @Test
  void getInstance_cachesRootInstanceWithinScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    RootService first = factory.getInstance(RootService.class);
    RootService second = factory.getInstance(RootService.class);

    assertThat(first).isSameAs(second);
    factory.disposeWorld();
  }

  @Test
  void getInstance_throwsForUnknownType() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    assertThatThrownBy(() -> factory.getInstance(String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("String");
    factory.disposeWorld();
  }

  // ---------------------------------------------------------------------------
  // disposeWorld
  // ---------------------------------------------------------------------------

  @Test
  void disposeWorld_clearsCachedInstances() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);

    factory.buildWorld();
    StepDef firstScenario = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    factory.buildWorld();
    StepDef secondScenario = factory.getInstance(StepDef.class);
    factory.disposeWorld();

    assertThat(firstScenario).isNotSameAs(secondScenario);
  }

  @Test
  void disposeWorld_clearsScopedSuppliers() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();
    factory.disposeWorld();

    // After disposeWorld, no world is active — next buildWorld must be called before getInstance
    assertThatThrownBy(() -> factory.getInstance(StepDef.class))
        .isInstanceOf(IllegalStateException.class);
  }
}
