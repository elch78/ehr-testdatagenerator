package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want a property described only by its {@code items} — without an explicit
 * {@code "type": "array"} — to still be generated as an array, so that schemas which omit the
 * declared type (as FHIR's do) are handled structurally, the same way an object is recognised by its
 * {@code properties} rather than its declared type. Each element is resolved, so a {@code $mother}
 * composed into an element is expanded, not rendered as a literal reference.
 */
class TypelessArraySchemaTest {

    @Test
    void arrayDescribedOnlyByItemsIsGenerated() {
        // a Team whose members array declares items but no "type": "array"
        String schema = """
                {
                  "type": "object",
                  "title": "Team",
                  "properties": {
                    "members": {
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string" },
                          "role": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;

        Schema team = Schema.parse(schema);

        // a reusable member mother that sets only the name
        team.define("lead", """
                { "name": "Alice" }
                """);

        // the members list composes that mother into one element and sets the other inline
        team.define("team", """
                { "members": [ { "$mother": "lead" }, { "name": "Bob", "role": "dev" } ] }
                """);

        // when the team is generated
        String data = team.mother("team").generate();

        // then the list is generated element by element: the composed mother is resolved (not a
        // literal $mother), and the inline element is rendered as set
        assertThatJson(data).isEqualTo("""
                {
                  "members": [
                    { "name": "Alice" },
                    { "name": "Bob", "role": "dev" }
                  ]
                }
                """);
    }
}
