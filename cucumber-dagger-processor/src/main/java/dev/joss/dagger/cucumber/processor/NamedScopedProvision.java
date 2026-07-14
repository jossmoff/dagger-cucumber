package dev.joss.dagger.cucumber.processor;

import com.palantir.javapoet.TypeName;

/**
 * Holds the information needed to generate a named (qualified) scoped provision method on {@code
 * GeneratedScopedComponent}.
 *
 * @param returnType The return type of the provision method.
 * @param methodName The generated method name derived from the qualifier value and the type name
 *     (e.g., {@code primaryBasket} for {@code @Named("primary") Basket}).
 * @param namedValue The string value of the {@code @Named} qualifier (e.g., {@code "primary"}).
 */
record NamedScopedProvision(TypeName returnType, String methodName, String namedValue) {}
