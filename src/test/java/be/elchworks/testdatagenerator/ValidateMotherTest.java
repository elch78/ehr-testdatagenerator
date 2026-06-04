package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want to validate a mother against its schema, so that when the schema changes I learn
 * which mothers no longer fit — the foundation for migration.
 */
class ValidateMotherTest {

    private static Schema personSchema() {
        return Schema.parse("""
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  }
                }
                """);
    }

    @Test
    void motherSettingOnlyKnownPropertiesIsValid() {
        // given a mother that sets only a known property
        Schema person = personSchema();
        person.define("alice", """
                { "name": "Alice" }
                """);

        // when the mother is validated
        Validation validation = person.validate("alice");

        // then it is valid with no problems
        assertThat(validation.isValid()).isTrue();
        assertThat(validation.problems()).isEmpty();
    }

    @Test
    void motherSettingUnknownPropertyIsReported() {
        // given a mother that sets a property the schema does not define
        Schema person = personSchema();
        person.define("alice", """
                { "name": "Alice", "nickname": "Ali" }
                """);

        // when the mother is validated
        Validation validation = person.validate("alice");

        // then it is invalid and the unknown property is reported
        assertThat(validation.isValid()).isFalse();
        assertThat(validation.problems()).anyMatch(problem -> problem.contains("nickname"));
    }
}
