---
stage: fragment
last-stage-change: 2026-05-23
---

# S-02 — OpenLineage RunEvent × F(AI)²R × PROV-O: EASA evidence for free

## Synergy

Every Airflow / MLflow task already emits a typed OpenLineage
`RunEvent`. Tag the `producer` URI against the F(AI)²R vocabulary at
**receive time** (in `shepard-plugin-mlops`), and the typed
`:AIActivity` / `:HumanActivity` split — the one EASA Learning
Assurance auditors and EU AI Act Article 50 deployers need — falls
out of the existing PROV-O store with zero additional capture cost.

## Elements (named anchors)

- **Plugin (designed):** `shepard-plugin-mlops` —
  `aidocs/integrations/83-rebar-airflow-integration.md`,
  `aidocs/40 §4` (the OpenLineage receiver + MLflow Tracking poller).
- **Ontology (designed vendor-tier):** F(AI)²R — `aidocs/40 §5`,
  `aidocs/semantics/95-shacl-templates-and-individuals.md` Part 15
  (TPL9a–TPL9h, ~17 days).
- **Ontology (shipped):** PROV-O + metadata4ing (m4i) — N1b/ONT1b in
  `aidocs/40 §6`, captured by `ProvenanceCaptureFilter`.
- **Regulatory frame:** EASA Learning Assurance —
  `aidocs/agent-findings/easa-data-management-learning-assurance.md`;
  EU AI Act Article 50 —
  `aidocs/agent-findings/easa-ai-regulatory-positioning.md`.
- **External:** OpenLineage v2 spec (`RunEvent.producer`,
  `RunEvent.job.facets`, `parentRun` facet).

## Why this is non-obvious

- The mlops integration design (aidocs/83) is positioned as a
  *lineage* feature: "translate OpenLineage events into
  Predecessor/Successor edges." It does not call out the AI-vs-human
  axis at all.
- The F(AI)²R design (aidocs/95 Part 15) is positioned as a
  *manual-curation* feature: a human marks an artefact as AI-touched
  in the UI. It does not call out automated capture from MLOps tools.
- They never meet in the design docs. But every Airflow run already
  has a `producer` URI (e.g. `https://github.com/apache/airflow/...`)
  and every MLflow run already has model-card metadata. The
  AI-vs-human classification is one regex on the producer string at
  receive time.
- The EASA *AI Roadmap 2.0* and the EU AI Act August-2026 deadline
  both demand artefact-level "this was produced by an AI system"
  marks. With the synergy, every existing Airflow DAG that touches
  Shepard becomes an Article-50-compliant evidence pack — no DAG
  author has to learn F(AI)²R.
- The PROV-O ↔ BFO mapping work (Scientific Data 2025) shows the
  community is converging on PROV-O as the upper substrate for
  provenance. OpenLineage's design also explicitly converges on
  PROV-O (the spec authors cite W3C PROV as their grounding). The
  Shepard layer becomes the bridge, not a competing vocabulary.

## Concrete output

### 1. Receiver classification rule

```java
// shepard-plugin-mlops :: OpenLineageReceiver
public class ProducerClassifier {

    private static final Set<String> AI_PRODUCERS = Set.of(
        "openai.com", "anthropic.com", "huggingface.co",
        "mlflow.org/ml-model", "kserve.io", "vllm.ai",
        "sagemaker", "vertex-ai", "azure-ml"
    );

    private static final Set<String> DAG_PRODUCERS = Set.of(
        "airflow.apache.org", "prefect.io", "dagster.io",
        "argo-workflows", "kubeflow", "metaflow"
    );

    public ActivityKind classify(URI producer, JobFacets facets) {
        if (facets.hasModelFacet()) return ActivityKind.AI_INFERENCE;
        if (facets.hasTrainingFacet()) return ActivityKind.AI_TRAINING;
        if (matchesAny(producer, AI_PRODUCERS)) return ActivityKind.AI_TOOL;
        if (matchesAny(producer, DAG_PRODUCERS)) return ActivityKind.HUMAN_PIPELINE;
        return ActivityKind.UNKNOWN;
    }
}
```

### 2. F(AI)²R-typed `:Activity` shape

```ttl
@prefix prov:   <http://www.w3.org/ns/prov#> .
@prefix m4i:    <http://w3id.org/nfdi4ing/metadata4ing#> .
@prefix fair2r: <https://noheton.github.io/f-ai-r/ns#> .

shp:activity/0192fd01-7000-… a prov:Activity, fair2r:AIActivity ;
    fair2r:hasAgent     shp:agent/openai-gpt-4o-2024-11-20 ;
    fair2r:claimStatus  fair2r:unverified ;
    m4i:realizesMethod  m4i:method/anomaly-detection-v3 ;
    prov:used           shp:dataobject/tr-004-vibration ;
    prov:generated      shp:annotation/anomaly-flag-tr004 ;
    prov:startedAtTime  "2026-05-22T14:31:02Z"^^xsd:dateTime ;
    prov:endedAtTime    "2026-05-22T14:31:08Z"^^xsd:dateTime ;
    prov:wasAssociatedWith shp:run/airflow-dag-anomaly-001 .
```

### 3. SPARQL: extract the EASA evidence pack

