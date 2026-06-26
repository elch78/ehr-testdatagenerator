package be.elchworks.testdatagenerator.declarative;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

/**
 * Produces a random value of a property's JSON schema type, used to resolve a {@code $random}
 * directive. A plain string value is prefixed with the field name ({@code street-7f3a9c}) so a stray
 * random value stays traceable to where it came from; a {@code format: date} string yields an ISO
 * date instead.
 */
final class RandomValue {

    private static final Random RANDOM = new Random();

    private RandomValue() {
    }

    static JsonNode forProperty(String field, JsonNode propertySchema) {
        String type = propertySchema.get("type").asString();
        return switch (type) {
            case "string" -> string(field, propertySchema);
            case "integer" -> new IntNode(RANDOM.nextInt());
            default -> throw new RuntimeException("Cannot randomize schema type: " + type);
        };
    }

    private static JsonNode string(String field, JsonNode propertySchema) {
        if (hasFormat(propertySchema, "date")) {
            return new StringNode(randomDate());
        }
        return new StringNode(field + "-" + randomSuffix());
    }

    private static boolean hasFormat(JsonNode propertySchema, String format) {
        JsonNode node = propertySchema.get("format");
        return node != null && format.equals(node.asString());
    }

    private static String randomDate() {
        return LocalDate.now().minusDays(RANDOM.nextInt(36_500)).toString();
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
