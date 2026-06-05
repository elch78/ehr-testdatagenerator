package be.elchworks.testdatagenerator.declarative;

import be.elchworks.testdatagenerator.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Which datasets to generate: a list of mother invocations, each a mother reference ({@code $mother})
 * plus optional field overrides. One dataset is generated per invocation — the declarative mirror of
 * composing object mothers in test code.
 */
public final class Datasets {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MOTHER = "$mother";

    private final Schema schema;
    private final JsonNode invocations;

    Datasets(Schema schema, JsonNode invocations) {
        this.schema = schema;
        this.invocations = invocations;
    }

    public String generate() {
        ArrayNode datasets = MAPPER.createArrayNode();
        invocations.forEach(invocation -> datasets.add(generate(invocation)));
        return Json.toJson(datasets);
    }

    private JsonNode generate(JsonNode invocation) {
        Map<String, JsonNode> values = new HashMap<>(schema.resolve(invocation.get(MOTHER).asText()));
        applyOverrides(invocation, values);
        return new Mother(schema, values).build();
    }

    private void applyOverrides(JsonNode invocation, Map<String, JsonNode> values) {
        for (Map.Entry<String, JsonNode> override : invocation.properties()) {
            if (!override.getKey().equals(MOTHER)) {
                values.put(override.getKey(), override.getValue());
            }
        }
    }
}
