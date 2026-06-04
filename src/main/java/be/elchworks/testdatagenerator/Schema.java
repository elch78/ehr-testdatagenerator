package be.elchworks.testdatagenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON schema describing the shape of a test data type. Entry point for the declarative path:
 * named {@link Mother mothers} are defined against the schema and generate test data, and may
 * build on one another through the {@code $extends} property.
 */
public final class Schema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXTENDS = "$extends";

    private final Map<String, String> types;
    private final Set<String> required;
    private final Map<String, JsonNode> definitions = new HashMap<>();

    private Schema(Map<String, String> types, Set<String> required) {
        this.types = types;
        this.required = required;
    }

    public static Schema parse(String schema) {
        JsonNode root = read(schema);
        Map<String, String> types = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : root.get("properties").properties()) {
            types.put(property.getKey(), property.getValue().get("type").asText());
        }
        return new Schema(types, requiredFields(root));
    }

    public void define(String name, String definition) {
        definitions.put(name, read(definition));
    }

    public Mother mother(String name) {
        return new Mother(this, resolve(name));
    }

    public Validation validate(String name) {
        List<String> problems = new ArrayList<>();
        for (String property : resolve(name).keySet()) {
            if (!types.containsKey(property)) {
                problems.add("Mother '" + name + "' sets unknown property '" + property + "'");
            }
        }
        return new Validation(problems);
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

    Collection<String> properties() {
        return types.keySet();
    }

    String type(String property) {
        return types.get(property);
    }

    boolean isRequired(String property) {
        return required.contains(property);
    }

    private static Set<String> requiredFields(JsonNode root) {
        Set<String> required = new HashSet<>();
        JsonNode node = root.get("required");
        if (node != null) {
            node.forEach(field -> required.add(field.asText()));
        }
        return required;
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
