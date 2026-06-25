# Direction: core model + adapters

Status: **in progress (2026-06-25)** — the declarative core (incl. the **Datasets** input, nested
objects and mother composition), both input formats, validation, migration and the **CLI** shell are
implemented. Validation now runs on the **networknt** JSON Schema validator (nested + type
validation included). Java export (slice 7) is still open. The next focus is reworking generation
(remove auto-fill, add `$random`) and then arrays + a real FHIR example — see **Upcoming** below.

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
  to a parent mother (inheritance) + which mandatory fields are randomized. Defined
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
3. ↩️ ~~Mandatory fields randomized when not set~~ — **REVERSED (2026-06-25).** The generator must
   only render the mother; filling mandatory fields is the **user's** responsibility (base mother +
   `$extends`). Auto-fill is being removed in favour of an explicit `$random` directive — see
   **Upcoming**.
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
    required fields; generation recurses, filling nested mandatory fields — `NestedObjectSchemaTest`.
    (Arrays still open.)
11. ✅ **Nested mother invocations** — a field may reference another mother via `$mother`, resolved
    as a base with sibling overrides; the same machinery as a dataset invocation, one level deeper,
    so datasets compose as a tree — `ComposeMothersTest`.
12. ✅ Delivery (CLI) as a thin shell that takes the three inputs (schema, mothers, datasets) and
    renders the datasets — `GenerateDatasetsFromCliTest`. Decision: **CLI** (in-process testable thin
    shell), not a web service. `Cli.run(args)` is the seam; an executable `main`/packaging is still
    open.

## Upcoming

Validation was rebuilt on the **networknt** JSON Schema validator (replacing the hand-rolled
existence checks). This brought, for free:

- ✅ **Nested validation** — properties inside nested objects are validated, not only top-level.
- ✅ **Type validation** — a value whose type does not match the schema is now reported.
- Two product rules: unknown properties are rejected by default (`additionalProperties:false`
  injected unless the schema opts out with `true`); mothers are validated as **partial** (checked
  against a `required`-stripped schema, so a missing mandatory field is not faulted).

Still planned, in order:

1. ⬜ **Generation reworked: no auto-fill.** The generator only renders the mother's values; an
   unset field is omitted (even if mandatory), and `generate()` does not throw — completeness is the
   user's responsibility. Then add an explicit, user-invoked **`$random`** directive whose value
   type comes from the schema (so `$random` on a `format: date` field yields a date). Rewrites
   `RandomizeMandatoryFieldsTest` / `NestedObjectSchemaTest` to the new spec.
2. ⬜ **Arrays** — schema, generation and validation support for `array` properties (not handled
   anywhere yet).
3. ⬜ **FHIR example** — a realistic end-to-end test driving a trimmed **FHIR Patient** subset
   (id, gender, birthDate, nested name) through schema → mothers → generate → validate. FHIR's
   official schema is JSON Schema Draft-06, heavy on `$ref` + `oneOf` (`value[x]`) + arrays; the
   subset keeps it structural. Proves the validator and generation on a real-world schema.

⬜ **Java export of a mother** (slice 7 above) remains open and independent of the list above.
