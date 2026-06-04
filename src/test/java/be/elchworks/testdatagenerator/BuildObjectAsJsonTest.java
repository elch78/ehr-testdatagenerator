package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static be.elchworks.testdatagenerator.TestFixtures.aPerson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * Build a domain object via a typed, fluent builder and produce its JSON in one call,
 * so that test data has a stable, readable JSON shape that can be shaped per scenario.
 */
class BuildObjectAsJsonTest {

    @Test
    void buildPersonWithExplicitValuesAndSerializeToJson() {
        // when a Person is built from the aPerson mother with explicit values and serialized
        String json = aPerson()
                .name("Alice")
                .age(30)
                .toJson();

        // then the JSON reflects exactly those values
        assertThatJson(json).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
