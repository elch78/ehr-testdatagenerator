package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Migration;
import be.elchworks.testdatagenerator.declarative.Schema;
import be.elchworks.testdatagenerator.declarative.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want one report of every mother and every test data instance that no longer fits a
 * changed schema, so that when the schema evolves I know exactly what to migrate.
 */
class MigrationTest {

    @Test
    void reportsMothersAndDataThatNoLongerFitChangedSchema() {
        // given the schema changed: age was removed, email was added and made mandatory
        Schema changed = Schema.parse("""
                {
                  "type": "object",
                  "title": "Person",
                  "properties": {
                    "name":  { "type": "string" },
                    "email": { "type": "string" }
                  },
                  "required": ["email"]
                }
                """);

        // and the existing mother is registered against the new schema
        changed.define("alice", """
                { "name": "Alice", "age": 30 }
                """);

        // when migration checks the mother and existing test data against the new schema
        Validation report = new Migration(changed)
                .checkMother("alice")
                .checkData("""
                        { "name": "Bob", "age": 40 }
                        """)
                .report();

        // then every mismatch is collected in one report
        assertThat(report.isValid()).isFalse();
        assertThat(report.problems())
                .anyMatch(problem -> problem.contains("alice") && problem.contains("age"))
                .anyMatch(problem -> problem.contains("Test data") && problem.contains("age"))
                .anyMatch(problem -> problem.contains("email"));
    }
}
