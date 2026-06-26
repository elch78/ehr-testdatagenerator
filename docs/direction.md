# Direction: core model + adapters

Status: **in progress (2026-06-26)** — the declarative core (incl. the **Datasets** input, nested
objects, mother composition, **arrays** and now **`$ref`/`definitions`** resolution), both input
formats, validation, migration and the **CLI** shell are implemented. Validation now runs on the
**networknt** JSON Schema validator (nested + type validation included). The generation rework is
**done**: the generator renders only what the mother sets (no auto-fill) and an explicit `$random`
directive fills a field with a schema-typed random value. The **real** FHIR R4 `fhir.schema.json` now
drives a Patient end to end (generate + validate), via a `$type` directive that picks the resource out
of the schema's `oneOf` root. Java export (slice 7) is still open — see **Upcoming** below.

## Goal

Grow testdatagenerator toward a **CLI and/or web service** where users:

- get builders and mother objects provided,
- define their **own** mother objects,
- assemble test data themselves.

Two parts the user flagged as important: the **interface** (how mothers/test data are
defined) and **migration** (what happens when the schema changes).

## Architecture: a format/output-independent core model

Everything hangs off one core model; inputs and outputs are adapters around it.

```
        Inputs (parsers)                  CORE MODEL                Outputs (renderers)
   ┌──────────────────────┐                                     ┌──────────────────────┐
   │ JSON Schema           │──┐                               ┌─│ Test data (JSON)      │
   │ Mother in YAML        │──┤                               │ │ Java builders + mother│
   │ Mother in JSON        │──┼──► Schema + Mother + Datasets ─┼─│ (export)              │
   │ Mother in Java        │──┤    (types)  (defaults, (which  │ └──────────────────────┘
   │ Datasets (which data) │──┘            inherit.,  datasets)
   └──────────────────────┘               random)       │
                                                   │
                                                   ▼
                                              Validators
                                   ┌──────────────────────────┐
                                   │ Mother valid vs schema?   │
                                   │ Test data conforms?       │
                                   └──────────────────────────┘
```

### Core concepts

- **Schema** — the type shapes (today parsed ad-hoc in `JavaSource`).
- **Mother** — named partial defaults for a schema type: fixed field values + a reference
  to a parent mother (inheritance) + fields explicitly randomized via `$random`. Defined
  **format-independently**: the same concept whether expressed in YAML, JSON, or Java.
  This is the declarative equivalent of `aFoodProduct()` building on `aProduct()`.
- **Datasets** — *which* datasets the user wants. Not a single mother selection: a **list of
  mother invocations**, each a mother reference plus optional overrides, where an override is
  either a scalar **or another (nested) mother invocation**. This is the declarative mirror of
  composing object mothers in test code —
  `anOrder().customer(aCustomer().name("Bob")).items(aLineItem().quantity(3), aLineItem())`.
  `count` is unnecessary: to get three datasets you list three invocations.

### Key consequences

1. **Mother is a format-independent concept** — otherwise the paths are not *equal*.
2. **Code generation is just one output adapter.** Today's
   `JavaSource → InMemoryCompiler → GeneratedType` stops being the core and becomes one
   renderer alongside "emit test-data JSON".
3. **Migration falls out of the model.** Migration = run the validators on existing
   artifacts against the changed schema (diff + report). Validating mothers and validating
   test data is the same machinery applied twice — not separate features.

## Decisions

- Declarative (JSON/YAML) path and Java code-gen path are **two equal ways**. Compiler
  validation in Java is treated as equivalent to validator-based validation in the
  declarative path.
- **Multiple mother formats** (YAML/JSON/Java) over one shared internal model.
- **Migration** = when the schema changes, validate **both** the user's mothers **and**
  their existing test data against the new schema. One capability, not two.

## ATDD slice order (thin vertical slices; build the model first, CLI/service last)

1. ✅ Declarative mother → test data — `GenerateTestDataFromMotherTest`.
2. ✅ Mother inheritance via `$extends` — `MotherInheritanceTest`.
3. ↩️ ~~Mandatory fields randomized when not set~~ — **REVERSED & DONE (2026-06-25).** The generator
   renders only the mother (no auto-fill); an unset field is omitted and `generate()` never throws —
   completeness is the **user's** responsibility (base mother + `$extends`). Filling on demand is the
   explicit `$random` directive — `GenerateRendersOnlySetValuesTest` + `RandomDirectiveTest`.
