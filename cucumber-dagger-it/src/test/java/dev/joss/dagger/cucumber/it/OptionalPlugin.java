package dev.joss.dagger.cucumber.it;

/**
 * An optional extension point. Declared with {@code @BindsOptionalOf} in {@link AppModule} but
 * never bound to an implementation, so injection sites receive {@code Optional.empty()}.
 */
public interface OptionalPlugin {

  /** Returns the plugin's identifier. */
  String id();
}
