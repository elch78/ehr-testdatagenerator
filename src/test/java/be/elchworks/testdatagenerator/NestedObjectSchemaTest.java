package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want my schema to describe nested objects, so that a mother can set part of a nested
 * object and have exactly that rendered — the generator descends into the nested object but, like the
 * top level ({@link GenerateRendersOnlySetValuesTest}), renders only what the mother sets and omits
 * the rest.
 */
class NestedObjectSchemaTest {

    @Test
    void nestedObjectRendersOnlyWhatTheMotherSets() {
        // given a Person schema whose mandatory address is a nested object with a mandatory city
        String schema = """
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "address": {
                      "type": "object",
                      "properties": {
                        "street": { "type": "string" },
                        "city":   { "type": "string" }
                      },
                      "required": ["city"]
                    }
                  },
                  "required": ["address"]
                }
                """;

        Schema person = Schema.parse(schema);

        // and a mother that sets the name and only the street of the address
        person.define("person", """
                { "name": "Alice", "address": { "street": "Main St" } }
                """);

        // when test data is generated
        String data = person.mother("person").generate();

        // then the set values are kept and the unset nested mandatory city is omitted, not filled
        assertThatJson(data).isEqualTo("""
                { "name": "Alice", "address": { "street": "Main St" } }
                """);
    }
}
