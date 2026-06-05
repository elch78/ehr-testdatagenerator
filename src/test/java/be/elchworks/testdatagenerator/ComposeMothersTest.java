package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want a mother's field to reference another mother, so that I compose mothers and reuse
 * a nested one — the declarative equivalent of {@code aPerson().address(aBerlinAddress())}. The
 * referenced mother is fully resolved: its own unset mandatory fields are randomized, the same
 * {@code $mother} mechanism the {@link GenerateDatasetsTest datasets} use, one level deeper.
 */
class ComposeMothersTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void fieldReferencingAnotherMotherIsResolved() throws Exception {
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
        JsonNode data = json.readTree(person.mother("person").generate());

        // then the referenced mother is resolved into the field, its set value kept
        assertThat(data.get("name").asText()).isEqualTo("Alice");
        assertThat(data.get("address").get("city").asText()).isEqualTo("Berlin");

        // and its mandatory field left unset is randomized, proving full resolution not a literal copy
        assertThat(data.get("address").get("street").asText()).isNotBlank();
    }
}
