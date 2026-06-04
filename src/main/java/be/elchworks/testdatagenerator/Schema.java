package be.elchworks.testdatagenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON schema describing the shape of a test data type. Entry point for the declarative path:
 * named {@link Mother mothers} are defined against the schema and generate test data, and may
 * build on one another through the {@code $extends} property.
 */
public final class Schema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXTENDS = "$extends";

    private final List<String> properties;
    private final Map<String, JsonNode> definitions = new HashMap<>();

    private Schema(List<String> properties) {
        this.properties = properties;
    }

    public static Schema parse(String schema) {
        JsonNode root = read(schema);
        List<String> properties = new ArrayList<>();
        for (Map.Entry<String, JsonNode> property : root.get("properties").properties()) {
            properties.add(property.getKey());
        }
        return new Schema(properties);
    }

    public void define(String name, String definition) {
        definitions.put(name, read(definition));
    }

    public Mother mother(String name) {
        return new Mother(this, resolve(name));
    }

    private Map<String, JsonNode> resolve(String name) {
        JsonNode definition = definitions.get(name);
        Map<String, JsonNode> values = inheritedValues(definition);
        addOwnValues(definition, values);
        return values;
    }

    private Map<String, JsonNode> inheritedValues(JsonNode definition) {
        if (!definition.has(EXTENDS)) {
            return new HashMap<>();
        }
        return resolve(definition.get(EXTENDS).asText());
    }

    private void addOwnValues(JsonNode definition, Map<String, JsonNode> values) {
        for (Map.Entry<String, JsonNode> field : definition.properties()) {
            if (!field.getKey().equals(EXTENDS)) {
                values.put(field.getKey(), field.getValue());
            }
        }
    }

    List<String> properties() {
        return properties;
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
