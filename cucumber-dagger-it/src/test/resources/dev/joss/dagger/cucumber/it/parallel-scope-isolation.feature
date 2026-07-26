Feature: @ScenarioScope isolation under parallel execution

  # Each scenario runs in its own thread with its own ThreadLocal-backed ScenarioScopedComponent.
  # Mutations in one scenario must not bleed into any other scenario running concurrently.
  # If scope isolation breaks under parallelism, the counter assertions below will fail
  # non-deterministically, revealing cross-thread contamination.

  Scenario: Parallel scenario A - counter stays isolated at 1 increment
    When I increment the scoped counter
    Then the scoped counter is 1

  Scenario: Parallel scenario B - counter stays isolated at 2 increments
    When I increment the scoped counter
    And I increment the scoped counter
    Then the scoped counter is 2

  Scenario: Parallel scenario C - counter stays isolated at 3 increments
    When I increment the scoped counter
    And I increment the scoped counter
    And I increment the scoped counter
    Then the scoped counter is 3

  Scenario: Parallel scenario D - counter stays isolated at 4 increments
    When I increment the scoped counter
    And I increment the scoped counter
    And I increment the scoped counter
    And I increment the scoped counter
    Then the scoped counter is 4

  Scenario: Parallel scenario E - fresh counter starts at zero with no increments
    Then the scoped counter is 0

  Scenario: Parallel scenario F - singleton is shared across all parallel scenarios
    Then the singleton app config has id 1
