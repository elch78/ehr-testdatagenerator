package be.elchworks.testdatagenerator.codegen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Java source code of a record type derived from a JSON schema.
 */
final class JavaSource {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PACKAGE = "be.elchworks.testdatagenerator.generated";

    private final String typeName;
    private final String code;

    private JavaSource(String typeName, String code) {
        this.typeName = typeName;
        this.code = code;
    }

    static JavaSource fromSchema(String schema) {
        JsonNode root = parse(schema);
        String typeName = root.get("title").asText();
        String code = "package " + PACKAGE + ";\n\n"
                + "public record " + typeName + "(" + components(root) + ") {\n}\n";
        return new JavaSource(typeName, code);
    }

    private static String components(JsonNode root) {
        List<String> components = new ArrayList<>();
        for (Map.Entry<String, JsonNode> property : root.get("properties").properties()) {
            String javaType = javaType(property.getValue().get("type").asText());
            components.add(javaType + " " + property.getKey());
        }
        return String.join(", ", components);
    }

    private static String javaType(String schemaType) {
        return switch (schemaType) {
            case "string" -> "String";
            case "integer" -> "int";
            default -> throw new IllegalArgumentException("Unsupported schema type: " + schemaType);
        };
    }

    private static JsonNode parse(String schema) {
        try {
            return MAPPER.readTree(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON schema", e);
        }
    }

    String qualifiedName() {
        return PACKAGE + "." + typeName;
    }

    String code() {
        return code;
    }
}
