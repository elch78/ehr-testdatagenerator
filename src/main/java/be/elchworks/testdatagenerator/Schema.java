package be.elchworks.testdatagenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A JSON schema describing the shape of a test data type. Entry point for the declarative path:
 * a schema produces {@link Mother mothers} from which test data is generated.
 */
public final class Schema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> properties;

    private Schema(List<String> properties) {
        this.properties = properties;
    }

    public static Schema parse(String schema) {
        JsonNode root = read(schema);
        List<String> properties = new ArrayList<>();
        for (Map.Entry<String, JsonNode> property : root.get("properties").properties()) {
            properties.add(property.getKey());
        }
        return new Schema(properties);
    }

    public Mother mother(String definition) {
        return new Mother(this, read(definition));
    }

    List<String> properties() {
        return properties;
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
