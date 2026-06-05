package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want my schema to describe nested objects, so that a mother can set part of a nested
 * object while the generator fills the nested object's mandatory fields it leaves unset — the same
 * promise as {@link RandomizeMandatoryFieldsTest}, one level deeper.
 */
class NestedObjectSchemaTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mandatoryFieldsOfANestedObjectAreRandomized() throws Exception {
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
        JsonNode data = json.readTree(person.mother("person").generate());

        // then the values the mother set are kept, nested structure preserved
        assertThat(data.get("name").asText()).isEqualTo("Alice");
        assertThat(data.get("address").get("street").asText()).isEqualTo("Main St");

        // and the mandatory nested field the mother left unset is filled with a random value
        assertThat(data.get("address").get("city").asText()).isNotBlank();
    }
}
