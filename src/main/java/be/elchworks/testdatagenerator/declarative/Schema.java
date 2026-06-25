package be.elchworks.testdatagenerator.declarative;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A JSON schema describing the shape of a test data type. Entry point for the declarative path:
 * named {@link Mother mothers} are defined against the schema and generate test data, and may
 * build on one another through the {@code $extends} property.
 *
 * <p>Validation is delegated to a JSON Schema validator. Two rules shape it:
 * <ul>
 *   <li><b>Unknown properties are rejected by default.</b> Every object the schema describes is
 *   validated as if it declared {@code "additionalProperties": false}, because for migration an
 *   unknown property is exactly the mismatch a user must learn about. A schema that deliberately
 *   allows extras can opt out by setting {@code "additionalProperties": true} itself.</li>
 *   <li><b>Mothers are partial.</b> A mother only specifies some fields; the generator fills the
 *   mandatory ones it leaves unset. So a mother is validated against a copy of the schema with its
 *   {@code required} constraints removed — only the values it does set are checked.</li>
 * </ul>
 */
public final class Schema {

    private static final ObjectMapper JSON = new JsonMapper();
    private static final ObjectMapper YAML = new YAMLMapper();
    private static final String EXTENDS = "$extends";
    private static final String MOTHER = "$mother";

    private final JsonNode root;
    private final com.networknt.schema.Schema dataValidator;
    private final com.networknt.schema.Schema motherValidator;
    private final Map<String, JsonNode> definitions = new HashMap<>();

    private Schema(JsonNode root,
                   com.networknt.schema.Schema dataValidator,
                   com.networknt.schema.Schema motherValidator) {
        this.root = root;
        this.dataValidator = dataValidator;
        this.motherValidator = motherValidator;
    }

    public static Schema parse(String schema) {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        return new Schema(parse(JSON, schema),
                registry.getSchema(strictSchema(schema)),
                registry.getSchema(partialSchema(schema)));
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
        ObjectNode mother = JSON.createObjectNode();
        resolve(name).forEach(mother::set);
        return new Validation(problems(motherValidator, mother.toString(), "Mother '" + name + "': "));
    }

    public Validation validateData(String testData) {
        return new Validation(problems(dataValidator, testData, "Test data: "));
    }

    private List<String> problems(com.networknt.schema.Schema validator, String json, String prefix) {
        List<String> problems = new ArrayList<>();
        for (var error : validator.validate(json, InputFormat.JSON)) {
            problems.add(prefix + error.getMessage());
        }
        return problems;
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
        return resolve(definition.get(EXTENDS).asString());
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
            return Optional.of(RandomValue.forType(propertySchema.get("type").asString()));
        }
        return Optional.empty();
    }

    private static boolean isObject(JsonNode propertySchema) {
        return "object".equals(propertySchema.get("type").asString());
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
            values.putAll(resolve(invocation.get(MOTHER).asString()));
        }
        for (Map.Entry<String, JsonNode> field : invocation.properties()) {
            if (!field.getKey().equals(MOTHER)) {
                values.put(field.getKey(), field.getValue());
            }
        }
        return values;
    }

    private static Set<String> requiredFields(JsonNode root) {
        Set<String> required = new HashSet<>();
        JsonNode node = root.get("required");
        if (node != null) {
            node.forEach(field -> required.add(field.asString()));
        }
        return required;
    }

    private static JsonNode strictSchema(String schema) {
        JsonNode node = parse(JSON, schema);
        rejectUnknownProperties(node);
        return node;
    }

    private static JsonNode partialSchema(String schema) {
        JsonNode node = strictSchema(schema);
        removeRequired(node);
        return node;
    }

    /** Makes every object in the schema reject unknown properties, unless it already says otherwise. */
    private static void rejectUnknownProperties(JsonNode node) {
        if (!describesObject(node)) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        if (!object.has("additionalProperties")) {
            object.put("additionalProperties", false);
        }
        forEachPropertySchema(object, Schema::rejectUnknownProperties);
    }

    /** Drops every {@code required} constraint, so a partial mother is not faulted for missing fields. */
    private static void removeRequired(JsonNode node) {
        if (!describesObject(node)) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        object.remove("required");
        forEachPropertySchema(object, Schema::removeRequired);
    }

    private static void forEachPropertySchema(ObjectNode object, Consumer<JsonNode> action) {
        for (Map.Entry<String, JsonNode> property : object.get("properties").properties()) {
            action.accept(property.getValue());
        }
    }

    private static boolean describesObject(JsonNode node) {
        JsonNode type = node.get("type");
        return type != null && "object".equals(type.asString()) && node.has("properties");
    }

    private static JsonNode parse(ObjectMapper format, String text) {
        try {
            return format.readTree(text);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot parse definition", e);
        }
    }
}
