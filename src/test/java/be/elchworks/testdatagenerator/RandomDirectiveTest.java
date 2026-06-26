package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want to mark a field with the {@code $random} directive so the generator fills it with a
 * type-correct random value drawn from the schema — an explicit request, by my choice, replacing the
 * auto-fill that {@link GenerateRendersOnlySetValuesTest} removed.
 *
 * <p>The value follows the schema: a {@code string} field gets a string, an {@code integer} field an
 * integer, a {@code format: date} field a date. A plain string random is prefixed with the field's
 * name ({@code street-7f3a9c}) so a stray value is traceable back to where it came from; the directive
 * is written as an object ({@code { "$random": {} }}) so it can later carry options such as an explicit
 * prefix.
 */
class RandomDirectiveTest {

    private final ObjectMapper json = new JsonMapper();

    private static final String SCHEMA = """
            {
              "type": "object",
              "title": "Product",
              "properties": {
                "name":       { "type": "string" },
                "street":     { "type": "string" },
                "quantity":   { "type": "integer" },
                "bestBefore": { "type": "string", "format": "date" }
              }
            }
            """;

    private static final String MOTHER = """
            {
              "name": "Widget",
              "street":     { "$random": {} },
              "quantity":   { "$random": {} },
              "bestBefore": { "$random": {} }
            }
            """;

    @Test
    void fillsEachFieldWithASchemaTypedRandomValue() throws Exception {
        // given a Product schema and a mother asking for random street, quantity and bestBefore
        Schema product = Schema.parse(SCHEMA);
        product.define("product", MOTHER);

        // when test data is generated twice
        JsonNode first = json.readTree(product.mother("product").generate());
        JsonNode second = json.readTree(product.mother("product").generate());

        // then the field the mother set keeps its value
        assertThat(first.get("name").asString()).isEqualTo("Widget");

        // and the string field is filled with a value prefixed by the field name, for traceability
        assertThat(first.get("street").asString()).matches("street-[0-9a-f]{6}");

        // and the integer field is filled with an integer, the date field with an ISO date
        assertThat(first.get("quantity").isInt()).isTrue();
        assertThat(first.get("bestBefore").asString()).matches("\\d{4}-\\d{2}-\\d{2}");

        // and the value is genuinely random: it differs between generations
        assertThat(first.get("street").asString())
                .isNotEqualTo(second.get("street").asString());
    }

    @Test
    void aMotherUsingRandomStillValidates() {
        // given the same schema and a mother that uses $random on an integer and a date field
        Schema product = Schema.parse(SCHEMA);
        product.define("product", MOTHER);

        // when the mother is validated, then $random is treated as a directive, not as data:
        // it raises no false type mismatch against the schema
        assertThat(product.validate("product").isValid()).isTrue();
    }
}
