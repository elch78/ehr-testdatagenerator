package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class GenerateBuilderFromSchemaTest {

    @Test
    void objectSchemaWithScalarPropertiesYieldsWorkingBuilder() {
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

        GeneratedType person = Generator.from(schema).compile();

        Object built = person.builder()
                .set("name", "Alice")
                .set("age", 30)
                .build();

        assertThatJson(Json.toJson(built)).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
