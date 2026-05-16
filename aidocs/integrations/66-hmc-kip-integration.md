# 66 — HMC Kernel Information Profile integration

**Status.** Design — ready for slice planning.
**Snapshot date.** 2026-05-13.
**Audience.** Contributors implementing PID-resolution surfaces;
operators wondering how shepard's data shows up in cross-Helmholtz
PID resolvers.

**Originating items.** User request 2026-05-13 (KIT publication
`10.5445/IR/1000173746` / HMC doc `10.3289/HMC_publ_03`). Couples
to `aidocs/workflows/64-provenance-architecture.md` §4, `aidocs/31-rocrate-export.md`,
`aidocs/63` ADR-0004 (PROV-O choice), and the future `aidocs/67`
Unhide-publish plugin.

---

## 1. What HMC KIP is, in one paragraph

The Helmholtz Metadata Collaboration **Kernel Information Profile**
(KIP) defines the minimum set of properties that **every PID
record** must carry inside the HMC ecosystem. It's the
"dereferencing contract" — anyone who resolves a Handle/DOI minted
under the HMC scheme gets back a predictable shape (digital-object
type, creation date, locator, checksum, license, related identifiers).
The aim is **cross-community findability** without forcing
everyone onto one rich ontology. KIP doesn't describe research
activities; it describes *the existence of a digital object*.

## 2. Where it sits relative to PROV1 / metadata4ing / Unhide

| Layer | What it answers | shepard surface |
|---|---|---|
| **PROV1** (`aidocs/55`, shipped) | *Who did what when on this shepard instance?* | `:Activity` Neo4j entity + `/v2/provenance/*` |
| **metadata4ing** (preseed via ONT1b; PROV1h content-neg planned) | *What method, what tool, what investigated object, what units?* | `Accept: application/ld+json; profile="…/metadata4ing/"` on `/v2/provenance/*` |
| **HMC KIP** (this doc) | *Given a PID, what kind of digital object is it and where do I get more?* | New `POST /v2/.../publish` mints a PID carrying a KIP record |
| **Unhide harvest** (`aidocs/67`) | *Find this object across all Helmholtz centres.* | `GET /v2/unhide/feed.jsonld` — m4i-flavoured feed Unhide pulls on schedule |

PROV1 → metadata4ing is **describe-richer**; metadata4ing → KIP
is **summarise-for-resolution**; KIP → Unhide is **expose-for-discovery**.
The four layers compose: a researcher dereferences a KIP-shaped
PID, follows the `locator` to shepard, gets back a metadata4ing
JSON-LD body (PROV1h), drills down into the live PROV1 activity
trail if they want.

## 3. The KIP fields, mapped to shepard

Per the HMC publication, the required + recommended KIP fields and
their shepard sources:

| KIP field | Required | shepard source |
|---|---|---|
| `digitalObjectType` | yes | `Collection` → `dlr:Collection`; `DataObject` → `dlr:DataObject`; `FileReference` → `dlr:File`; `FileBundleReference` → `dlr:Dataset`; `LabJournalEntry` → `dlr:LabJournalEntry`. Authoritative type IRI list lives in a new `aidocs/66-types.md` sub-table (placeholder; written when the slice ships). |
| `digitalObjectLocator` | yes | Fully-qualified URL to the v2 endpoint that returns the entity, e.g. `https://shepard.example.dlr.de/v2/collections/{appId}` |
| `dateCreated` | yes | Audit-trail `createdAt` from `BasicEntityIO` |
| `dateModified` | recommended | `updatedAt` from `BasicEntityIO` |
| `checksum` | recommended | For file-shaped objects only: `ShepardFile.md5` (already on the wire). Container-shaped objects (DataObject / Collection) emit `null` per the KIP "if applicable" semantics. |
| `policy` | recommended | License string from `Collection` if set; otherwise the instance default from a new `shepard.publish.default-license` config key. |
| `digitalObjectMutability` | recommended | `mutable` for live entities; `immutable` once snapshotted via `aidocs/41` V2 snapshot. |
| `referencedBy` / `references` | optional | Outbound: any `:Activity` rows that `prov:used` or `prov:generated` this object. Inbound: any rows pointing AT it. Derived at publish time. |
| `keywords` | optional | `BasicEntity.attributes["keywords"]` if present; otherwise empty. |

The shapes line up cleanly because PROV1 already captures the
identity, creation date, locator, and lineage shepard would emit;
KIP is essentially a *projection* of what shepard already knows.

## 4. The publish surface

### 4.1 `POST /v2/{kind}/{appId}/publish`

Mints a PID and returns the KIP record. `kind` is one of
`collections`, `dataobjects`, `bundles`, `files`,
`lab-journal-entries`.

**Auth.** Caller must hold **Manage** on the parent Collection
(or on the entity itself for top-level Collections).

**Body** (optional):
```json
{
  "pidScheme": "handle" | "doi",       // default 'handle'
  "license": "CC-BY-4.0",              // overrides defaulted
  "keywords": ["foo", "bar"]           // overrides Collection.attributes.keywords
}
```

