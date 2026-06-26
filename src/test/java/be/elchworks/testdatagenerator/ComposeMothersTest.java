package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want a mother's field to reference another mother, so that I compose mothers and reuse
 * a nested one — the declarative equivalent of {@code aPerson().address(aBerlinAddress())}. The
 * referenced mother is resolved into the field — its set values appear, not a literal copy of the
 * reference — the same {@code $mother} mechanism the {@link GenerateDatasetsTest datasets} use, one
 * level deeper. Like everywhere else, a field the referenced mother leaves unset is simply omitted.
 */
class ComposeMothersTest {

    @Test
    void fieldReferencingAnotherMotherIsResolved() {
        // given a Person schema whose address is a nested object with mandatory street and city
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
                      "required": ["street", "city"]
                    }
                  }
                }
                """;

        Schema person = Schema.parse(schema);

        // and a reusable address mother that sets only the city
        person.define("berlinAddress", """
                { "city": "Berlin" }
                """);

        // and a person mother whose address references that address mother
        person.define("person", """
                { "name": "Alice", "address": { "$mother": "berlinAddress" } }
                """);

        // when test data is generated
        String data = person.mother("person").generate();

        // then the reference resolves into the field's values (an object, not a literal copy of the
        // reference) and the field the referenced mother leaves unset is omitted
        assertThatJson(data).isEqualTo("""
                { "name": "Alice", "address": { "city": "Berlin" } }
                """);
    }
}
