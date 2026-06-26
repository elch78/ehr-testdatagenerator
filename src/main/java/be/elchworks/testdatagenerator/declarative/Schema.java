package be.elchworks.testdatagenerator.declarative;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li><b>Mothers are partial.</b> A mother only specifies some fields; completeness is the user's
 *   job. So a mother is validated against a copy of the schema with its {@code required} constraints
 *   removed — only the values it does set are checked.</li>
 * </ul>
 */
public final class Schema {

    private static final ObjectMapper JSON = new JsonMapper();
    private static final ObjectMapper YAML = new YAMLMapper();
    private static final String EXTENDS = "$extends";
    private static final String MOTHER = "$mother";
    private static final String RANDOM = "$random";

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
        resolve(name).forEach((field, value) -> mother.set(field, value.deepCopy()));
        removeRandomDirectives(mother);
        return new Validation(problems(motherValidator, mother.toString(), "Mother '" + name + "': "));
    }

    /** Drops {@code $random}-directive fields: they are a generation instruction, not data to validate. */
    private static void removeRandomDirectives(ObjectNode object) {
        List<String> directives = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            JsonNode value = field.getValue();
            if (isRandomDirective(value)) {
                directives.add(field.getKey());
            } else if (value.isObject()) {
                removeRandomDirectives((ObjectNode) value);
            }
        }
        directives.forEach(object::remove);
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
        for (Map.Entry<String, JsonNode> property : objectSchema.get("properties").properties()) {
            String name = property.getKey();
            valueFor(name, property.getValue(), values.get(name))
                    .ifPresent(value -> result.set(name, value));
        }
        return result;
    }

    /**
     * Renders the value the mother set for a property, and nothing else: an unset property is omitted
     * (even when the schema marks it required — completeness is the mother's responsibility). A
     * {@code $random} directive is resolved to a type-correct random value drawn from the schema, a
     * nested object the mother sets is generated recursively (so a {@code $mother} reference resolves),
     * and an array is rendered element by element by the same rules.
     */
    private Optional<JsonNode> valueFor(String name, JsonNode propertySchema, JsonNode set) {
        if (set == null) {
            return Optional.empty();
        }
        if (isRandomDirective(set)) {
            return Optional.of(RandomValue.forProperty(name, propertySchema));
        }
        if (isObject(propertySchema)) {
            return Optional.of(generateObject(propertySchema, valuesOf(set)));
        }
        if (isArray(propertySchema)) {
            return Optional.of(generateArray(propertySchema, set));
        }
        return Optional.of(set);
    }

    /** Renders each element the mother listed: an object element recursively, a scalar element as-is. */
    private ArrayNode generateArray(JsonNode arraySchema, JsonNode elements) {
        JsonNode itemSchema = arraySchema.get("items");
        ArrayNode result = JSON.createArrayNode();
        for (JsonNode element : elements) {
            if (isObject(itemSchema)) {
                result.add(generateObject(itemSchema, valuesOf(element)));
            } else {
                result.add(element);
            }
        }
        return result;
    }

    private static boolean isRandomDirective(JsonNode value) {
        return value.isObject() && value.has(RANDOM);
    }

    private static boolean isObject(JsonNode propertySchema) {
        return "object".equals(propertySchema.get("type").asString());
    }

    private static boolean isArray(JsonNode propertySchema) {
        return "array".equals(propertySchema.get("type").asString());
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
        if (describesObject(node)) {
            ObjectNode object = (ObjectNode) node;
            if (!object.has("additionalProperties")) {
                object.put("additionalProperties", false);
            }
        }
        forEachSubschema(node, Schema::rejectUnknownProperties);
    }

    /** Drops every {@code required} constraint, so a partial mother is not faulted for missing fields. */
    private static void removeRequired(JsonNode node) {
        if (describesObject(node)) {
            ((ObjectNode) node).remove("required");
        }
        forEachSubschema(node, Schema::removeRequired);
    }

    /** Visits the schemas nested in a node: an object's property schemas, or an array's item schema. */
    private static void forEachSubschema(JsonNode node, Consumer<JsonNode> action) {
        if (describesObject(node)) {
            for (Map.Entry<String, JsonNode> property : node.get("properties").properties()) {
                action.accept(property.getValue());
            }
        } else if (describesArray(node)) {
            action.accept(node.get("items"));
        }
    }

    private static boolean describesObject(JsonNode node) {
        JsonNode type = node.get("type");
        return type != null && "object".equals(type.asString()) && node.has("properties");
    }

    private static boolean describesArray(JsonNode node) {
        JsonNode type = node.get("type");
        return type != null && "array".equals(type.asString()) && node.has("items");
    }

    private static JsonNode parse(ObjectMapper format, String text) {
        try {
            return format.readTree(text);
        } catch (JacksonException e) {
            throw new RuntimeException("Cannot parse definition", e);
        }
    }
}
