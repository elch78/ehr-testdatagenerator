Feature: Generate a typed builder from a JSON schema

  As a developer using the testdatagenerator library
  I want to turn a JSON schema into a typed, fluent builder
  So that I get the builder + object-mother ergonomics for any schema-defined shape
  without hand-writing the builder myself

  Scenario: Object schema with scalar properties yields a working builder
    Given a JSON schema named "Person" with the properties:
      | property | type    |
      | name     | string  |
      | age      | integer |
    When a builder is generated from the schema
      And a Person is built with name "Alice" and age 30
    Then the built object serializes to JSON:
      """
      { "name": "Alice", "age": 30 }
      """
