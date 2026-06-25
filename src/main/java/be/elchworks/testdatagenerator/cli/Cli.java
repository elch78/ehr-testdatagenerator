package be.elchworks.testdatagenerator.cli;

import be.elchworks.testdatagenerator.Json;
import be.elchworks.testdatagenerator.declarative.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The delivery shell: reads a schema, named mothers and the wanted datasets from files and prints the
 * generated test data. A thin adapter over the declarative core — it only reads inputs (JSON or YAML)
 * and wires {@link Schema}.
 */
public final class Cli {

    private static final ObjectMapper YAML = new YAMLMapper();

    private Cli() {
    }

    public static String run(String... args) {
        Map<String, String> options = options(args);
        Schema schema = Schema.parse(jsonOf(options.get("--schema")));
        defineMothers(schema, nodeOf(options.get("--mothers")));
        return schema.datasets(jsonOf(options.get("--datasets"))).generate();
    }

    private static void defineMothers(Schema schema, JsonNode mothers) {
        mothers.properties().forEach(mother ->
                schema.define(mother.getKey(), Json.toJson(mother.getValue())));
    }

    private static Map<String, String> options(String... args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        return options;
    }

    private static String jsonOf(String path) {
        return Json.toJson(nodeOf(path));
    }

    private static JsonNode nodeOf(String path) {
        try {
            return YAML.readTree(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
