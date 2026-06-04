package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want to define a mother in YAML as well as JSON, so that the same test data
 * capabilities work from whichever format I prefer — proving formats are interchangeable over one
 * core model.
 */
class DefineMotherInYamlTest {

    @Test
    void motherDefinedInYamlGeneratesTestData() {
        // given a schema for a Person with a string name and an integer age
        Schema person = Schema.parse("""
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  }
                }
                """);

        // and a mother defined in YAML
        person.defineYaml("alice", """
                name: Alice
                age: 30
                """);

        // when test data is generated from the mother
        String testData = person.mother("alice").generate();

        // then the generated data matches the mother's values
        assertThatJson(testData).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
