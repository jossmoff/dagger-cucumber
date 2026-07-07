package dev.joss.dagger.cucumber.it;

import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import jakarta.inject.Singleton;

/**
 * Root module covering singleton and interface-binding styles:
 *
 * <ul>
 *   <li>{@code @Provides @Singleton} — explicit singleton factory ({@link AppConfig}).
 *   <li>{@code @Binds @Singleton} — zero-overhead interface-to-impl binding ({@link Formatter}).
 *   <li>{@code @BindsOptionalOf} — optional extension point ({@link OptionalPlugin}).
 *   <li>{@code @Provides @IntoSet} — multibinding contributions to {@code Set<String>}.
 * </ul>
 */
@Module
public abstract class AppModule {

  @Provides
  @Singleton
  static AppConfig provideAppConfig() {
    return new AppConfig();
  }

  @Binds
  @Singleton
  abstract Formatter bindFormatter(UpperCaseFormatter impl);

  @BindsOptionalOf
  abstract OptionalPlugin optionalPlugin();

  @Provides
  @IntoSet
  @Singleton
  static String provideTagAlpha() {
    return "alpha";
  }

  @Provides
  @IntoSet
  @Singleton
  static String provideTagBeta() {
    return "beta";
  }
}
