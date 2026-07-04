# Open an NCR and walk a disposition trail

When a process step fails — a weld won't pass NDT, a layup ply has porosity,
a hot-fire run trips an anomaly threshold — you need to **open a Non-Conformance
Report (NCR)** and trail it through to a disposition that satisfies an EN 9100
auditor. Shepard treats this as a first-class flow.

> **Who can do this?** Setting the quality-engineering statuses
> (`NCR_OPEN`, `ON_HOLD`, `REJECTED`, `CERTIFIED`, `CONCESSION_PENDING`)
> requires the **`quality-engineer`** role in your Keycloak realm. Anyone
> can read a DataObject that carries one of these statuses.

## 1 — Open the NCR

Pick the DataObject for the failing process step (e.g. `bridgewelding/AF_3`).
On its detail page:

1. Click the status chip next to the entity name.
2. Choose **NCR Open** from the dropdown.
3. Save.

The status chip turns into a red "NCR Open" badge with the alert-octagon icon.
The audit trail (Activity log) records the transition.

## 2 — Create the repair / re-test DataObject

Create a new DataObject for the repair attempt (e.g. `bridgewelding/AF_3/Run_9`).
On the **typed-predecessor** picker, select the original failing step
and set the relationship type:

| Pick this | When the new DataObject is… |
|---|---|
| **repairs** (`fair2r:repairs`) | A rework attempt — the physical artefact is being re-worked. |
| **revision of** (`prov:wasRevisionOf`) | A re-test of the same artefact (no rework). |
| **concession** (`fair2r:concession`) | Documenting that the original was accepted "use-as-is". |

The link renders in the provenance graph with a small chip ("repairs", "revision of",
"concession") so an auditor sees the rework loop without reading attribute strings.

## 3 — Decide the disposition

When the disposition is being decided (but not yet approved), move the
original NCR DataObject to **Concession Pending** (amber-outlined badge
with shield-alert icon). This signals to the team that an approver review
is in flight.

Attach a **Disposition record** as a Structured Data reference. Use the
built-in template **"Disposition record"** when creating the
StructuredDataContainer — it pre-fills the EN 9100 §8.7 fields:

- `ncr_id` — your site's NCR identifier (e.g. `NCR-2026-0042`).
- `defect_type` — controlled-vocabulary defect class.
- `disposition` — one of `use-as-is`, `rework`, `scrap`, `concession`.
- `approver_orcid` — the approver's ORCID (preferred).
- `approver_username` — the Shepard username of the approver.
- `decided_at` — ISO 8601 timestamp of the decision.
- `notes` — free-form markdown (evidence pointers, ECR refs).

## 4 — Close the trail

Once the disposition is approved:

- **Accepted (with or without concession)** → move the NCR DataObject to
  **Certified** (green badge). The successor / re-test DataObject takes
  whichever lifecycle status its own evidence warrants (often `READY`).
- **Rejected (scrap)** → move the NCR DataObject to **Rejected** (red
  outlined badge). The downstream chain stops here.

## 5 — Audit the trail

Two queries answer "what happened to NCR-2026-0042":

**REST**

```bash
# All DataObjects currently in the quality branch.
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "https://<host>/v2/collections/{cid}/data-objects?status=NCR_OPEN"
```

**Cypher** (instance admin):

```cypher
// All NCR DataObjects in the collection, with their rework chains.
MATCH (n:DataObject)
WHERE n.status IN ['NCR_OPEN', 'ON_HOLD', 'CONCESSION_PENDING', 'REJECTED']
OPTIONAL MATCH (n)<-[:HAS_SUCCESSOR*]-(repair:DataObject)
RETURN n.name AS ncr, n.status, collect(repair.name) AS chain
ORDER BY n.name;
```

## See also

- [DataObject reference](../reference/data-objects.md#quality-lifecycle-statuses-mfg1--qm1a) —
  the full status vocabulary + typed-predecessor relationship table.
- [Provenance reference](../reference/provenance.md) — how the Activity log
  records each status transition.
- `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-3` — the
  feature gap analysis that motivated the QM1a/b/c family.
