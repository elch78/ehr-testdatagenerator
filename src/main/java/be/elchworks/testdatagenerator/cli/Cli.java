package be.elchworks.testdatagenerator.cli;

import be.elchworks.testdatagenerator.Json;
import be.elchworks.testdatagenerator.declarative.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The delivery shell over the declarative core. It reads inputs (JSON or YAML) and wires {@link Schema};
 * it adds no behaviour of its own. Two entry points:
 * <ul>
 *   <li>{@link #run} — the three inputs named explicitly as {@code --schema}/{@code --mothers}/{@code --datasets} files.</li>
 *   <li>{@link #generate} — a directory following a convention: {@code schema.json}, a {@code mothers/}
 *   directory whose files are merged into one mother namespace, and a {@code datasets/} directory whose
 *   files each yield one output written to {@code out/<basename>.json}.</li>
 * </ul>
 */
public final class Cli {

    private static final ObjectMapper YAML = new YAMLMapper();

    private Cli() {
    }

    public static String run(String... args) {
        Map<String, String> options = options(args);
        Schema schema = Schema.parse(jsonOf(Path.of(options.get("--schema"))));
        defineMothers(schema, nodeOf(Path.of(options.get("--mothers"))));
        return schema.datasets(jsonOf(Path.of(options.get("--datasets")))).generate();
    }

    /**
     * Generates test data from a directory: parses {@code schema.json}, defines every mother found under
     * {@code mothers/} (all files merged into one namespace), and writes one output per file under
     * {@code datasets/} to {@code out/<basename>.json}.
     */
    public static void generate(Path directory) {
        Schema schema = Schema.parse(jsonOf(directory.resolve("schema.json")));
        defineMothers(schema, directory.resolve("mothers"));
        writeOutputs(schema, directory.resolve("datasets"), directory.resolve("out"));
    }

    private static void defineMothers(Schema schema, Path mothersDirectory) {
        Set<String> defined = new HashSet<>();
        for (Path file : filesIn(mothersDirectory)) {
            nodeOf(file).properties().forEach(mother -> {
                if (!defined.add(mother.getKey())) {
                    throw new RuntimeException("Duplicate mother '" + mother.getKey() + "' in " + file);
                }
                schema.define(mother.getKey(), Json.toJson(mother.getValue()));
            });
        }
    }

    private static void writeOutputs(Schema schema, Path datasetsDirectory, Path outputDirectory) {
        createDirectory(outputDirectory);
        for (Path file : filesIn(datasetsDirectory)) {
            String datasets = schema.datasets(jsonOf(file)).generate();
            write(outputDirectory.resolve(baseName(file) + ".json"), datasets);
        }
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

    private static List<Path> filesIn(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String jsonOf(Path path) {
        return Json.toJson(nodeOf(path));
    }

    private static JsonNode nodeOf(Path path) {
        try {
            return YAML.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
