Feature: Hooks receive injected @ScenarioScope dependencies

  # @Before and @After hooks are instantiated through the same Dagger graph as
  # step-definition classes. They must therefore receive the same @ScenarioScope
  # instances, so that mutations made in a hook are visible to subsequent steps.

  @hook-injection
  Scenario: @Before hook can mutate a scoped dependency visible to step definitions
    Then the scoped counter is 1
