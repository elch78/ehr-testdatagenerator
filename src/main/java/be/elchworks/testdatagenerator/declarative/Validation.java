package be.elchworks.testdatagenerator.declarative;

import java.util.List;

/**
 * The outcome of validating an artifact against a schema: the problems found, or none when valid.
 */
public record Validation(List<String> problems) {

    public Validation {
        problems = List.copyOf(problems);
    }

    public boolean isValid() {
        return problems.isEmpty();
    }
}
