Feature: @Singleton sharing

  # A @Singleton binding is created once and reused for the lifetime of the component.
  # The same instance must be visible at every injection site, including inside scoped dependencies.

  Scenario: The singleton has a stable id of 1
    Then the singleton app config has id 1

  Scenario: The singleton is shared between a direct injection and a scoped dependency
    Then the singleton is shared between the root scope and scoped dependencies

  Scenario: The singleton id is still 1 in a later scenario
    Then the singleton app config has id 1