```sparql
PREFIX fair2r: <https://noheton.github.io/f-ai-r/ns#>
PREFIX prov:   <http://www.w3.org/ns/prov#>

SELECT ?activity ?agent ?model ?status ?in ?out
WHERE {
  ?activity a fair2r:AIActivity ;
            fair2r:hasAgent      ?agent ;
            fair2r:claimStatus   ?status ;
            prov:used            ?in ;
            prov:generated       ?out .
  OPTIONAL { ?agent  fair2r:realizesModel ?model }
  FILTER (?status != fair2r:human-confirmed)
}
```

Result: every AI-produced artefact currently lacking human
confirmation. This is the exact list an EU AI Act Article 50
deployer must surface; an EASA Learning Assurance auditor uses
the same query with an additional filter on `m4i:realizesMethod` to
trace back to the model card.

### 4. Default-on classification at receive time

The OpenLineage receiver is a CDI bean in the mlops plugin; the
classifier runs synchronously per event. No DAG author touches
F(AI)²R; no UI change is required for the basic flow. Manual
`fair2r:human-confirmed` promotion stays a UI action (the existing
TPL9 verification ladder).

## Real-world use case

**Persona:** a DLR REBAR data scientist running `anomaly-classifier-v3`
(`aidocs/integrations/83 §2`) on LUMEN TR-004 hot-fire data.
Today: the Airflow DAG produces an output, the researcher
manually annotates "we ran the model" in a Shepard note, and
when EASA asks "what produced this annotation?" the answer is a
free-text note. After this synergy: the Airflow OpenLineage event
arrives at Shepard, the receiver classifies the GPT/HuggingFace
producer as `fair2r:AIActivity`, the `:Annotation` node is born
with `fair2r:claimStatus fair2r:unverified`, and the
provenance-export endpoint emits a JSON-LD shape that an EASA
auditor can ingest verbatim.

For PLUTO (DLR satellite mission, Welzmüller et al. 2024
eLib 215120): the same shape covers ground-station automation —
every command an AI agent suggested before a human operator
approved is a typed AI Activity with `claimStatus
unverified` until the operator confirms.

## External evidence

- **OpenLineage *Run Cycle* spec** —
  [openlineage.io/docs/spec/run-cycle](https://openlineage.io/docs/spec/run-cycle/)
  Takeaway: `RunEvent` has explicit `producer` and facet slots; the
  spec is event-based and accommodates external receivers.
- **EU AI Act Article 50 service-desk page (European Commission)** —
  [ai-act-service-desk.ec.europa.eu/en/ai-act/article-50](https://ai-act-service-desk.ec.europa.eu/en/ai-act/article-50)
  Takeaway: providers of AI-generated content must "mark such
  content in a machine-readable manner"; the F(AI)²R-typed
  `:Activity` is exactly that mark.
- ***A semantic approach to mapping the Provenance Ontology to
  Basic Formal Ontology* — Scientific Data, 2025** —
  [s41597-025-04580-1](https://www.nature.com/articles/s41597-025-04580-1)
  Takeaway: PROV-O is now formally aligned to BFO; the four-pillar
  upper-ontology decision (`aidocs/semantics/96`) is consistent with
  the broader provenance community direction.
- ***Provenance Data in the Machine Learning Lifecycle in
  Computational Science and Engineering* — arXiv 1910.04223** —
  [arxiv.org/pdf/1910.04223](https://arxiv.org/pdf/1910.04223)
  Takeaway: PROV-ML demonstrates PROV-O is sufficient for the
  ML-pipeline case; no new ontology is required for the AI-vs-human
  split — only a typed sub-class.

## Effort estimate

**M (medium).** Components:

- `shepard-plugin-mlops` baseline (OpenLineage receiver + Marquez
  bridge) lands first — independently planned, ~3 weeks.
- `ProducerClassifier` rule table (1–2 days; iterative).
- F(AI)²R N1c2 pre-seed (TPL9a from aidocs/95 Part 15, ~3 days).
- Provenance-export endpoint extension to surface the new
  `fair2r:claimStatus` field — additive, 1 day.

Net incremental over the mlops baseline + TPL9a baseline: ~5 days.
The synergy delivers EASA evidence WITHOUT requiring any DAG
author change.

## Risk / counter-evidence

- The classifier is heuristic — producer URIs are not authoritative.
  An honest DAG author can mark a human-only task with
  `producer=mlflow` and falsely become "AI". Mitigation: add a
  facet-based stronger signal (the presence of `JobFacets.model`
  or `JobFacets.training` is the actual ground truth) and treat
  the URI rule as a fallback.
- arXiv 2603.26983 (*Transparency as Architecture*, 2026) argues
  that Article 50 structural compliance gaps persist even with
  machine-readable marks — pure provenance is not enough; UI
  surfacing matters. Mitigation: badges + filters in the UI
  (already on the design roadmap per `feedback_ai_human_collab_provenance`).
- CCIA Article 50 commentary (December 2025) flags that the spec
  is still evolving (Code of Practice publishes ~September 2026).
  The F(AI)²R vocabulary may need version-pinning if the final
  Code introduces a different ontology of choice. Mitigation: keep
  F(AI)²R as a vendor-tier import (per `aidocs/96`) and add a
  thin SKOS adapter to whichever vocabulary the final Code blesses.
