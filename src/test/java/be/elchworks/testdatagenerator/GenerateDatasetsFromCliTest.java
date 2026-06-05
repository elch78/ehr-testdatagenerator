package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.cli.Cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want to run a command line tool that takes my schema, my mothers and the datasets I
 * want, and prints the generated test data, so that I assemble test data without writing Java — the
 * thin delivery shell over the declarative core.
 */
class GenerateDatasetsFromCliTest {

    @Test
    void generatesDatasetsFromInputFiles(@TempDir Path dir) throws Exception {
        // given a JSON schema for a Person
        Path schema = Files.writeString(dir.resolve("schema.json"), """
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  }
                }
                """);

        // and a file of named mothers
        Path mothers = Files.writeString(dir.resolve("mothers.yaml"), """
                person:
                  name: Default
                  age: 30
                """);

        // and a file describing which datasets to generate
        Path datasets = Files.writeString(dir.resolve("datasets.yaml"), """
                - { $mother: person, name: Bob }
                - { $mother: person, name: Carol, age: 41 }
                """);

        // when the CLI is run with the three inputs
        String output = Cli.run(
                "--schema", schema.toString(),
                "--mothers", mothers.toString(),
                "--datasets", datasets.toString());

        // then it prints the generated datasets as JSON, one per invocation
        assertThatJson(output).isEqualTo("""
                [
                  { "name": "Bob",   "age": 30 },
                  { "name": "Carol", "age": 41 }
                ]
                """);
    }
}
