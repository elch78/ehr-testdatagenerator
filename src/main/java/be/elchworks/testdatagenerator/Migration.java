package be.elchworks.testdatagenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks existing mothers and test data against a changed schema, collecting every mismatch into a
 * single {@link Validation} report. Migration adds no new checks — it runs the schema's validators
 * over the artifacts that must still fit.
 */
public final class Migration {

    private final Schema schema;
    private final List<String> problems = new ArrayList<>();

    public Migration(Schema schema) {
        this.schema = schema;
    }

    public Migration checkMother(String name) {
        problems.addAll(schema.validate(name).problems());
        return this;
    }

    public Migration checkData(String testData) {
        problems.addAll(schema.validateData(testData).problems());
        return this;
    }

    public Validation report() {
        return new Validation(problems);
    }
}
