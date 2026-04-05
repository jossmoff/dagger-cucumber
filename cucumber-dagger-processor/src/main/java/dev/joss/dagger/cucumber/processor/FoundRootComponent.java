package dev.joss.dagger.cucumber.processor;

import javax.lang.model.element.TypeElement;

/**
 * Output of {@link FindRootComponentStep}: the validated {@code @CucumberDaggerConfiguration}
 * root-component interface and its package name.
 */
record FoundRootComponent(TypeElement rootComponent, String rootPackage) {}
