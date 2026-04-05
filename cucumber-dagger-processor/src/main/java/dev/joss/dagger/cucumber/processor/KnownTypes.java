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

  /** {@code dev.joss.dagger.cucumber.api.ScenarioScoped} */
  final TypeElement scenarioScoped;

  /** {@code dagger.Component} */
  final TypeElement daggerComponent;

  /** {@code dagger.Provides} */
  final TypeElement daggerProvides;

  /** {@code jakarta.inject.Inject} */
  final TypeElement jakartaInject;

  /**
   * {@code jakarta.inject.Scope} — meta-annotation for scope annotations such as {@code @Singleton}
   */
  final TypeElement jakartaScope;

  /**
   * {@code jakarta.inject.Qualifier} — meta-annotation for qualifier annotations such as
   * {@code @Named}
   */
  final TypeElement jakartaQualifier;

  KnownTypes(ProcessingEnvironment processingEnv) {
    Elements elements = processingEnv.getElementUtils();
    cucumberDaggerConfiguration = elements.getTypeElement(API_PKG + ".CucumberDaggerConfiguration");
    scenarioScoped = elements.getTypeElement(API_PKG + ".ScenarioScoped");
    daggerComponent = elements.getTypeElement("dagger.Component");
    daggerProvides = elements.getTypeElement("dagger.Provides");
    jakartaInject = elements.getTypeElement("jakarta.inject.Inject");
    jakartaScope = elements.getTypeElement("jakarta.inject.Scope");
    jakartaQualifier = elements.getTypeElement("jakarta.inject.Qualifier");
  }
}
