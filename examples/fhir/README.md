# FHIR example

A worked example that drives the **real, unmodified** FHIR R4 schema (`schema.json`, ~3.2 MB, the
published `fhir.schema.json`) through the directory convention and exercises every declarative feature
of the generator. Look at the inputs below and at the generated `out/` to see what each feature does.

## Directory convention

```
examples/fhir/
├── schema.json          # the FHIR schema (one big oneOf over every resource type)
├── mothers/             # all files merged into one mother namespace
│   ├── names.yaml
│   ├── addresses.yaml
│   ├── patients.yaml
│   ├── observations.yaml  # body weight/height/temperature, blood pressure, tumour marker
│   ├── conditions.yaml    # a cancer diagnosis
│   └── bundles.yaml       # minimal, vital-signs panel, oncology case
└── datasets/            # each file -> one output file under out/
    ├── patients.yaml     # reference a mother, override individual fields (incl. inside arrays)
    ├── observations.yaml # how an override behaves per field shape (scalar / object / array)
    └── bundles.yaml      # minimal, vital-signs panel, oncology case, custom inline bundle
```

`Cli.generate(Path.of("examples/fhir"))` parses `schema.json`, defines every mother under `mothers/`,
and writes one output per `datasets/` file to `out/<basename>.json`. The `out/` directory is
git-ignored (a generated artifact) — run the generator to produce it. A sample of what comes out:

```jsonc
// out/patients.json — one Patient per dataset invocation
[
  {
    "resourceType": "Patient",
    "id": "id-b8bf0a",                       // $random: distinct per instance
    "active": true,
    "name": [ { "use": "official", "family": "Doe", "given": ["Jane"] } ],
    "gender": "female",
    "address": [ { "use": "home", "line": ["Unter den Linden 1"], "city": "Berlin", "country": "DE" } ]
  }
  // ... a second female patient with an added birthDate, then two male patients (one active:false)
]

// out/bundles.json — four bundles: minimal, a vital-signs panel, an oncology case, a custom one.
// The oncology case puzzles a patient together with a diagnosis and results:
[
  // ...
  { "resourceType": "Bundle", "type": "collection", "entry": [
      { "resource": { "resourceType": "Patient",     "gender": "female", "...": "..." } },
      { "resource": { "resourceType": "Condition",   "code": { "text": "Malignant neoplasm of breast" },
                      "stage": [ { "summary": { "text": "Stage II" } } ], "...": "..." } },
      { "resource": { "resourceType": "Observation", "code": { "text": "CA 15-3" },
                      "valueQuantity": { "value": 45, "unit": "U/mL" }, "...": "..." } },
      { "resource": { "resourceType": "Observation", "code": { "text": "Body weight" }, "...": "..." } }
  ] }
]
```

## What each feature looks like here

| Feature | Where to see it |
|---------|-----------------|
| **`$type`** — pick a resource out of the schema's `oneOf`-over-everything root | every mother (`$type: Patient` / `Observation` / `Condition` / `Bundle`) |
| **`$type` nested** — a `Bundle.entry.resource` is itself a `oneOf`; the composed mother carries its own `$type` per entry | `mothers/bundles.yaml` → `out/bundles.json` |
| **`$mother` composition** — a field reuses another mother | `patients.yaml` composes `names`/`addresses`; bundles compose patients, observations and a condition |
| **composition in an array element** — `Patient.name` / `entry` are arrays whose elements are `{ $mother: ... }` | `patients.yaml`, `bundles.yaml` |
| **puzzle a bundle together** — reuse a bundle mother, or override `entry` to assemble any set of resources | `datasets/bundles.yaml`: `vitalsBundle`, `oncologyCase`, and a custom inline bundle |
| **a realistic case across resources** — Patient + Condition (cancer diagnosis) + tumour-marker Observation | `oncologyCase` in `bundles.yaml` |
| **`$extends`** — inherit a base mother, state only what differs | `femalePatient` / `malePatient` extend `patient` |
| **`$random`** — a schema-typed random value, fresh per generated instance | `id: { $random: {} }` → each patient in `out/patients.json` gets a distinct, schema-valid FHIR id |
| **`$ref` / `definitions`** | pervasive — the whole FHIR schema is refs (`Patient`, `HumanName`, `Address`, `Reference`, …) |
| **arrays** — of objects and of scalars | `name[]`, `address[]`, `entry[]` (objects); `given[]`, `line[]` (scalars) |
| **nested objects** | `Observation.code` (CodeableConcept), `valueQuantity` (Quantity), `component[]`, `Condition.stage[]`, `subject` (Reference), `Address` |
| **reference a mother, change individual properties** — `{ $mother: X, field: newValue }` | `datasets/patients.yaml` / `observations.yaml`: override `birthDate`, `status`, a whole `valueQuantity`, … |
| **override at any depth** — an array element can itself be `{ $mother: ..., field: new }`, so you reuse a sub-mother and change just one of its properties | `datasets/patients.yaml`: reuse `janeDoe` but change `family`, reuse `berlinHome` but change `city` |
| **override replaces, it does not merge** — for an array that means restating the whole list (no append); this is why arrays favour composing small sub-mothers over deep editing | `datasets/observations.yaml` |
| **render only what is set** — an unset field is omitted, even if the schema marks it required | the first patient has no `birthDate`; nothing is auto-filled |
| **JSON and YAML mixed** | schema is JSON, mothers and datasets are YAML — interchangeable |

Every resource in `out/` validates against the real FHIR schema.

> **Note on `$random`:** `id` is generated randomly, so re-running the generator changes those values —
> the sample above is one run. That is exactly the point of generating several instances from one
> mother: each gets its own random id while sharing the fixed values.
