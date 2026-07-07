Feature: @ScenarioScope isolation

  # A @ScenarioScope object is a fresh instance for every scenario.
  # Mutations made in one scenario must not carry over to the next.

  Scenario: Scoped counter starts at zero
    Then the scoped counter is 0

  Scenario: Incrementing in one scenario does not affect the next
    When I increment the scoped counter
    Then the scoped counter is 1

  Scenario: Scoped counter is reset for this scenario
    Then the scoped counter is 0