4. ✅ Mother validation against schema — `ValidateMotherTest`.
5. ✅ Data validation against schema — `ValidateTestDataTest`.
6. ✅ Second format (YAML) over the same core — `DefineMotherInYamlTest`.
7. ⬜ Java export of a mother — proves the "two equal ways". Deferred; open question is
   source-text generation vs. compile-and-run equivalence.
8. ✅ Migration = slices 4 + 5 against a changed schema — `MigrationTest`.

The **Datasets** concept (which datasets to generate) surfaced while scoping delivery: generating
test data is not "pick one mother", it is composing a list of (possibly nested) mother invocations.
That is core-model work and comes before the delivery shell:

9. ✅ **Flat datasets** — the wanted datasets are a list of mother invocations, each a mother
   reference plus scalar overrides, producing several datasets at once — `GenerateDatasetsTest`.
10. ✅ **Nested objects in the schema** — a property may be an object with its own properties and
    required fields; generation recurses, rendering only what the mother sets — `NestedObjectSchemaTest`.
11. ✅ **Nested mother invocations** — a field may reference another mother via `$mother`, resolved
    as a base with sibling overrides; the same machinery as a dataset invocation, one level deeper,
    so datasets compose as a tree — `ComposeMothersTest`.
12. ✅ Delivery (CLI), not a web service — a thin, in-process testable shell over the declarative core.
    First built as `Cli.run(--schema/--mothers/--datasets)` taking the three inputs as explicit file
    flags; that variant was **removed** in favour of slice 13 once the directory convention proved the
    more practical single way in.
13. ✅ Directory convention — `Cli.generate(dir)` discovers inputs by layout: `schema.json`, a
    `mothers/` directory (all files merged into one mother namespace; a duplicate mother name across
    files is an error), and a `datasets/` directory; each datasets file yields one output written to
    `out/<basename>.json`. So a user drops files in a directory and gets one output per dataset file —
    `GenerateFromDirectoryTest`. This is now the **only** CLI entry point.

## Upcoming

Validation was rebuilt on the **networknt** JSON Schema validator (replacing the hand-rolled
existence checks). This brought, for free:

- ✅ **Nested validation** — properties inside nested objects are validated, not only top-level.
- ✅ **Type validation** — a value whose type does not match the schema is now reported.
- Two product rules: unknown properties are rejected by default (`additionalProperties:false`
  injected unless the schema opts out with `true`); mothers are validated as **partial** (checked
  against a `required`-stripped schema, so a missing mandatory field is not faulted).

Still planned, in order:

1. ✅ **Generation reworked: no auto-fill.** The generator only renders the mother's values; an unset
   field is omitted (even if mandatory) and `generate()` does not throw — completeness is the user's
   responsibility. The explicit, user-invoked **`$random`** directive (`{ "$random": {} }`) draws a
   value of the property's schema type (string / integer / `format: date`), prefixing a plain string
   with the field name (`street-7f3a9c`) for traceability and stripped from mother validation as a
   directive. Reworked `NestedObjectSchemaTest`, replaced `RandomizeMandatoryFieldsTest` with
   `GenerateRendersOnlySetValuesTest`, added `RandomDirectiveTest`. (Future: a `prefix` option on the
   `$random` object for richer semantic-carrying values.)
2. ✅ **Arrays** — schema, generation and validation support for `array` properties: an array of
   scalars passes through, an array of objects renders each element by the same rules (so `$mother`
   composition works inside an array), and the schema transforms recurse into `items` so an unknown
   property inside an element is rejected — `ArraySchemaTest`.
3. ✅ **`$ref` / `definitions` resolution** — the generator follows a `$ref` to the type it names,
   for a direct property and for an array's items, so a schema whose types are factored into
   `#/definitions/...` (as the real FHIR schema is) generates. The schema transforms walk through
   refs too (one `walk`, shared by the strict/partial passes), so `additionalProperties:false` and
   `required`-stripping reach referenced types; a ref is followed at most once, so a recursive schema
   terminates — `RefSchemaTest`.
