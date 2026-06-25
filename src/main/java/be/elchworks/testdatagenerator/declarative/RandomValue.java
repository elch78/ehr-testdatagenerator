package be.elchworks.testdatagenerator.declarative;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

import java.util.Random;
import java.util.UUID;

/**
 * Produces a random value of a given JSON schema type, used to fill mandatory fields a mother
 * leaves unset.
 */
final class RandomValue {

    private static final Random RANDOM = new Random();

    private RandomValue() {
    }

    static JsonNode forType(String schemaType) {
        return switch (schemaType) {
            case "string" -> new StringNode(UUID.randomUUID().toString());
            case "integer" -> new IntNode(RANDOM.nextInt());
            default -> throw new IllegalArgumentException("Cannot randomize schema type: " + schemaType);
        };
    }
}
