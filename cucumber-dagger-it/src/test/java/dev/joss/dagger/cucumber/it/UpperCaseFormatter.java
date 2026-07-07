package dev.joss.dagger.cucumber.it;

import jakarta.inject.Inject;

/**
 * Upper-casing implementation of {@link Formatter}. Bound via {@code @Binds} in {@link AppModule};
 * Dagger constructs it directly from this {@code @Inject} constructor.
 */
public final class UpperCaseFormatter implements Formatter {

  @Inject
  public UpperCaseFormatter() {}

  @Override
  public String format(String input) {
    return input.toUpperCase(java.util.Locale.ROOT);
  }
}
