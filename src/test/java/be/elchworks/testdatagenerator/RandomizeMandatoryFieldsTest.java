package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want mandatory fields I don't set to be filled with random values, so that my mothers
 * only state what matters to the test while the generated data stays complete.
 */
class RandomizeMandatoryFieldsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void unsetMandatoryFieldsAreFilledWithRandomValues() throws Exception {
        // given a Product schema with a name and the mandatory fields serialNumber and quantity
        String schema = """
                {
                  "type": "object",
                  "title": "Product",
                  "properties": {
                    "name":         { "type": "string" },
                    "serialNumber": { "type": "string" },
                    "quantity":     { "type": "integer" }
                  },
                  "required": ["serialNumber", "quantity"]
                }
                """;

        Schema product = Schema.parse(schema);

        // and a mother that sets only the name
        product.define("product", """
                { "name": "Widget" }
                """);

        // when test data is generated twice
        JsonNode first = json.readTree(product.mother("product").generate());
        JsonNode second = json.readTree(product.mother("product").generate());

        // then the set field keeps its value
        assertThat(first.get("name").asText()).isEqualTo("Widget");

        // and the mandatory fields are filled with type-correct values though unset
        assertThat(first.get("serialNumber").asText()).isNotBlank();
        assertThat(first.get("quantity").isInt()).isTrue();

        // and the random values differ between generations
        assertThat(first.get("serialNumber").asText())
                .isNotEqualTo(second.get("serialNumber").asText());
    }
}
