# testdatagenerator

A Java library for producing JSON test data, built around the **builder + object-mother**
pattern many developers already use in their tests — but reusable beyond the test source set,
and for arbitrary, schema-defined shapes.

## Why

The builder + object-mother pattern gives test data two qualities that are hard to get otherwise:

- **Focus** — a test sets only the fields relevant to its scenario; everything else is a sensible
  (often randomized) default supplied by the mother method.
- **Readability** — `aPerson().name("Alice")` reads like intent, not like data plumbing.

This library keeps that ergonomics and adds two things:

1. A JSON serialization mechanism, so any built object can become JSON in one call.
2. Code generation from a JSON schema, so the builder for a given shape can be **generated**
   instead of hand-written.

## Status

Early, test-driven. Two capabilities exist end-to-end today:

| Capability | Entry point | Feature spec |
|------------|-------------|--------------|
| Build a domain object and serialize it to JSON | `Json.toJson(object)` | [`features/build-object-as-json.feature`](features/build-object-as-json.feature) |
| Generate a working builder from a JSON schema | `Generator.from(schema).compile()` | [`features/generate-builder-from-schema.feature`](features/generate-builder-from-schema.feature) |

## Usage

### Serialize a hand-written builder to JSON

```java
String json = aPerson()
        .name("Alice")
        .age(30)
        .toJson();
// {"name":"Alice","age":30}
```

`Person`, its `Builder`, and the `aPerson()` mother method are written by the library user —
exactly as in a test fixture. The library supplies the serialization (`Json.toJson`).

### Generate a builder from a JSON schema

```java
String schema = """
        {
          "type": "object",
          "title": "Person",
          "properties": {
            "name": { "type": "string" },
            "age":  { "type": "integer" }
          }
        }
        """;

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

| Class | Responsibility |
|-------|----------------|
| `Json` | Serializes any object to JSON (Jackson). |
| `Generator` | Entry point: `from(schema).compile()`. |
| `JavaSource` | Turns a JSON schema into Java record source code. |
| `InMemoryCompiler` | Compiles source to a loaded class via the JDK compiler, without touching disk. |
| `GeneratedType` | Wraps the compiled type; exposes a dynamic builder. |

Code generation deliberately uses no external code-generation library yet. A source-emitting
helper such as JavaPoet is a planned refactor once nested types, imports, and collections make
string assembly painful.

### Current limitations

- Schema support: `object` with scalar properties (`string`, `integer`) only.
- No `required`/optional handling, no nested objects, no arrays.
- Generation happens in memory at runtime; there is no build-time plugin yet.
- Generated builders are dynamic, not typed.

## Development

The project is fully test-driven (ATDD: a feature spec and a failing acceptance test precede any
implementation). Build and test with the Maven wrapper:

```bash
./mvnw test
```

Requires a JDK 26 toolchain (the in-memory compiler needs a JDK, not just a JRE).
The Maven wrapper downloads Maven itself on first run.
