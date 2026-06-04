# Direction: core model + adapters

Status: **in progress (2026-06-04)** — the declarative core, both input formats, validation and
migration are implemented; Java export and delivery remain. See the slice checklist below.

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
        Inputs (parsers)               CORE MODEL                 Outputs (renderers)
   ┌──────────────────────┐                                  ┌──────────────────────┐
   │ JSON Schema           │──┐                            ┌─│ Test data (JSON)      │
   │ Mother in YAML        │──┼──►  Schema  +  Mother   ───┼─│ Java builders + mother│
   │ Mother in JSON        │──┤     (types)   (defaults,    │ │ (export)              │
   │ Mother in Java        │──┘               inheritance,  └─└──────────────────────┘
   └──────────────────────┘                   random fields)
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
3. ✅ Mandatory fields randomized when not set — `RandomizeMandatoryFieldsTest`.
4. ✅ Mother validation against schema — `ValidateMotherTest`.
5. ✅ Data validation against schema — `ValidateTestDataTest`.
6. ✅ Second format (YAML) over the same core — `DefineMotherInYamlTest`.
7. ⬜ Java export of a mother — proves the "two equal ways". Deferred; open question is
   source-text generation vs. compile-and-run equivalence.
8. ✅ Migration = slices 4 + 5 against a changed schema — `MigrationTest`.
9. ⬜ Delivery (CLI/service) as a thin shell. Needs the CLI-vs-service decision.

Possible follow-up to 4/5: **type** validation (a field's value type does not match the schema
type), which existence-only validation does not yet catch.
