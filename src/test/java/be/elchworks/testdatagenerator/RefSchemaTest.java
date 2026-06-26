package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want my schema to factor a repeated type into {@code definitions} and reference it with
 * {@code $ref} — both as a direct property and as an array's items — so that a schema shaped like the
 * real FHIR schema generates and validates. The generator must resolve the reference to find the
 * type's properties; today it only reads inline {@code properties}.
 */
class RefSchemaTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "title": "Team",
              "properties": {
                "lead":    { "$ref": "#/definitions/Member" },
                "members": { "type": "array", "items": { "$ref": "#/definitions/Member" } }
              },
              "definitions": {
                "Member": {
                  "type": "object",
                  "properties": { "name": { "type": "string" } },
                  "required": ["name"]
                }
              }
            }
            """;

    @Test
    void referencedTypeIsResolvedWhenGenerating() {
        Schema team = Schema.parse(SCHEMA);
        team.define("team", """
                { "lead": { "name": "Alice" }, "members": [ { "name": "Bob" }, { "name": "Carol" } ] }
                """);

        // the generator follows $ref to find Member's properties, for the direct field and each element
        assertThatJson(team.mother("team").generate()).isEqualTo("""
                {
                  "lead":    { "name": "Alice" },
                  "members": [ { "name": "Bob" }, { "name": "Carol" } ]
                }
                """);
    }

    @Test
    void unknownPropertyInsideAReferencedTypeIsRejected() {
        Schema team = Schema.parse(SCHEMA);

        // a value carries a property the referenced Member type does not define
        String data = """
                { "lead": { "name": "Alice", "title": "captain" } }
                """;

        // the additionalProperties:false rule reaches into the referenced definition
        assertThat(team.validateData(data).isValid()).isFalse();
    }
}
