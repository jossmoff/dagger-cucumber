package dev.joss.dagger.cucumber.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.Container;
import io.cucumber.core.backend.Lookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DaggerBackendProviderServiceTest {

  @AfterEach
  void resetHolder() {
    ObjectFactoryHolder.register(null);
  }

  @Test
  void createReturnsDaggerBackendInstance() {
    DaggerBackendProviderService service = new DaggerBackendProviderService();

    Backend backend =
        service.create(
            mock(Lookup.class),
            mock(Container.class),
            Thread.currentThread()::getContextClassLoader);

    assertThat(backend).isInstanceOf(DaggerBackend.class);
  }

  @Test
  void createReturnsNewInstanceOnEachCall() {
    DaggerBackendProviderService service = new DaggerBackendProviderService();

    Backend first =
        service.create(
            mock(Lookup.class),
            mock(Container.class),
            Thread.currentThread()::getContextClassLoader);
    Backend second =
        service.create(
            mock(Lookup.class),
            mock(Container.class),
            Thread.currentThread()::getContextClassLoader);

    assertThat(first).isNotSameAs(second);
  }
}
