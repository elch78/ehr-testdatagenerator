package be.elchworks.testdatagenerator.declarative;

import be.elchworks.testdatagenerator.Json;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A declarative mother: resolved values for a schema type, from which test data is generated.
 * Mandatory fields the mother leaves unset are filled with random values.
 */
public final class Mother {

    private final Schema schema;
    private final Map<String, JsonNode> values;

    Mother(Schema schema, Map<String, JsonNode> values) {
        this.schema = schema;
        this.values = values;
    }

    public String generate() {
        return Json.toJson(build());
    }

    JsonNode build() {
        return schema.generateData(values);
    }
}
