package be.elchworks.testdatagenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A declarative mother: partial values for a schema type, from which test data is generated.
 */
public final class Mother {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Schema schema;
    private final JsonNode values;

    Mother(Schema schema, JsonNode values) {
        this.schema = schema;
        this.values = values;
    }

    public String generate() {
        ObjectNode testData = MAPPER.createObjectNode();
        for (String property : schema.properties()) {
            testData.set(property, values.get(property));
        }
        return Json.toJson(testData);
    }
}
