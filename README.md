# testdatagenerator

A Java library for producing JSON test data, built around the **builder + object-mother**
pattern many developers already use in their tests — but reusable beyond the test source set,
and for arbitrary, schema-defined shapes.

## Why

The builder + object-mother pattern gives test data two qualities that are hard to get otherwise:

- **Focus** — a test sets only the fields relevant to its scenario; everything else is a sensible
  (often randomized) default supplied by the mother.
- **Readability** — `aPerson().name("Alice")` reads like intent, not like data plumbing.

This library keeps that ergonomics and adds three things:

1. **JSON serialization**, so any built object can become JSON in one call.
2. A **declarative path**: define mothers in JSON or YAML against a JSON schema, generate test data
   from them, validate them, and produce a migration report when the schema changes — all without
   writing Java.
3. **Code generation** from a JSON schema, so the Java builder for a given shape can be generated
   instead of hand-written.

The declarative path and the Java code-generation path are two equal ways to the same goal, sharing
one core model (a schema plus named mothers).

## Status

Early, test-driven. Each capability's living specification is its acceptance test: the test class
Javadoc states the intent, and the test body is the executable scenario. There are no separate
`.feature` files.

| Capability | Entry point | Acceptance test |
|------------|-------------|-----------------|
| Build a domain object and serialize it to JSON | `Json.toJson(object)` | `BuildObjectAsJsonTest` |
| Generate test data from a declarative mother | `Schema.parse(schema).mother(name).generate()` | `GenerateTestDataFromMotherTest` |
| Let a mother build on another (`$extends`) | `Schema.define(...)` | `MotherInheritanceTest` |
| Generate exactly what the mother sets, nothing more | `Mother.generate()` | `GenerateRendersOnlySetValuesTest` |
| Generate array properties, composing a mother into each element | `Mother.generate()` | `ArraySchemaTest` |
| Resolve `$ref` into `definitions` when generating and validating | `Mother.generate()` | `RefSchemaTest` |
| Generate & validate a Patient against the real FHIR schema | `Schema.define(... "$type" ...)` | `FhirPatientExampleTest` |
| Fill a field on demand with a schema-typed random value (`$random`) | `Mother.generate()` | `RandomDirectiveTest` |
| Define a mother in YAML | `Schema.defineYaml(name, yaml)` | `DefineMotherInYamlTest` |
| Validate a mother against its schema | `Schema.validate(name)` | `ValidateMotherTest` |
| Validate test data against its schema | `Schema.validateData(json)` | `ValidateTestDataTest` |
| Report what breaks when the schema changes | `new Migration(schema)` | `MigrationTest` |
| Generate a Java builder from a JSON schema | `Generator.from(schema).compile()` | `GenerateBuilderFromSchemaTest` |

## Usage

### Generate test data from a declarative mother

```java
Schema person = Schema.parse("""
        {
          "type": "object",
          "title": "Person",
          "properties": {
            "name": { "type": "string" },
            "age":  { "type": "integer" }
          }
        }
        """);

person.define("alice", """
        { "name": "Alice", "age": 30 }
        """);

String testData = person.mother("alice").generate(); // {"name":"Alice","age":30}
```

Generation renders **only** what the mother sets — an unset field is omitted, even when the schema
marks it required, and `generate()` never throws. Completeness is the mother's job: build on a base
mother, or ask for a value explicitly with the `$random` directive, whose type follows the schema (a
plain string is prefixed with the field name for traceability, a `format: date` field yields a date):

```java
person.define("base", """
        { "name": "Widget" }
        """);
person.defineYaml("child", """
        $extends: base
        age: { "$random": {} }
        """);
```

### Validate and migrate

When the schema changes, check existing mothers and test data against the new schema in one report:

```java
Validation report = new Migration(newSchema)
        .checkMother("alice")
        .checkData(existingTestData)
        .report();

report.isValid();   // false if anything no longer fits
report.problems();  // e.g. "Mother 'alice': property 'age' is not defined in the schema ..."
```

Validation is delegated to a JSON Schema validator and follows two rules:

- **Unknown properties are rejected by default.** Every object the schema describes is validated as
  if it declared `"additionalProperties": false` — for migration, a property the schema no longer
  knows is exactly the mismatch you need to learn about. A schema that deliberately allows extra
  properties can opt out by setting `"additionalProperties": true` itself.
- **Mothers are partial.** A mother only sets some fields; completeness is the user's job. So a mother
  is checked against the schema with its `required` constraints removed — only the values it does set
  are validated (unknown properties, type mismatches), never a missing mandatory field. Test data, by
  contrast, is a complete instance and *is* checked for missing mandatory fields.

### Serialize a hand-written builder to JSON

```java
String json = aPerson()
        .name("Alice")
        .age(30)
        .toJson();
// {"name":"Alice","age":30}
```

`Person`, its `Builder`, and the `aPerson()` mother are written by the library user — exactly as in
a test fixture. The library supplies the serialization (`Json.toJson`).

### Generate a Java builder from a JSON schema

```java
GeneratedType person = Generator.from(schema).compile();

Object built = person.builder()
        .set("name", "Alice")
        .set("age", 30)
        .build();

String json = Json.toJson(built); // {"name":"Alice","age":30}
```

The schema is parsed, a Java record is emitted, compiled in memory (no files written), and loaded.
The returned builder is currently **dynamic** (`set(property, value)`); typed builder methods
(`.name(...)`) are a planned step.

## How it works

The declarative path and the Java path share one core: a parsed schema and named mothers, both held
internally as Jackson `JsonNode` trees so JSON and YAML are interchangeable input formats.

| Class | Responsibility |
|-------|----------------|
| `Schema` | Declarative entry point: parses a schema, registers named mothers (JSON/YAML), validates mothers and test data (via a JSON Schema validator). |
| `Mother` | A resolved mother; `generate()` renders only what the mother sets (no auto-fill), resolving any `$random` directive. |
| `RandomValue` | Schema-typed random value for a `$random` directive (field-name-prefixed string, integer, or `format: date`). |
| `Validation` | Reusable outcome of a check: the problems found, or none when valid. |
| `Migration` | Collects every mother/data mismatch against a changed schema into one report. |
| `Json` | Serializes any object to JSON (Jackson). |
| `Generator` | Java path entry point: `from(schema).compile()`. |
| `JavaSource` | Turns a JSON schema into Java record source code. |
| `InMemoryCompiler` | Compiles source to a loaded class via the JDK compiler, without touching disk. |
| `GeneratedType` | Wraps the compiled type; exposes a dynamic builder. |

Code generation deliberately uses no external code-generation library yet. A source-emitting
helper such as JavaPoet is a planned refactor once nested types, imports, and collections make
string assembly painful.

### Current limitations

- Schema support: `object` (including **nested** objects), **arrays** (of scalars or objects), scalar
  properties (`string`, `integer`), and **`$ref`/`definitions`** references (including recursive
  ones). The real FHIR R4 schema is handled end to end; pick a resource out of its `oneOf` root with
  the `$type` directive (`"$type": "Patient"`).
- The Java code-generation path does not yet emit a mother, and its builders are dynamic, not typed.
- Everything runs in memory at runtime; there is no build-time plugin and no CLI/service yet.

## Development

The project is fully test-driven (ATDD: a failing acceptance test precedes any implementation; the
test is the living specification). Build and test with the Maven wrapper:

```bash
./mvnw test
```

Requires a JDK 26 toolchain (the in-memory compiler needs a JDK, not just a JRE).
The Maven wrapper downloads Maven itself on first run.
