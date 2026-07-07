Feature: Binding styles

  # @Binds — interface resolved to implementation at compile time, no factory body needed.
  Scenario: @Binds wires an interface to its implementation
    Then formatting "hello" produces "HELLO"

  # Provider<T> — Dagger injects a lazy wrapper; the instance is fetched on get().
  Scenario: Provider<T> defers construction until get() is called
    Then formatting "world" produces "WORLD"

  # @BindsOptionalOf — Optional<T> is empty when no binding is present for T.
  Scenario: @BindsOptionalOf yields Optional.empty when no implementation is bound
    Then no optional plugin is bound

  # @IntoSet multibinding — contributions from all @Provides @IntoSet methods are collected.
  Scenario: @IntoSet contributes elements to a Set
    Then the tag set contains "alpha"
    And the tag set contains "beta"
    And the tag set has 2 entries
