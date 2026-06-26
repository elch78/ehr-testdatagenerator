package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want my schema to describe array properties — both arrays of scalars and arrays of
 * objects — so that I can generate repeating data, including by composing a mother into each element
 * ({@code [ { "$mother": ... } ]}), and have every element validated. This is the last structural gap
 * before a realistic FHIR example.
 */
class ArraySchemaTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "title": "Team",
              "properties": {
                "name": { "type": "string" },
                "members": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name":    { "type": "string" },
                      "aliases": { "type": "array", "items": { "type": "string" } }
                    },
                    "required": ["name"]
                  }
                }
              },
              "required": ["name", "members"]
            }
            """;

    @Test
    void arrayOfComposedMothersIsGenerated() {
        Schema team = Schema.parse(SCHEMA);

        // a reusable member mother, including a scalar array of its own
        team.define("member", """
                { "name": "Alice", "aliases": ["Al", "Ali"] }
                """);

        // the team's members are a list: one element composed from the member mother, one inline
        team.define("team", """
                { "name": "Avengers", "members": [ { "$mother": "member" }, { "name": "Bob" } ] }
                """);

        String data = team.mother("team").generate();

        // each element is resolved; the scalar array passes through; Bob's unset aliases is omitted
        assertThatJson(data).isEqualTo("""
                {
                  "name": "Avengers",
                  "members": [
                    { "name": "Alice", "aliases": ["Al", "Ali"] },
                    { "name": "Bob" }
                  ]
                }
                """);
    }

    @Test
    void unknownPropertyInsideAnArrayElementIsRejected() {
        Schema team = Schema.parse(SCHEMA);

        // a member carries a property the schema does not define
        String data = """
                { "name": "Avengers", "members": [ { "name": "Bob", "rank": "captain" } ] }
                """;

        // validation descends into array elements, so the unknown property is reported
        assertThat(team.validateData(data).isValid()).isFalse();
    }
}