4. ✅ **Real FHIR example** — the **real**, unmodified FHIR R4 `fhir.schema.json` (vendored as a test
   resource) drives a **Patient** end to end: define a mother, generate, validate against the official
   schema — `FhirPatientExampleTest`. This forced three additions:
   - **`$type`** directive — names the resource to build (`"$type": "Patient"`), pointing generation
     at `#/definitions/Patient`, because the FHIR root is a `oneOf` over every resource rather than a
     single type. It travels in the JSON (like `$mother` / `$random` / `$ref`), so the CLI carries it;
     it is not a schema property, so it never appears in the output.
   - **Type-less property schemas** — FHIR describes values with `const` / `enum` / a bare `$ref` and
     no `type`; `isObject` / `isArray` are now null-safe and treat those as scalars.
   - **Validator tolerance for the FHIR schema's quirks** — it declares Draft 6 but uses the draft-04
     `id` keyword and an OpenAPI `discriminator`. The registry keeps the full standard dialect set
     (default Draft 2020-12 for our own `$schema`-less schemas) and overlays only the Draft 6 dialect
     with a tolerant variant that keeps `discriminator` as an annotation and redeclares `id` as
     non-validating.

⬜ **Java export of a mother** (slice 7 above) remains open and independent of the list above.

Possible follow-ups surfaced by the FHIR work: a `prefix` option on `$random`; stripping `$type` in
mother validation (today only generation ignores it); a thin `type("Patient")` Java convenience that
just injects `$type`.

⬜ **Override ergonomics for arrays.** Today an override (`{ $mother: X, field: new }`) **replaces** a
field wholesale — there is no deep merge. This works at any depth (an array element can itself be
`{ $mother: ..., field: new }`, so a sub-mother is reused with one property changed), but for an array
it means restating the whole list: you cannot append an element or tweak one element in place. FHIR is
array-heavy (`name[]`, `address[]`, `category[]`, `entry[]`, `component[]`), so this is felt. Surfaced
by the `examples/fhir` work (see `datasets/observations.yaml`). Option to explore: an array
merge/append directive or a path-based override — design as its own ATDD slice. For now the idiomatic
answer is composing small named sub-mothers rather than deep editing.

⬜ **Deliberately invalid data (negative test data).** Today everything aims at *valid* generation;
producing data that intentionally breaks the schema — to test that a system under test rejects bad
input — is not modelled anywhere. It fits the existing machinery: a violation directive (analogous to
`$random`, e.g. `$invalid`) or a mother/dataset flag could target one rule — wrong `type`, value
outside `enum`, `pattern` break, `additionalProperties` violation, missing `required` — and, because
we already have `validateData` + `Validation`, each generated violation could be **paired with the
validator error it provokes** (generate broken data *and* know exactly which rule it breaks — the same
"one machinery, applied twice" idea as migration). Design question: *generate-then-mutate* (take a
valid datum, inject a known violation) vs. a declarative violation directive in the mother. Own ATDD
slice.

⬜ **Terminology binding (FHIR) — built as a plugin.** The JSON Schema we drive carries no
machine-readable terminology bindings: `Coding.system`/`Coding.code` are free strings, value sets
appear only in description prose, and the few `enum`s are just the fixed *required* bindings the FHIR
publisher inlined (`gender`, `status`, `Bundle.type`). Real bindings live in the StructureDefinitions
and resolve against a **terminology server** (`$expand`, `$validate-code`). Two integration points:
- **Generation** — a directive (analogous to `$random`, e.g. `$code`/`$valueset`) draws a real,
  bound code from a ValueSet expansion (e.g. LOINC `29463-7` for body weight) instead of a bare `text`.
- **Validation** — terminology validation (`$validate-code`) as an **extra validator layer** beyond
  structural JSON Schema. It is also the natural producer of the negative case above: a code outside
  the bound value set is structurally valid but terminologically wrong — a violation networknt cannot
  see.

This is **FHIR-specific** and **external** (network), so it must **not** sit in the schema-agnostic
core: build it as an **optional plugin/adapter** behind a small resolver interface, with offline,
pre-expanded ValueSets as the default (cached local resources, pluggable to a live server such as
`tx.fhir.org`) so the in-memory property is preserved when the plugin is absent. Own ATDD slice;
depends on a plugin seam existing first.
