# Direction: core model + adapters

Status: **direction agreed (brainstorm, 2026-06-04)** — not yet implemented.

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

1. Declarative mother → test data (declarative twin of today's builder test) — **start here**.
2. Mother inheritance (`aFoodProduct` builds on `aProduct`).
3. Mandatory fields randomized when not set.
4. Mother validation against schema (foundation for migration).
5. Data validation against schema.
6. Second format (YAML) over the same core — proves adapter separation.
7. Java export of a mother — proves the "two equal ways".
8. Migration = slices 4 + 5 against a changed schema, with a diff report.
9. Delivery (CLI/service) as a thin shell.

## Proposed first acceptance test (awaiting RED implementation)

> **Intent:** As a user I want to define a mother declaratively (against a JSON schema) and
> generate test data from it, so I can assemble test data without writing Java.
>
> **Scenario:**
> - **Given** a schema for `Person` (`name: string`, `age: integer`)
> - **And** a declarative mother `alice` setting `name = "Alice"` and `age = 30`
> - **When** I generate test data from mother `alice`
> - **Then** the result is `{ "name": "Alice", "age": 30 }`

Deliberately small: no inheritance, no randomization, no format choice (JSON input for the
start, which is already parsed). Introduces just one new concept — the declarative mother as
input and "test data" as output — establishing the core model.