**Response.** RFC 7807-shaped on errors; KIP JSON on success:
```json
{
  "pid": "21.11125/abcd-efgh",
  "kipRecord": {
    "digitalObjectType": "http://shepard.dlr.de/types/dlr:Collection",
    "digitalObjectLocator": "https://shepard.example.dlr.de/v2/collections/01HF…",
    "dateCreated": "2026-01-15T10:23:00Z",
    "dateModified": "2026-05-13T08:11:00Z",
    "checksum": null,
    "policy": "CC-BY-4.0",
    "digitalObjectMutability": "mutable",
    "referencedBy": [],
    "references": [],
    "keywords": ["foo", "bar"]
  }
}
```

**Side effects.** Stamps `pidScheme`, `pid`, `publishedAt`,
`publishedBy` properties onto the entity. Fires a
`PermissionsChangedEvent`-shape audit (PROV1a captures it as a
`Publish` activity). Idempotent — second call for an already-published
entity returns the existing record without re-minting.

### 4.2 `GET /v2/.well-known/kip/{pid-suffix}`

The dereferencing endpoint that resolves a PID-suffix back to its
KIP record. Public (`@PermitAll`). Returns 404 if the PID isn't
known to this instance.

**Use case.** A Helmholtz PID resolver gets a query for
`21.11125/abcd-efgh`, the registered locator points at
`https://shepard.example.dlr.de/v2/.well-known/kip/21.11125/abcd-efgh`,
shepard returns the KIP record without authentication.

**Permission posture.** Public by design — KIP records are
findability metadata, not the underlying data. The KIP record's
`digitalObjectLocator` points at the entity URL which **is**
permission-gated; an anonymous resolver gets the KIP record but
hits 401 on the locator if they're not authorised. This mirrors
the AAS `/v2/aas/.well-known/aas-server` posture (`aidocs/52 §4a.5`).

### 4.3 `GET /v2/{kind}/{appId}/kip`

The authoritative KIP-record view for a known entity. Used by
the Unhide feed (`aidocs/67`) and by curious clients walking the
PROV1h activity trail.

Permission-gated (Read on the parent Collection).

## 5. PID-minting integration

Three minter shapes considered:

| Option | Notes |
|---|---|
| **(a) Built-in mock minter** | `shepard.publish.minter=mock` (default). Generates `21.shepard/<appId>` as a synthetic Handle. Useful for dev / casual installs. No external dependency. |
| **(b) ePIC handle service** | `shepard.publish.minter=epic` + creds. Mints real Handles via the ePIC REST API (the standard Helmholtz path). |
| **(c) DataCite REST** | `shepard.publish.minter=datacite` + creds. Mints DOIs. Useful when an instance is publishing journal-paper-shaped data. |

Pick **all three** behind a `Minter` interface — small adapter
seam mirroring `aidocs/16` G1b's `GitAdapter` shape. Operators
pick at install time; the in-process behaviour is identical.

## 6. Storage

New nullable properties on `BasicEntity` (or whichever common
ancestor is right):

```java
@Property private String pid;            // e.g. "21.11125/abcd-efgh"
@Property private String pidScheme;      // "handle" | "doi" | "mock"
@Property private Date   publishedAt;
@Property private String publishedBy;    // username
```

V## migration adds the four properties; backfill not needed (all
nullable; pre-PID rows simply don't have them).

Index on `pid` for the `.well-known/kip/{pid-suffix}` lookup.

## 7. Phasing

| Slice | What it ships | Size |
|---|---|---|
| **KIP1a-baseline** | Property addition + V## migration + `POST /v2/{kind}/{appId}/publish` + `GET /v2/{kind}/{appId}/kip` + mock minter only + `PublishService` + tests | M |
| **KIP1b-resolver** | `GET /v2/.well-known/kip/{pid-suffix}` + public-endpoint registry entry | S |
| **KIP1c-epic** | ePIC adapter behind the `Minter` interface; config keys; integration test against ePIC staging | M |
| **KIP1d-datacite** | DataCite adapter | M; gated on operator demand |
| **KIP1e-frontend** | Vue "Publish" button on Collection / DataObject panes with the licence picker | S |
| **KIP1f-unpublish** | `DELETE` the publish state — retain the PID record (KIP records are append-only by convention) but mark `digitalObjectMutability: "retired"`. **Open question** — confirm with maintainer. | S |

Recommended order: **KIP1a → KIP1b → KIP1c → KIP1e → KIP1d → KIP1f**.

## 8. Cross-references

- `aidocs/55` PROV1 — provenance-captured publish action.
- `aidocs/64` provenance-architecture — positioning KIP relative to
  PROV-O and m4i.
- `aidocs/67` Unhide plugin — the natural downstream consumer of
  the KIP record (the feed cites the PID + locator).
- `aidocs/31` RO-Crate export — KIP record gets embedded as a
  `Person` / `Organization` / `DigitalObject` entity in the
  RO-Crate manifest when published Collections are exported.
- `aidocs/52` AAS — the `.well-known` posture is borrowed from
  AAS1-well-known.

External:
- [HMC KIP — KIT publication](https://publikationen.bibliothek.kit.edu/1000173746)
- [HMC publication DOI](https://doi.org/10.3289/HMC_publ_03)
- [ePIC handle service](http://www.pidconsortium.net/)
- [DataCite REST API](https://support.datacite.org/docs/api)
