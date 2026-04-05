package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.joss.dagger.cucumber.api.CucumberDaggerComponent;
import dev.joss.dagger.cucumber.api.ScenarioScopedComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DaggerObjectFactoryTest {

  // Test helpers — interfaces and classes must be public so that
  // MethodHandles.lookup() (called from DaggerObjectFactory) can unreflect their methods.

  public static class StepDef {}

  public static class ScopedObject {}

  public static class RootService {}

  public interface TestScopedComponent extends ScenarioScopedComponent {
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
      implements ScenarioScopedComponent.Builder<TestScopedComponentImpl> {
    @Override
    public TestScopedComponentImpl build() {
      return new TestScopedComponentImpl();
    }
  }

  public static class TestRootComponentImpl implements TestRootComponent {
    @Override
    @SuppressWarnings("rawtypes")
    public ScenarioScopedComponent.Builder scopedComponentBuilder() {
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

  @Test
  void constructionRegistersInHolder() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  @Test
  void startIsNoOp() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.start();
  }

  @Test
  void stopIsNoOp() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.stop();
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
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);

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
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    StepDef instance = factory.getInstance(StepDef.class);

    assertThat(instance).isNotNull().isInstanceOf(StepDef.class);
    factory.disposeWorld();
  }

  @Test
  void getInstanceCachesInstanceWithinScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    StepDef first = factory.getInstance(StepDef.class);
    StepDef second = factory.getInstance(StepDef.class);

    assertThat(first).isSameAs(second);
    factory.disposeWorld();
  }

  @Test
  void getInstancePrefersScopedOverRootWhenTypeIsOnBoth() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    StepDef instance = factory.getInstance(StepDef.class);

    assertThat(instance).isNotNull();
    factory.disposeWorld();
  }

  @Test
  void getInstanceReturnsRootInstanceWhenTypeIsOnlyOnRootComponent() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    RootService instance = factory.getInstance(RootService.class);

    assertThat(instance).isNotNull().isInstanceOf(RootService.class);
    factory.disposeWorld();
  }

  @Test
  void getInstanceCachesRootInstanceWithinScenario() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    RootService first = factory.getInstance(RootService.class);
    RootService second = factory.getInstance(RootService.class);

    assertThat(first).isSameAs(second);
    factory.disposeWorld();
  }

  @Test
  void getInstanceThrowsForUnknownType() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();

    assertThatThrownBy(() -> factory.getInstance(String.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("String");
    factory.disposeWorld();
  }

  @Test
  void disposeWorldClearsCachedInstances() {
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
  void disposeWorldClearsScopedSuppliers() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    factory.configure(new TestRootComponentImpl(), TestScopedComponent.class);
    factory.buildWorld();
    factory.disposeWorld();

    assertThatThrownBy(() -> factory.getInstance(StepDef.class))
        .isInstanceOf(IllegalStateException.class);
  }
}
