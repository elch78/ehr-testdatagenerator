package be.elchworks.testdatagenerator.declarative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A JSON schema describing the shape of a test data type. Entry point for the declarative path:
 * named {@link Mother mothers} are defined against the schema and generate test data, and may
 * build on one another through the {@code $extends} property.
 */
public final class Schema {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final String EXTENDS = "$extends";
    private static final String MOTHER = "$mother";

    private final JsonNode root;
    private final Map<String, String> types;
    private final Set<String> required;
    private final Map<String, JsonNode> definitions = new HashMap<>();

    private Schema(JsonNode root, Map<String, String> types, Set<String> required) {
        this.root = root;
        this.types = types;
        this.required = required;
    }

    public static Schema parse(String schema) {
        JsonNode root = parse(JSON, schema);
        Map<String, String> types = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> property : root.get("properties").properties()) {
            types.put(property.getKey(), property.getValue().get("type").asText());
        }
        return new Schema(root, types, requiredFields(root));
    }

    public void define(String name, String definition) {
        definitions.put(name, parse(JSON, definition));
    }

    public void defineYaml(String name, String definition) {
        definitions.put(name, parse(YAML, definition));
    }

    public Mother mother(String name) {
        return new Mother(this, resolve(name));
    }

    public Datasets datasets(String datasets) {
        return new Datasets(this, parse(JSON, datasets));
    }

    public Validation validate(String name) {
        List<String> problems = new ArrayList<>();
        for (String property : resolve(name).keySet()) {
            if (!isKnown(property)) {
                problems.add("Mother '" + name + "' sets unknown property '" + property + "'");
            }
        }
        return new Validation(problems);
    }

    public Validation validateData(String testData) {
        JsonNode data = parse(JSON, testData);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : data.properties()) {
            if (!isKnown(field.getKey())) {
                problems.add("Test data sets unknown property '" + field.getKey() + "'");
            }
        }
        for (String property : required) {
            if (!data.has(property)) {
                problems.add("Test data is missing required property '" + property + "'");
            }
        }
        return new Validation(problems);
    }

    Map<String, JsonNode> resolve(String name) {
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

    JsonNode generateData(Map<String, JsonNode> values) {
        return generateObject(root, values);
    }

    private ObjectNode generateObject(JsonNode objectSchema, Map<String, JsonNode> values) {
        ObjectNode result = JSON.createObjectNode();
        Set<String> required = requiredFields(objectSchema);
        for (Map.Entry<String, JsonNode> property : objectSchema.get("properties").properties()) {
            String name = property.getKey();
            valueFor(property.getValue(), required.contains(name), values.get(name))
                    .ifPresent(value -> result.set(name, value));
        }
        return result;
    }

    private Optional<JsonNode> valueFor(JsonNode propertySchema, boolean required, JsonNode set) {
        if (isObject(propertySchema)) {
            if (set == null && !required) {
                return Optional.empty();
            }
            return Optional.of(generateObject(propertySchema, valuesOf(set)));
        }
        if (set != null) {
            return Optional.of(set);
        }
        if (required) {
            return Optional.of(RandomValue.forType(propertySchema.get("type").asText()));
        }
        return Optional.empty();
    }

    private static boolean isObject(JsonNode propertySchema) {
        return "object".equals(propertySchema.get("type").asText());
    }

    /**
     * The values of a mother invocation: the referenced {@code $mother} resolved as the base, with
     * the invocation's own fields applied as overrides. Used both for a nested object field and for a
     * top-level dataset invocation.
     */
    Map<String, JsonNode> valuesOf(JsonNode invocation) {
        Map<String, JsonNode> values = new HashMap<>();
        if (invocation == null) {
            return values;
        }
        if (invocation.has(MOTHER)) {
            values.putAll(resolve(invocation.get(MOTHER).asText()));
        }
        for (Map.Entry<String, JsonNode> field : invocation.properties()) {
            if (!field.getKey().equals(MOTHER)) {
                values.put(field.getKey(), field.getValue());
            }
        }
        return values;
    }

    private boolean isKnown(String property) {
        return types.containsKey(property);
    }

    private static Set<String> requiredFields(JsonNode root) {
        Set<String> required = new HashSet<>();
        JsonNode node = root.get("required");
        if (node != null) {
            node.forEach(field -> required.add(field.asText()));
        }
        return required;
    }

    private static JsonNode parse(ObjectMapper format, String text) {
        try {
            return format.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot parse definition", e);
        }
    }
}
