package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Turn a JSON schema into a typed, fluent builder, so that the builder + object-mother
 * ergonomics are available for any schema-defined shape without hand-writing the builder.
 */
class GenerateBuilderFromSchemaTest {

    @Test
    void objectSchemaWithScalarPropertiesYieldsWorkingBuilder() {
        // given a schema for a Person with a string name and an integer age
        String schema = """
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  }
                }
                """;

        // when a builder is generated from the schema and a Person is built
        GeneratedType person = Generator.from(schema).compile();
        Object built = person.builder()
                .set("name", "Alice")
                .set("age", 30)
                .build();

        // then the built object serializes to the expected JSON
        assertThatJson(Json.toJson(built)).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
