package be.elchworks.testdatagenerator.declarative;

import be.elchworks.testdatagenerator.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

/**
 * A declarative mother: resolved values for a schema type, from which test data is generated.
 * Mandatory fields the mother leaves unset are filled with random values.
 */
public final class Mother {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Schema schema;
    private final Map<String, JsonNode> values;

    Mother(Schema schema, Map<String, JsonNode> values) {
        this.schema = schema;
        this.values = values;
    }

    public String generate() {
        ObjectNode testData = MAPPER.createObjectNode();
        for (String property : schema.properties()) {
            valueFor(property).ifPresent(value -> testData.set(property, value));
        }
        return Json.toJson(testData);
    }

    private Optional<JsonNode> valueFor(String property) {
        if (values.containsKey(property)) {
            return Optional.of(values.get(property));
        }
        if (schema.isRequired(property)) {
            return Optional.of(RandomValue.forType(schema.type(property)));
        }
        return Optional.empty();
    }
}
