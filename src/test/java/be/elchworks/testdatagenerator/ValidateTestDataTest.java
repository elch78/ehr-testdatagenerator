package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import be.elchworks.testdatagenerator.declarative.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want to validate test data against a schema, so that when the schema changes I learn
 * which existing data no longer fits — the data half of migration.
 */
class ValidateTestDataTest {

    private static Schema personSchema() {
        return Schema.parse("""
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  },
                  "required": ["name"]
                }
                """);
    }

    @Test
    void dataWithAllKnownAndRequiredPropertiesIsValid() {
        Validation validation = personSchema().validateData("""
                { "name": "Alice", "age": 30 }
                """);

        assertThat(validation.isValid()).isTrue();
        assertThat(validation.problems()).isEmpty();
    }

    @Test
    void missingRequiredPropertyIsReported() {
        Validation validation = personSchema().validateData("""
                { "age": 30 }
                """);

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.problems()).anyMatch(problem -> problem.contains("name"));
    }

    @Test
    void unknownPropertyIsReported() {
        Validation validation = personSchema().validateData("""
                { "name": "Alice", "nickname": "Ali" }
                """);

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.problems()).anyMatch(problem -> problem.contains("nickname"));
    }
}
