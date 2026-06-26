package be.elchworks.testdatagenerator.declarative;

import be.elchworks.testdatagenerator.Json;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A declarative mother: resolved values for a schema type, from which test data is generated.
 * Generation renders only what the mother sets — an unset field is omitted, even when mandatory —
 * except a {@code $random} directive, which is resolved to a schema-typed random value.
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
