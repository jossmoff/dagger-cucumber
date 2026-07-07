Feature: @ScenarioScope sharing across step-definition classes

  # When two different step-definition classes in the same scenario both request
  # the same @ScenarioScope type, Dagger must return the same instance to both.
  # Mutations made via one step-def must therefore be visible from the other.

  Scenario: Scoped object is shared across step-definition classes
    When I increment the scoped counter from the secondary steps
    Then the scoped counter is 1
