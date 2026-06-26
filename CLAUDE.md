# CLAUDE.md

Guidance for Claude when working in this repository. The user's global rules (ATDD workflow,
testing standards, coding guidelines, architecture) always apply; this file records only what is
specific to **testdatagenerator**.

## What this project is

A Java library that produces JSON test data using the **builder + object-mother** pattern. It offers
two equal paths over one core model (a schema plus named mothers): a **declarative path** (define
mothers in JSON/YAML against a JSON schema, then generate, validate, and migrate test data) and a
**Java code-generation path** (generate Java builders from a schema). See `README.md` for the
user-facing overview.

## Build & test

```bash
./mvnw test        # run all tests (Maven wrapper downloads Maven on first run)
./mvnw compile     # compile main sources only
```

- Java **26** toolchain, managed via sdkman (`sdk use java 26.0.1-tem`).
- The in-memory compiler (`InMemoryCompiler`) needs a **JDK**, not a JRE — `ToolProvider.getSystemJavaCompiler()`
  must be non-null.
- Build tool is **Maven**. The wrapper uses the `only-script` distribution; there is no
  checked-in wrapper jar.

## Workflow: ATDD, strictly

Every feature follows the global ATDD workflow, with one project-specific adaptation: **there are
no `.feature` files.** The acceptance test itself is the living specification — its class Javadoc
states the intent ("As a… I want… so that…") and its body is the scenario.

1. Describe the scenario for the new feature (test class Javadoc + Given/When/Then body),
   **stop, and get explicit approval** before any implementation.
2. Write the failing acceptance test (RED) and confirm it fails for the expected reason.
3. Implement the minimum to pass (GREEN).
4. Refactor with tests green.

Acceptance tests are behaviour-focused — no implementation details in the scenario.

## Project-specific conventions

- **No `Scenario` facade.** This is a library with no service layer, so tests call the library API
  directly. (The global ATDD examples use a `Scenario` class; deliberately skipped here.)
- **Object mothers live in `TestFixtures`** — static factory methods returning builders
  (e.g. `aPerson()`), per the global testing standard.
- **Builders** are static inner classes with a private constructor, reached via a factory method on
  the outer type (`Person.builder()`). Builders may expose `toJson()` as a convenience that
  serializes the built object.
- **Code generation uses no external codegen library yet.** Source is assembled as strings.
  Introducing JavaPoet (or similar) is a planned refactor once nested types / imports / collections
  make string assembly painful — drive it from a test, don't add it speculatively.

## Architecture (current)

Production code in `src/main/java/be/elchworks/testdatagenerator`:

Declarative path:

| Class | Responsibility |
|-------|----------------|
| `Schema` | Declarative entry point: parse a schema, register named mothers (JSON/YAML), `validate`/`validateData`. Validation is delegated to the **networknt** JSON Schema validator on the Jackson 3 `JsonNode`. The registry keeps the full standard dialect set (default **Draft 2020-12**, since our own schemas declare no `$schema`) and overlays only the **Draft 6** dialect with a tolerant variant for the FHIR schema's quirks (`discriminator` kept as an annotation; the stray draft-04 `id` redeclared non-validating). Two project rules shape validation: unknown properties are rejected by default (`additionalProperties:false` injected into every object unless the schema opts out with `additionalProperties:true`), and mothers are validated against a `required`-stripped copy (a mother is partial — completeness is the user's job). Both transforms `walk` through `$ref` into `definitions` so they reach referenced types and terminate on recursive schemas. The `$random` directive is stripped before mother validation (an instruction, not data). |
| `Mother` | A resolved mother; `generate()` renders **only** what the mother sets — an unset field is omitted (even if mandatory) and `generate()` never throws. A `$random` directive (`{ "$random": {} }`) is resolved to a schema-typed random value. Generation follows `$ref` and recurses into nested objects and arrays. A `$type` directive (`"$type": "Patient"`) points generation at a named `definitions` entry instead of the schema root — needed when the root is a `oneOf` over many types (FHIR); it is not a schema property, so it is never rendered. |
| `Datasets` | Which datasets to generate: a list of mother invocations (`$mother` reference + overrides); `generate()` yields one dataset per invocation as a JSON array. A `$mother` reference also works inside a nested object field (mother composition), resolved by the same `Schema.valuesOf`. |
| `RandomValue` | Schema-typed random value for a `$random` directive (string prefixed with the field name, `integer`, `format: date`). |
| `Validation` | Reusable check outcome: the problems found, or none when valid. |
| `Migration` | Aggregate mother/data mismatches against a changed schema into one report. |

Java code-generation path:

| Class | Responsibility |
|-------|----------------|
| `Json` | Serialize any object to JSON (Jackson). |
| `Generator` | Entry point: `from(schema).compile()`. |
| `JavaSource` | JSON schema → Java record source code. |
| `InMemoryCompiler` | Compile source to a loaded class without touching disk. |
| `GeneratedType` | Wrap the compiled type; expose a dynamic `builder().set(...).build()`. |

Delivery shell:

| Class | Responsibility |
|-------|----------------|
| `Cli` | `run(args)` reads `--schema`/`--mothers`/`--datasets` files (JSON or YAML), wires `Schema`, and returns the generated datasets JSON. Thin adapter; no executable `main` yet. |

Internally both paths hold schema and mother definitions as Jackson `JsonNode`, so JSON and YAML are
interchangeable input formats.

`Person`, `TestFixtures` live in `src/test` — they simulate how a library *user* defines their own
domain plus mother methods.

Visibility: private by default; only the public API (`Schema`, `Mother`, `Validation`, `Migration`,
`Json`, `Generator`, `GeneratedType`, `Cli`) is public.

## Known gaps / direction

- Schema support is `object` (incl. **nested** objects), **arrays** (of scalars or objects), scalar
  `string`/`integer`, and **`$ref`/`definitions`** (a type factored into `#/definitions/...` and
  referenced). Generation recurses into nested objects and arrays and follows `$ref`, rendering only
  what the mother sets (no auto-fill); an array element may itself be a `$mother` composition.
  Validation covers nested properties, array elements, referenced types and **type** mismatches (came
  for free with the networknt swap). The schema transforms (`additionalProperties:false` injection,
  `required`-stripping) walk through refs once, so they reach referenced types and terminate on
  recursive schemas. With these plus the `$type` directive, the **real** FHIR R4 `fhir.schema.json`
  runs end to end (`FhirPatientExampleTest`). Property schemas without a `type` (`const`/`enum`/bare
  `$ref`, as FHIR uses) are treated as scalars.
- The Java path does not yet emit a mother, and its builders are **dynamic** (`set("name", value)`)
  rather than typed (`.name(...)`).
- The CLI exists as a library seam (`Cli.run(args)`) but has no executable `main`/packaging yet, and
  no build-time plugin or web service; everything runs in memory at runtime.
