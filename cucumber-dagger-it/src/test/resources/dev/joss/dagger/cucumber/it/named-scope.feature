Feature: @Named-qualified @ScenarioScope bindings

  # @Named-qualified @ScenarioScope bindings must be resolvable as transitive dependencies
  # of step-definition classes. Each qualified binding is a distinct instance from the
  # unqualified binding of the same type.

  Scenario: Named primary counter starts at zero
    Then the named primary counter starts at zero

  Scenario: Named primary counter is independent from the regular counter
    Then the named primary counter is independent from the regular counter
