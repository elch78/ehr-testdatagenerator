package be.elchworks.testdatagenerator;

import org.junit.jupiter.api.Test;

import static be.elchworks.testdatagenerator.TestFixtures.aPerson;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class BuildObjectAsJsonTest {

    @Test
    void buildPersonWithExplicitValuesAndSerializeToJson() {
        String json = aPerson()
                .name("Alice")
                .age(30)
                .toJson();

        assertThatJson(json).isEqualTo("""
                { "name": "Alice", "age": 30 }
                """);
    }
}
