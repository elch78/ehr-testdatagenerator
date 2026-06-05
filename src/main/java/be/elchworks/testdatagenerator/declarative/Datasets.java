package be.elchworks.testdatagenerator.declarative;

import be.elchworks.testdatagenerator.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Which datasets to generate: a list of mother invocations, each a mother reference ({@code $mother})
 * plus optional field overrides. One dataset is generated per invocation — the declarative mirror of
 * composing object mothers in test code.
 */
public final class Datasets {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Schema schema;
    private final JsonNode invocations;

    Datasets(Schema schema, JsonNode invocations) {
        this.schema = schema;
        this.invocations = invocations;
    }

    public String generate() {
        ArrayNode datasets = MAPPER.createArrayNode();
        invocations.forEach(invocation -> datasets.add(new Mother(schema, schema.valuesOf(invocation)).build()));
        return Json.toJson(datasets);
    }
}
