package be.elchworks.testdatagenerator.declarative;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.DefaultDialectRegistry;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.NonValidationKeyword;
import com.networknt.schema.keyword.UnknownKeywordFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
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
    private static final String REF = "$ref";
    private static final String TYPE = "$type";

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
        SchemaRegistry registry = registry();
        return new Schema(parse(JSON, schema),
                registry.getSchema(strictSchema(schema)),
                registry.getSchema(partialSchema(schema)));
    }

    /**
     * A validator registry that keeps the full set of standard dialects — so any schema's declared
     * {@code $schema} still resolves — and defaults to Draft 2020-12 for our own schemas, which declare
     * none. Only the Draft 6 dialect (the version the FHIR schema declares) is overlaid with a tolerant
     * variant for that schema's two quirks: its OpenAPI {@code discriminator} (an unknown keyword, kept
     * as an annotation) and its stray draft-04 {@code id} (a known keyword with no validator in Draft 6,
     * where the identifier is {@code $id}; redeclared here as non-validating so it is ignored, not rejected).
     */
    private static SchemaRegistry registry() {
        Dialect tolerantDraft6 = Dialect.builder(Dialects.getDraft6())
                .unknownKeywordFactory(UnknownKeywordFactory.getInstance())
                .keyword(new NonValidationKeyword("id"))
                .build();
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.dialectRegistry(new DefaultDialectRegistry(List.of(tolerantDraft6))));
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
        return generateObject(entrySchema(values), values);
    }

    /**
     * Where generation starts: the schema root, unless the mother names a resource with {@code $type}
     * (e.g. {@code "$type": "Patient"}), which points generation at that {@code definitions} entry — the
     * way to pick one resource out of a schema whose root is a {@code oneOf} over many, like FHIR's. The
     * {@code $type} value is not a schema property, so it is never rendered into the output.
     */
    private JsonNode entrySchema(Map<String, JsonNode> values) {
        if (!values.containsKey(TYPE)) {
            return root;
        }
        return typeSchema(values);
    }

    /** The {@code definitions} entry a {@code $type} directive names. */
    private JsonNode typeSchema(Map<String, JsonNode> values) {
        return pointer(root, "#/definitions/" + values.get(TYPE).asString());
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
     * and an array is rendered element by element by the same rules. A {@code $type} directive in the
     * set values selects which {@code definitions} type to generate against — needed where the property
     * schema is a {@code oneOf} over many types (FHIR's {@code Bundle.entry.resource}).
     */
    private Optional<JsonNode> valueFor(String name, JsonNode propertySchema, JsonNode set) {
        if (set == null) {
            return Optional.empty();
        }
        JsonNode schema = resolveRef(propertySchema);
        if (isRandomDirective(set)) {
            return Optional.of(RandomValue.forProperty(name, schema));
        }
        if (set.isObject()) {
            Map<String, JsonNode> values = valuesOf(set);
            if (values.containsKey(TYPE)) {
                return Optional.of(generateObject(typeSchema(values), values));
            }
            if (isObject(schema)) {
                return Optional.of(generateObject(schema, values));
            }
        }
        if (isArray(schema)) {
            return Optional.of(generateArray(schema, set));
        }
        return Optional.of(set);
    }

    /** Renders each element the mother listed: an object element recursively, a scalar element as-is. */
    private ArrayNode generateArray(JsonNode arraySchema, JsonNode elements) {
        JsonNode itemSchema = resolveRef(arraySchema.get("items"));
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

    /**
     * A schema describes an object when it lists {@code properties} — the declared {@code type} is not
     * required, because the FHIR definitions carry properties without a {@code "type": "object"}.
     */
    private static boolean isObject(JsonNode schema) {
        return schema.has("properties");
    }

    private static boolean isArray(JsonNode propertySchema) {
        return hasType(propertySchema, "array");
    }

    /** A schema may describe a value without a {@code type} (e.g. {@code const}, {@code enum}, a bare {@code $ref}). */
    private static boolean hasType(JsonNode schema, String type) {
        JsonNode declared = schema.get("type");
        return declared != null && type.equals(declared.asString());
    }

    /** Follows a {@code $ref} to the type it names; a schema that is not a reference is returned as is. */
    private JsonNode resolveRef(JsonNode schema) {
        if (!schema.has(REF)) {
            return schema;
        }
        return pointer(root, schema.get(REF).asString());
    }

    /** Resolves a local JSON pointer such as {@code #/definitions/Member} against the schema root. */
    private static JsonNode pointer(JsonNode root, String ref) {
        JsonNode node = root;
        for (String token : ref.substring(2).split("/")) {
            node = node.get(token);
        }
        return node;
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

    /** Makes every object in the schema reject unknown properties, unless it already says otherwise. */
    private static JsonNode strictSchema(String schema) {
        JsonNode node = parse(JSON, schema);
        walk(node, node, new HashSet<>(), object -> {
            if (!object.has("additionalProperties")) {
                object.put("additionalProperties", false);
            }
        });
        return node;
    }

    /** Drops every {@code required} constraint, so a partial mother is not faulted for missing fields. */
    private static JsonNode partialSchema(String schema) {
        JsonNode node = strictSchema(schema);
        walk(node, node, new HashSet<>(), object -> object.remove("required"));
        return node;
    }

    /**
     * Applies an action to every object the schema describes, descending into an object's property
     * schemas and an array's item schema, and following each {@code $ref} into the type it names. A ref
     * is followed at most once, which both avoids redundant work and makes recursive schemas terminate.
     */
    private static void walk(JsonNode node, JsonNode root, Set<String> visited, Consumer<ObjectNode> onObject) {
        JsonNode schema = resolveRef(node, root, visited);
        if (schema == null) {
            return;
        }
        if (describesObject(schema)) {
            onObject.accept((ObjectNode) schema);
            for (Map.Entry<String, JsonNode> property : schema.get("properties").properties()) {
                walk(property.getValue(), root, visited, onObject);
            }
        } else if (describesArray(schema)) {
            walk(schema.get("items"), root, visited, onObject);
        }
    }

    /** Follows a {@code $ref}, or returns {@code null} once a ref has already been followed (cycle stop). */
    private static JsonNode resolveRef(JsonNode node, JsonNode root, Set<String> visited) {
        if (!node.has(REF)) {
            return node;
        }
        String ref = node.get(REF).asString();
        if (!visited.add(ref)) {
            return null;
        }
        return pointer(root, ref);
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
