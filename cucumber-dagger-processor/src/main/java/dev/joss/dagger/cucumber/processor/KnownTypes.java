package dev.joss.dagger.cucumber.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Centralizes all {@link TypeElement} lookups used throughout the processor pipeline. Fields are
 * {@code null} when the corresponding type is not on the compilation classpath, preserving the
 * existing null-guard pattern.
 */
final class KnownTypes {

  static final String API_PKG = "dev.joss.dagger.cucumber.api";
  static final String GENERATED_PKG = "dev.joss.dagger.cucumber.generated";

  /** {@code dev.joss.dagger.cucumber.api.CucumberDaggerConfiguration} */
  final TypeElement cucumberDaggerConfiguration;

  /** {@code dev.joss.dagger.cucumber.api.CucumberScoped} */
  final TypeElement cucumberScoped;

  /** {@code dagger.Component} */
  final TypeElement daggerComponent;

  /** {@code dagger.Provides} */
  final TypeElement daggerProvides;

  /** {@code jakarta.inject.Inject} */
  final TypeElement javaxInject;

  /**
   * {@code jakarta.inject.Scope} — meta-annotation for scope annotations such as {@code @Singleton}
   */
  final TypeElement javaxScope;

  /**
   * {@code jakarta.inject.Qualifier} — meta-annotation for qualifier annotations such as
   * {@code @Named}
   */
  final TypeElement javaxQualifier;

  KnownTypes(ProcessingEnvironment processingEnv) {
    Elements elements = processingEnv.getElementUtils();
    cucumberDaggerConfiguration = elements.getTypeElement(API_PKG + ".CucumberDaggerConfiguration");
    cucumberScoped = elements.getTypeElement(API_PKG + ".CucumberScoped");
    daggerComponent = elements.getTypeElement("dagger.Component");
    daggerProvides = elements.getTypeElement("dagger.Provides");
    javaxInject = elements.getTypeElement("jakarta.inject.Inject");
    javaxScope = elements.getTypeElement("jakarta.inject.Scope");
    javaxQualifier = elements.getTypeElement("jakarta.inject.Qualifier");
  }
}
