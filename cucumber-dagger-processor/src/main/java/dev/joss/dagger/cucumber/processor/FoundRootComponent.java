package dev.joss.dagger.cucumber.processor;

import javax.lang.model.element.TypeElement;

/**
 * Output of {@link FindRootComponentStep}: the validated {@code @CucumberDaggerConfiguration}
 * root-component interface and its package name.
 *
 * @param rootComponent the root component {@link TypeElement}
 * @param rootPackage package of {@code rootComponent}
 * @param componentBuilder the inner {@code @Component.Builder} interface detected by {@link
 *     DetectComponentBuilderStep}, or {@code null} if the root component declares no such inner
 *     type
 */
record FoundRootComponent(
    TypeElement rootComponent, String rootPackage, TypeElement componentBuilder) {}
