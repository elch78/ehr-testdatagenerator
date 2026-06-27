package be.elchworks.testdatagenerator;

import be.elchworks.testdatagenerator.declarative.Schema;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * As a user I want to assemble a FHIR Bundle from several resource mothers that reference one
 * another, so that I can generate a realistic clinical dataset — not just a single resource.
 * A collection Bundle holds a Patient and an Observation about that patient
 * (subject -> "Patient/jane"). Each entry's resource is a oneOf over every FHIR resource, so the
 * mother names the type per entry with $type; the Bundle is composed by referencing the patient and
 * observation mothers, and the whole thing validates against the real, unmodified FHIR schema.
 */
class FhirBundleExampleTest {

    private static final String FHIR_SCHEMA = resource("fhir.schema.json");

    @Test
    void composeAndValidateABundleOfReferencingResources() {
        Schema fhir = Schema.parse(FHIR_SCHEMA);

        fhir.define("aFemalePatient", """
                {
                  "$type": "Patient",
                  "resourceType": "Patient",
                  "id": "jane",
                  "gender": "female",
                  "birthDate": "1985-07-12",
                  "name": [ { "family": "Doe", "given": ["Jane"] } ]
                }
                """);

        fhir.define("aBodyWeight", """
                {
                  "$type": "Observation",
                  "resourceType": "Observation",
                  "status": "final",
                  "code": { "text": "Body weight" },
                  "subject": { "reference": "Patient/jane" }
                }
                """);

        // the Bundle composes the two resource mothers, one per entry
        fhir.define("aPatientBundle", """
                {
                  "$type": "Bundle",
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    { "resource": { "$mother": "aFemalePatient" } },
                    { "resource": { "$mother": "aBodyWeight" } }
                  ]
                }
                """);

        // when the Bundle is generated
        String bundle = fhir.mother("aPatientBundle").generate();

        // then each entry holds the fully resolved resource; $type / $mother are directives, never rendered
        assertThatJson(bundle).isEqualTo("""
                {
                  "resourceType": "Bundle",
                  "type": "collection",
                  "entry": [
                    { "resource": {
                        "resourceType": "Patient",
                        "id": "jane",
                        "gender": "female",
                        "birthDate": "1985-07-12",
                        "name": [ { "family": "Doe", "given": ["Jane"] } ]
                    } },
                    { "resource": {
                        "resourceType": "Observation",
                        "status": "final",
                        "code": { "text": "Body weight" },
                        "subject": { "reference": "Patient/jane" }
                    } }
                  ]
                }
                """);

        // and the whole Bundle validates against the official FHIR schema
        assertThat(fhir.validateData(bundle).isValid()).isTrue();
    }

    private static String resource(String name) {
        try (InputStream in = FhirBundleExampleTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
