package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want to define a mother declaratively against a JSON schema and generate test data
 * from it, so that I can assemble test data without writing Java.
 */
class GenerateTestDataFromMotherTest {

    @Test
    void declarativeMotherGeneratesTestData() {
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

        // and a declarative mother that sets name and age
        String alice = """
                { "name": "Alice", "age": 30 }
                """;

        // when test data is generated from the mother
        Schema person = Schema.parse(schema);
        person.define("alice", alice);
        String testData = person.mother("alice").generate();

        // then the generated data matches the mother's values
        assertThatJson(testData).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
