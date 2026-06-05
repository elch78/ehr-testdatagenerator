package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want a mother to build on another mother, so that I reuse common defaults and only
 * state what differs — the declarative equivalent of {@code aFoodProduct()} building on
 * {@code aProduct()}.
 */
class MotherInheritanceTest {

    @Test
    void childMotherInheritsAndOverridesParentValues() {
        // given a schema for a Product with a string name and a string category
        String schema = """
                {
                  "type": "object",
                  "title": "Product",
                  "properties": {
                    "name":     { "type": "string" },
                    "category": { "type": "string" }
                  }
                }
                """;

        Schema product = Schema.parse(schema);

        // and a base mother setting name and category
        product.define("product", """
                { "name": "Widget", "category": "Generic" }
                """);

        // and a child mother that extends it and overrides the category
        product.define("foodProduct", """
                { "$extends": "product", "category": "Food" }
                """);

        // when test data is generated from the child mother
        String testData = product.mother("foodProduct").generate();

        // then the child inherits the parent's name and overrides the category
        assertThatJson(testData).isEqualTo("""
                { "name": "Widget", "category": "Food" }
                """);
    }
}
