package dev.joss.dagger.cucumber.it;

/**
 * Simple string transformer. Bound to {@link UpperCaseFormatter} via {@code @Binds} in {@link
 * AppModule}; demonstrates interface-to-implementation binding without a {@code @Provides} factory
 * method.
 */
public interface Formatter {

  /** Returns a transformed version of {@code input}. */
  String format(String input);
}
