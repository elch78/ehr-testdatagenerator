package be.elchworks.testdatagenerator;

/**
 * Turns a JSON schema into a compiled, ready-to-use {@link GeneratedType}.
 */
public final class Generator {

    private final String schema;

    private Generator(String schema) {
        this.schema = schema;
    }

    public static Generator from(String schema) {
        return new Generator(schema);
    }

    public GeneratedType compile() {
        JavaSource source = JavaSource.fromSchema(schema);
        Class<?> type = InMemoryCompiler.compile(source.qualifiedName(), source.code());
        return new GeneratedType(type);
    }
}
