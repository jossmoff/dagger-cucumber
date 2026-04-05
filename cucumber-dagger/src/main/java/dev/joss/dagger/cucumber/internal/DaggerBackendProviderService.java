package dev.joss.dagger.cucumber.internal;

import io.cucumber.core.backend.Backend;
import io.cucumber.core.backend.BackendProviderService;
import io.cucumber.core.backend.Container;
import io.cucumber.core.backend.Lookup;
import java.util.function.Supplier;

/**
 * Cucumber {@link BackendProviderService} SPI implementation that creates a {@link DaggerBackend}.
 *
 * <p>Registered in {@code META-INF/services/io.cucumber.core.backend.BackendProviderService}.
 */
public final class DaggerBackendProviderService implements BackendProviderService {

  /** Creates a new {@code DaggerBackendProviderService}. */
  public DaggerBackendProviderService() {}

  @Override
  public Backend create(
      Lookup lookup, Container container, Supplier<ClassLoader> classLoaderSupplier) {
    return new DaggerBackend(lookup, container, classLoaderSupplier);
  }
}
