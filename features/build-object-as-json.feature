Feature: Build JSON from a fluent builder

  As a developer using the testdatagenerator library
  I want to construct a domain object via a typed, fluent builder
  And produce its JSON representation in one call
  So that test data has a stable, readable JSON shape that I can shape per scenario

  Scenario: Build a Person with explicit values and serialize to JSON
    Given a Person built from the "aPerson" object mother
      And the Person's name is set to "Alice"
      And the Person's age is set to 30
    When the Person is serialized to JSON
    Then the JSON equals:
      """
      { "name": "Alice", "age": 30 }
      """