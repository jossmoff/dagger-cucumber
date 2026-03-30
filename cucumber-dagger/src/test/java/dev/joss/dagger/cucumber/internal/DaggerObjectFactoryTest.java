package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DaggerObjectFactoryTest {

  @Test
  void correctlyInstantiatesDaggerObjectFactory() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(factory).isNotNull();
  }

  @Test
  void addClassAlwaysReturnsTrue() {
    DaggerObjectFactory factory = new DaggerObjectFactory();
    assertThat(factory.addClass(String.class)).isTrue();
  }
}
