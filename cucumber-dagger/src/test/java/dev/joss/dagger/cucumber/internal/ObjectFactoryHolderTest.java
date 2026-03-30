package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObjectFactoryHolderTest {

  @BeforeEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  @Test
  void register_storesFactory() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    ObjectFactoryHolder.register(factory);
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  @Test
  void register_null_clearsHolder() {
    new DaggerObjectFactory();
    ObjectFactoryHolder.register(null);
    assertThat(ObjectFactoryHolder.get()).isNull();
  }

  @Test
  void register_replacesExistingEntry() {
    DaggerObjectFactory first = new DaggerObjectFactory();
    DaggerObjectFactory second = new DaggerObjectFactory();
    // second auto-registers on construction, replacing first
    assertThat(ObjectFactoryHolder.get()).isSameAs(second).isNotSameAs(first);
  }

  @Test
  void constructingFactory_autoRegisters() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }
}
