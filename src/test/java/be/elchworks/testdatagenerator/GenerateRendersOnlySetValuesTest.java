package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want generation to render exactly what my mother sets — nothing more — so that the
 * mother, not the generator, owns completeness. A field the mother leaves unset is omitted from the
 * generated data, even when the schema marks it required, and generation never fails for a missing
 * mandatory field. (Filling mandatory fields is the user's job: a base mother + {@code $extends}, or
 * the explicit {@code $random} directive.)
 */
class GenerateRendersOnlySetValuesTest {

    @Test
    void unsetFieldsAreOmittedEvenWhenMandatory() {
        // given a Product schema with mandatory serialNumber and quantity
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

        // when test data is generated
        String data = product.mother("product").generate();

        // then only the field the mother set is rendered; the unset mandatory fields are omitted
        assertThatJson(data).isEqualTo("""
                { "name": "Widget" }
                """);
    }
}
