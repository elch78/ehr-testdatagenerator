package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want to specify which datasets to generate as a list of mother invocations — each a
 * mother reference plus optional overrides — so that I assemble several distinct test records at once.
 * This is the declarative mirror of composing object mothers in test code:
 * {@code aPerson().name("Bob"); aPerson().name("Carol").age(41);}.
 */
class GenerateDatasetsTest {

    @Test
    void oneDatasetPerMotherInvocation() {
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

        Schema person = Schema.parse(schema);

        // and a base mother with default values
        person.define("person", """
                { "name": "Default", "age": 30 }
                """);

        // and a list of two invocations of that mother, each overriding some fields
        String wanted = """
                [
                  { "$mother": "person", "name": "Bob" },
                  { "$mother": "person", "name": "Carol", "age": 41 }
                ]
                """;

        // when the datasets are generated
        String datasets = person.datasets(wanted).generate();

        // then there is one dataset per invocation, each the mother's values with overrides applied
        assertThatJson(datasets).isEqualTo("""
                [
                  { "name": "Bob",   "age": 30 },
                  { "name": "Carol", "age": 41 }
                ]
                """);
    }
}
