package be.elchworks.testdatagenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

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
            case "string" -> new TextNode(UUID.randomUUID().toString());
            case "integer" -> new IntNode(RANDOM.nextInt());
            default -> throw new IllegalArgumentException("Cannot randomize schema type: " + schemaType);
        };
    }
}
