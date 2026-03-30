package dev.joss.dagger.cucumber.internal;

/**
 * Shared static registry that lets {@link DaggerBackend} obtain the {@link DaggerObjectFactory}
 * instance that was created by Cucumber's service loader.
 *
 * <p>Cucumber instantiates {@link DaggerObjectFactory} (via the {@code ObjectFactory} SPI) and
 * {@link DaggerBackend} (via the {@code BackendProviderService} SPI) independently, with no
 * built-in way to pass one to the other. {@link DaggerObjectFactory} registers itself here during
 * construction so that {@link DaggerBackend} can retrieve it when {@code loadGlue()} is called.
 */
class ObjectFactoryHolder {

  private static DaggerObjectFactory instance;

  /**
   * Registers {@code factory} as the active {@link DaggerObjectFactory}. Called by the {@link
   * DaggerObjectFactory} constructor.
   */
  static void register(DaggerObjectFactory factory) {
    instance = factory;
  }

  /**
   * Returns the registered {@link DaggerObjectFactory}, or {@code null} if none has been registered
   * yet.
   */
  static DaggerObjectFactory get() {
    return instance;
  }
}
