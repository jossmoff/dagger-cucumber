package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObjectFactoryHolderTest {

  @Mock DaggerObjectFactory factory;

  @AfterEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  @Test
  void registerStoresFactory() {
    ObjectFactoryHolder.register(factory);
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  @Test
  void registerReplacesExistingEntry() {
    ObjectFactoryHolder.register(mock(DaggerObjectFactory.class));
    ObjectFactoryHolder.register(factory);
    assertThat(ObjectFactoryHolder.get()).isSameAs(factory);
  }

  @Test
  void constructingFactoryAutoRegisters() {
    DaggerObjectFactory newFactory = new DaggerObjectFactory();
    assertThat(ObjectFactoryHolder.get()).isSameAs(newFactory);
  }
}
