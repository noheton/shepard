// INTENTIONALLY EMPTY — no schema change required.
//
// Feature B ships an optional `heroImageUrl` String property on `:Collection`
// nodes. Neo4j is schema-less, so additive nullable properties need no
// migration: existing `:Collection` nodes simply lack the property and
// Spring Data Neo4j OGM reads the absence as `null`.
//
// Operator runbook: no action required. Existing Collections continue to work
// unchanged; the banner is shown only when `heroImageUrl` is set via the
// PATCH /v2/collections/{appId} endpoint or the Collection edit dialog.
