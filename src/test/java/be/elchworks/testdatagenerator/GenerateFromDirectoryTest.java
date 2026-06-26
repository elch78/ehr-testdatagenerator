package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.cli.Cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

/**
 * As a user I want to drop my schema, my mother files and my dataset files into one directory by a
 * simple convention, and have the tool discover them and write one output per dataset file, so that I
 * generate test data without naming each input on the command line.
 *
 * <p>Convention: {@code <dir>/schema.json}, {@code <dir>/mothers/*.{json,yaml}} merged into one mother
 * namespace, {@code <dir>/datasets/*.{json,yaml}}; each datasets file produces {@code <dir>/out/<basename>.json}.
 */
class GenerateFromDirectoryTest {

    @Test
    void discoversInputsAndWritesOneOutputPerDatasetFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("schema.json"), """
                { "type": "object", "title": "Person",
                  "properties": { "name": { "type": "string" }, "age": { "type": "integer" } } }
                """);

        // two mother files, merged into one namespace
        Files.createDirectories(dir.resolve("mothers"));
        Files.writeString(dir.resolve("mothers/defaults.yaml"), """
                person:
                  name: Default
                  age: 30
                """);
        Files.writeString(dir.resolve("mothers/seniors.yaml"), """
                senior:
                  name: Senior
                  age: 99
                """);

        // two dataset files — one references a mother from each mother file
        Files.createDirectories(dir.resolve("datasets"));
        Files.writeString(dir.resolve("datasets/young.yaml"), """
                - { $mother: person, name: Bob }
                """);
        Files.writeString(dir.resolve("datasets/old.yaml"), """
                - { $mother: senior }
                """);

        // when the tool runs against the directory
        Cli.generate(dir);

        // then each dataset file yields one output of the same base name under out/
        assertThatJson(Files.readString(dir.resolve("out/young.json"))).isEqualTo("""
                [ { "name": "Bob", "age": 30 } ]
                """);
        assertThatJson(Files.readString(dir.resolve("out/old.json"))).isEqualTo("""
                [ { "name": "Senior", "age": 99 } ]
                """);
    }
}
