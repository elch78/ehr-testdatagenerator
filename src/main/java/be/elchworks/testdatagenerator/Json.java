package be.elchworks.testdatagenerator;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;

/**
 * Serializes test data objects to their JSON representation.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private Json() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize to JSON: " + value, e);
        }
    }
}
