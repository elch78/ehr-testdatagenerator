package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want to drive the real, unmodified FHIR schema — the published fhir.schema.json — so
 * that I can define a mother for a Patient, generate it, and validate it against the official schema.
 * This proves generation and validation on a real-world schema (oneOf root, $ref definitions, arrays,
 * nested types, type-less const/enum properties), not a hand-trimmed copy.
 */
class FhirPatientExampleTest {

    private static final String FHIR_SCHEMA = resource("fhir.schema.json");

    @Test
    void generateAndValidateAPatientAgainstTheRealFhirSchema() {
        Schema fhir = Schema.parse(FHIR_SCHEMA);

        // $type names the resource to build; it travels in the JSON, like $mother / $random / $ref
        fhir.define("patient", """
                {
                  "$type": "Patient",
                  "resourceType": "Patient",
                  "gender": "female",
                  "birthDate": "1985-07-12",
                  "name": [ { "family": "Doe", "given": ["Jane"] } ]
                }
                """);

        // when the Patient is generated
        String patient = fhir.mother("patient").generate();

        // then exactly those fields are rendered; $type is a directive, stripped from the output
        assertThatJson(patient).isEqualTo("""
                {
                  "resourceType": "Patient",
                  "gender": "female",
                  "birthDate": "1985-07-12",
                  "name": [ { "family": "Doe", "given": ["Jane"] } ]
                }
                """);

        // and it validates against the official FHIR schema
        assertThat(fhir.validateData(patient).isValid()).isTrue();
    }

    private static String resource(String name) {
        try (InputStream in = FhirPatientExampleTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
