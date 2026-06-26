package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.cli.Cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want the directory convention to read my real-world JSON schema even when it is larger
 * than the YAML parser's safety limit (the published FHIR schema is ~3.2 MB), so that such schemas
 * work through the tool. A {@code .json} file is read as JSON, not routed through the YAML parser.
 */
class LargeJsonSchemaTest {

    @Test
    void readsAJsonSchemaLargerThanTheYamlParserLimit(@TempDir Path dir) throws Exception {
        // a valid JSON schema padded past snakeyaml's ~3.1M code point limit
        String padding = "x".repeat(3_200_000);
        Files.writeString(dir.resolve("schema.json"), """
                { "type": "object", "title": "Person",
                  "description": "%s",
                  "properties": { "name": { "type": "string" } } }
                """.formatted(padding));

        Files.createDirectories(dir.resolve("mothers"));
        Files.writeString(dir.resolve("mothers/people.yaml"), """
                person:
                  name: Bob
                """);

        Files.createDirectories(dir.resolve("datasets"));
        Files.writeString(dir.resolve("datasets/people.yaml"), """
                - { $mother: person }
                """);

        // when the tool runs against the directory
        Cli.generate(dir);

        // then the large JSON schema is read and the output is produced
        assertThatJson(Files.readString(dir.resolve("out/people.json"))).isEqualTo("""
                [ { "name": "Bob" } ]
                """);
    }
}
