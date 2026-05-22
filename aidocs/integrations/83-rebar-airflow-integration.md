---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 83 — ReBAR / Airflow Integration

**Status:** design
**Audience:** contributors, MLOps operators, data scientists using ReBAR alongside Shepard
**Depends on:** L2d (appId-first v2 surface, shipped), IMP1 (import validate/commit API, shipped),
P10a/P10b (SQL timeseries endpoint, shipped), ANC-1 (predecessor/successor API, shipped),
`shepard-py` SDK (J2b, design — `aidocs/integrations/81`), TS-IDa (timeseries appId backfill, design — soft dependency)
**Snapshot date:** 2026-05-21

---

## 1. Motivation

Shepard is the research data context layer — it records what data exists, which experiment
produced it, and the lineage chain linking every DataObject. ReBAR
(Research Baseline for Reproducible ML, `gitlab.dlr.de/rebar/rebar-infrastructure`) is the
ML computation layer — it runs Airflow DAGs, tracks experiments in MLflow, stores model
artifacts in MinIO, and records OpenLineage events via Marquez.

Today the two systems are separate. A researcher trains a model on LUMEN hotfire sensor data
by exporting CSVs from Shepard, uploading them to ReBAR's MinIO manually, running the
Airflow DAG, and then manually noting the MLflow run URL in a Shepard annotation. The lineage
between input DataObjects and output model artifacts exists only in the researcher's memory.

The integration described here collapses that gap:

- Airflow operators read DataObjects from Shepard as DataFrames and write results back
  as references — no manual export/import.
- Marquez OpenLineage events are translated into Predecessor/Successor edges in the
  Shepard graph automatically, without DAG authors needing to know the Shepard API.
- Shared MinIO wiring lets DAG artifacts land directly in the bucket Shepard already
  manages, so there is no data duplication.

**DLR strategic context.** The 2040 DLR research map positions D1 (Enhanced Machine
Learning via ReBAR) as a key capability fed by D2 (Integrated Data Management via
Shepard). The flow is bidirectional: Shepard supplies ML pipelines with curated,
lineage-tracked training data; trained models and their metrics flow back into Shepard
as first-class references, making them findable and their provenance auditable from
the same UI a researcher uses to browse raw measurements.

---

## 2. Integration Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Keycloak (shared OIDC provider)                                         │
│  realm: shepard-demo   clients: frontend-dev (public)                    │
│                         + marquez-bridge (confidential service account)  │
└────────────────────┬─────────────────────────────────────────────────────┘
                     │ OIDC / API key
        ┌────────────┴──────────────────────────────────────────────┐
        │                    Shepard v2 API                         │
        │  /v2/collections/{appId}/data-objects/{appId}             │
        │  /v2/sql/timeseries  (P10a/P10b streaming query)          │
        │  /v2/import/validate + /v2/import/plans/{commitId}        │
        │  /v2/collections/{c}/data-objects/{do}/predecessors       │
        └───────────┬───────────────────────────────────────────────┘
                    │                         ▲ lineage bridge
        ┌───────────▼──────────────┐    ┌─────┴────────────────────┐
        │  Apache Airflow          │    │  marquez-bridge sidecar  │
        │  shepard-plugin-airflow  │    │  (subscribes to Marquez  │
        │  ┌────────────────────┐  │    │   OpenLineage webhooks)  │
        │  │ ShepardReadOp      │  │    └─────────────────────────-┘
        │  │ ShepardWriteOp     │  │          ▲ RunEvents
        │  │ ShepardProvOp      │  │    ┌─────┴────────────────────┐
        │  └────────────────────┘  │    │  Marquez (OpenLineage)   │
        └───────────┬──────────────┘    └──────────────────────────┘
                    │ XCom DataFrames / artifact keys
        ┌───────────▼──────────────┐
        │  Airflow worker          │    ┌──────────────────────────┐
        │  (user DAG code)         │    │  MLflow                  │
        │  reads DF, trains model, │    │  MLFLOW_S3_ENDPOINT_URL  │
        │  logs to MLflow          │◄───┤  → Shepard/shared MinIO  │
        └──────────────────────────┘    └──────────────────────────┘
```

Three integration modes are described below. They are independent and can be adopted
incrementally — Mode A alone already closes the export/import loop; Mode B adds
automatic lineage; Mode C eliminates artifact duplication.

---

## 3. Mode A — `shepard-plugin-airflow`

A Python package (`shepard-plugin-airflow`) providing three Airflow operators and one
Airflow Connection type. Internally the operators delegate to `shepard-py` (J2b, `aidocs/81 §J2b`)
— there is no second REST client. This keeps the Python API surface consistent with
the Jupyter integration.

Install in the Airflow worker image:

```dockerfile
FROM apache/airflow:2.9
RUN pip install shepard-py shepard-plugin-airflow
```

Authentication is via an Airflow Connection named `shepard_default` stored in the
Airflow secrets backend (Vault, environment variable, or DB with Fernet encryption).
The connection carries a Shepard API key (not a user bearer token). API keys are
managed at `POST /shepard/api/users/{username}/apikeys` (v1 compat surface, shipped).
The key is never in DAG source code.

### A1 — `ShepardReadOperator`

Fetches one or more timeseries channels from a Shepard DataObject and pushes a
pandas DataFrame to Airflow XCom for downstream tasks.

```python
from shepard_airflow import ShepardReadOperator

read_task = ShepardReadOperator(
    task_id="load_tr004_vibration",
    shepard_conn_id="shepard_default",        # Airflow Connection
    collection_app_id="{{ var.value.lumen_collection_app_id }}",
    data_object_app_id="{{ params.data_object_app_id }}",
    timeseries_reference_name="Measurements", # name-based lookup via shepard-py
    # Post-TS-IDa: prefer timeseries_app_id for a single-field channel address.
    # Pre-TS-IDa (today): pass the 5-tuple; deprecated once TS-IDa ships.
    channel_filter=["turbopump_vibration_x", "turbopump_vibration_y"],
    time_from="{{ data_interval_start.isoformat() }}",
    time_to="{{ data_interval_end.isoformat() }}",
    xcom_key="input_df",
)
```

Internally `ShepardReadOperator` calls `POST /v2/sql/timeseries` (P10a/P10b). The
endpoint accepts a `SqlQuerySpec` JSON DSL body — a structured query with `select`,
`from`, `where`, `group_by`, `order_by`, and `limit` fields. The operator assembles
the spec from its parameters; the exact DSL shape is documented in
`aidocs/platform/29-p10-implementation-design.md §2.1` and the `SqlQuerySpec` source
at `backend/src/main/java/de/dlr/shepard/data/timeseries/sql/SqlQuerySpec.java`.

```
POST /v2/sql/timeseries
Content-Type: application/json
Accept: application/x-ndjson   # streaming; fall back to application/json for small windows
```

The response is parsed into a `pd.DataFrame` with a UTC `DatetimeTZDtype` index.
For large windows the operator streams via the NDJSON content type
(`Accept: application/x-ndjson`) and builds the DataFrame incrementally to avoid
loading the full response body into memory.

### A2 — `ShepardWriteOperator`

Writes task output (a model weights file or a metrics dict) back to a Shepard
DataObject as references, using the import validate/commit API (IMP1).

```python
from shepard_airflow import ShepardWriteOperator

write_task = ShepardWriteOperator(
    task_id="write_model_to_shepard",
    shepard_conn_id="shepard_default",
    collection_app_id="{{ var.value.lumen_collection_app_id }}",
    data_object_app_id="{{ params.output_data_object_app_id }}",
    artifacts=[
        # Model weights → FileReference
        {"type": "file", "local_path": "/tmp/model.onnx", "reference_name": "LSTM model v1"},
        # Metrics → StructuredDataReference
        {"type": "structured", "xcom_task_id": "train_task", "xcom_key": "metrics",
         "reference_name": "Training metrics"},
    ],
    mlflow_run_url="{{ ti.xcom_pull(task_ids='train_task', key='mlflow_run_url') }}",
)
```

The operator assembles an import manifest, calls `POST /v2/import/validate` (dry-run),
then `POST /v2/import/plans/{commitId}` to commit. On failure the plan is abandoned
and the task retries cleanly (the validate/commit pattern is idempotent).

The `mlflow_run_url` is attached to the DataObject as an attribute
`mlflow.run_url = "<url>"` so it is visible in the Shepard UI alongside the DataObject.

### A3 — `ShepardProvenanceOperator`

Records the input DataObjects as Predecessors of the output DataObject — closing the
semantic lineage loop without requiring DAG authors to call the Shepard API directly.

```python
from shepard_airflow import ShepardProvenanceOperator

prov_task = ShepardProvenanceOperator(
    task_id="record_lineage",
    shepard_conn_id="shepard_default",
    collection_app_id="{{ var.value.lumen_collection_app_id }}",
    output_data_object_app_id="{{ params.output_data_object_app_id }}",
    input_data_object_app_ids=[
        "{{ params.data_object_app_id }}",
    ],
)
```

Internally calls `POST /v2/collections/{coll}/data-objects/{output}/predecessors` for
each input DataObject.

**Relationship to PROV1a.** `ProvenanceCaptureFilter` (PROV1a, shipped) automatically
creates an `:Activity` node for every `/v2/` API call. `ShepardProvenanceOperator` adds
a `[:PREDECESSOR_OF]` edge between DataObjects — the semantic lineage assertion. These
are complementary: PROV1a captures the audit trail of who called what when;
`ShepardProvenanceOperator` captures what produced what. The `:Activity` nodes
generated by PROV1a also record the `ShepardProvenanceOperator` mutations themselves,
so the full provenance chain is auditable end-to-end.

### Task DAG example — LUMEN anomaly detector retraining

The `dags/pof/pof_dag.py` LSTM flight-phase classifier in ReBAR (`gitlab.dlr.de/rebar/rebar-infrastructure`)
is the upstream reference DAG pattern. The example below follows that structure,
applied to the LUMEN hotfire campaign.

```python
from datetime import datetime
from airflow import DAG
from airflow.operators.python import PythonOperator
from shepard_airflow import ShepardReadOperator, ShepardWriteOperator, ShepardProvenanceOperator

COLLECTION = "{{ var.value.lumen_collection_app_id }}"
TRAINING_DO = "{{ var.value.lumen_training_data_object_app_id }}"  # TR-001..TR-015
OUTPUT_DO   = "{{ var.value.lumen_model_data_object_app_id }}"

with DAG(
    dag_id="shepard_lumen_anomaly_retraining",
    start_date=datetime(2026, 1, 1),
    schedule=None,
    catchup=False,
) as dag:

    load = ShepardReadOperator(
        task_id="load_training_data",
        shepard_conn_id="shepard_default",
        collection_app_id=COLLECTION,
        data_object_app_id=TRAINING_DO,
        timeseries_reference_name="Measurements",
        channel_filter=["turbopump_vibration_x", "turbopump_vibration_y",
                        "chamber_pressure", "nozzle_temperature"],
        xcom_key="training_df",
    )

    def train_lstm(ti, **ctx):
        import pandas as pd, mlflow, json
        df: pd.DataFrame = ti.xcom_pull(task_ids="load_training_data", key="training_df")
        with mlflow.start_run() as run:
            # ... model training ...
            metrics = {"val_f1": 0.94, "anomaly_threshold": 3.2}
            mlflow.log_metrics(metrics)
            mlflow.log_artifact("/tmp/model.onnx")
            ti.xcom_push(key="metrics", value=json.dumps(metrics))
            ti.xcom_push(key="mlflow_run_url", value=run.info.artifact_uri)

    train = PythonOperator(task_id="train_lstm", python_callable=train_lstm)

    write = ShepardWriteOperator(
        task_id="write_model",
        shepard_conn_id="shepard_default",
        collection_app_id=COLLECTION,
        data_object_app_id=OUTPUT_DO,
        artifacts=[
            {"type": "file",       "local_path": "/tmp/model.onnx",   "reference_name": "LSTM anomaly model"},
            {"type": "structured", "xcom_task_id": "train_lstm",       "xcom_key": "metrics",
             "reference_name": "Training metrics"},
        ],
        mlflow_run_url="{{ ti.xcom_pull(task_ids='train_lstm', key='mlflow_run_url') }}",
    )

    prov = ShepardProvenanceOperator(
        task_id="record_lineage",
        shepard_conn_id="shepard_default",
        collection_app_id=COLLECTION,
        output_data_object_app_id=OUTPUT_DO,
        input_data_object_app_ids=[TRAINING_DO],
    )

    load >> train >> write >> prov
```

---

## 4. Mode B — Marquez → Shepard Lineage Bridge

A lightweight sidecar service (`marquez-bridge`) that subscribes to Marquez OpenLineage
events and creates Predecessor edges in Shepard automatically. DAG authors gain
lineage-for-free without knowing the Shepard API.

### How it works

Marquez emits `RunEvent` JSON objects (OpenLineage spec) for each DAG run:

```json
{
  "eventType": "COMPLETE",
  "job": { "name": "shepard_lumen_anomaly_retraining" },
  "inputs":  [{ "name": "shepard://lumen-collection/lumen-training-do" }],
  "outputs": [{ "name": "shepard://lumen-collection/lumen-model-do"    }]
}
```

The bridge listens for `COMPLETE` events, parses the `shepard://` dataset URIs, and
calls `POST /v2/collections/{coll}/data-objects/{output}/predecessors` to create a
Predecessor link for each (input, output) pair.

### Dataset naming convention

```
shepard://{collection_appId}/{data_object_appId}
```

or, for human-readable DAG code:

```
shepard://{collection_name}/{data_object_name}
```

The bridge resolves names to appIds via `GET /v2/collections?name={name}` and
`GET /v2/collections/{coll}/data-objects?name={name}`. Names that do not resolve are
logged as warnings and skipped — the bridge is best-effort; lineage via
`ShepardProvenanceOperator` (Mode A) is the authoritative path.

### Bridge authentication

The bridge runs as a Keycloak confidential service-account client (`marquez-bridge`).
On startup it exchanges its client credentials for a Keycloak bearer token and
presents it as `Authorization: Bearer` on every Shepard v2 call. The Keycloak realm
admin must add a `marquez-bridge` confidential client with:
- `Service Accounts Enabled: true`
- No valid redirect URIs (service account only)
- The Shepard `instance-reader` role mapped to the service account (Read permission
  on all Collections is sufficient for name resolution; Predecessor mutations require
  Write permission on the target DataObject).

Alternatively, the bridge accepts a Shepard API key in the `SHEPARD_API_KEY`
environment variable, skipping the OIDC flow.

### Marquez webhook config

```yaml
# marquez/marquez.yml  (addition)
openlineage:
  http:
    url: http://marquez-bridge:8090/events
```

The bridge exposes `POST /events` (OpenLineage wire format). Marquez pushes to it on
every run completion.

---

## 5. Mode C — Shared MinIO

ReBAR's MLflow artifact store and Shepard's file storage point at the same MinIO
endpoint. DAG artifacts land directly in the bucket Shepard already manages — there
is no re-upload.

### Prerequisite: `POST /v2/files/register-object-key` (new endpoint)

Mode C requires a Shepard endpoint that registers an existing MinIO object key as a
`FileReference` without re-uploading the bytes. This endpoint does not yet exist.

**Proposed shape:**

```
POST /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/file-references/register
Content-Type: application/json

{
  "storageKey": "mlflow/<experiment_id>/<run_id>/artifacts/model.onnx",
  "filename":   "model.onnx",
  "mimeType":   "application/octet-stream",
  "referenceName": "LSTM anomaly model"
}
```

Returns a `FileReferenceIO` with the new reference's `appId`. Gated by Write
permission on the DataObject. The `storageKey` must be within the Shepard-managed
bucket (not an arbitrary S3 path).

This is tracked as **RB2b** in the implementation plan below and is a prerequisite
for Mode C's full operation. Until RB2b ships, Mode C can still register artifacts
by having `ShepardWriteOperator` download from MLflow and re-upload (Mode A path).

### Compose wiring

```yaml
# infrastructure/docker-compose.override.yml  (Mode C addition)
services:
  mlflow:
    image: ghcr.io/mlflow/mlflow:2.12
    profiles: [rebar]
    environment:
      MLFLOW_S3_ENDPOINT_URL: "http://minio:9000"    # Shepard's MinIO
      AWS_ACCESS_KEY_ID:      "${MINIO_ROOT_USER}"
      AWS_SECRET_ACCESS_KEY:  "${MINIO_ROOT_PASSWORD}"
    command: >
      mlflow server
        --backend-store-uri postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/mlflow
        --default-artifact-root s3://shepard-artifacts/mlflow
        --host 0.0.0.0 --port 5000
    networks: [shepard]
    depends_on: [minio, postgres]
    restart: unless-stopped
    ports:
      - "5000:5000"
```

The `shepard-artifacts` bucket must pre-exist in MinIO (Shepard creates it on first
file upload). The `mlflow/` prefix is reserved for MLflow artifacts; Shepard's own
file storage uses separate prefixes. There is no cross-contamination.

---

## 6. MLflow ↔ Shepard Concept Mapping

| MLflow concept | Shepard concept | Notes |
|---|---|---|
| Experiment | Collection | One Collection per research domain. When a project spans multiple MLflow experiments, tag runs with `shepard.collection_app_id` to redirect to the correct Collection. |
| Run | DataObject | Each training run is a DataObject under the experiment Collection. |
| Input dataset | `TimeseriesReference` or `FileReference` | What the model trained on — discovered via Predecessor chain. |
| Run metrics (JSON) | `StructuredDataReference` | `{"accuracy": 0.94, "f1": 0.91, …}` stored as a structured reference named "Training metrics". |
| Model artifact (pkl/onnx) | `FileReference` | The trained model weights file. |
| Run tag | Attribute | Key-value on the DataObject (e.g. `mlflow.run_id`, `mlflow.run_url`). |
| Registered model version | Predecessor chain | Model v2 DataObject is a Successor of model v1 DataObject. |
| MLflow Run URL | Attribute `mlflow.run_url` | Visible in the Shepard DataObject detail panel; links back to the MLflow UI. |

The default mapping (MLflow Experiment → Collection) is the right fit for a single-project
lab. For multi-experiment programmes, DAG authors set the `shepard.collection_app_id`
Airflow Variable (or tag) to override which Collection receives the output DataObject.

---

## 7. Implementation Plan

The task brief named six steps (RB1a–c, RB2a–b, RB3a). This plan expands to eight
by splitting the example DAG (RB1d) from the operators, and splitting the new Shepard
register endpoint (RB2b) from the compose wiring (RB2c), since they have different
blast radii. Original RB3a (operator runbook) is retained as-is.

| Step | Artefact | Effort | Notes |
|---|---|---|---|
| **RB1a** | `ShepardReadOperator` — timeseries fetch via `POST /v2/sql/timeseries` | S | Delegates to `shepard-py`; streaming via NDJSON for large windows |
| **RB1b** | `ShepardWriteOperator` — file + structured data via import API | S | Uses `POST /v2/import/validate` + `/plans/{commitId}` |
| **RB1c** | `ShepardProvenanceOperator` — predecessor links | S | Calls `POST /v2/…/predecessors`; attribute `mlflow.run_url` on the DataObject |
| **RB1d** | Example DAG `shepard_lumen_anomaly_retraining.py` | S | Reads TR-001..TR-015 channels, trains LSTM, writes model + metrics, records lineage |
| **RB2a** | `marquez-bridge` sidecar service | M | Python FastAPI; `POST /events`; Keycloak service-account client; `shepard://` URI resolver |
| **RB2b** | `POST /v2/…/file-references/register` endpoint (register-by-storage-key) | S | New Shepard endpoint; gated by Write; prerequisite for Mode C zero-copy |
| **RB2c** | Shared MinIO compose wiring + MLflow service under `rebar` profile | XS | Compose override + MinIO bucket prefix convention; depends on RB2b for zero-copy |
| **RB3a** | Keycloak `marquez-bridge` client recipe + operator runbook | XS | Realm JSON patch + `infrastructure/rebar/` directory |

RB1a → RB1b → RB1c can be parallelised. RB2a depends only on Shepard's existing
predecessor endpoint (shipped). RB2b is independent. RB2c depends on RB2b.

When any RB step ships, update:
- `aidocs/34-upstream-upgrade-path.md` — operator impact row
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — feature matrix row

Plugin documentation (`plugins/airflow/docs/{reference,quickstart,install}.md`)
must land in the same PR as RB1a per CLAUDE.md "plugins ship their own documentation"
rule.

---

## 8. Compose Integration

Full `rebar` profile bringing up all ReBAR services alongside Shepard:

```yaml
# infrastructure/docker-compose.override.yml  (rebar profile addition)
services:

  airflow-db-init:
    image: apache/airflow:2.9
    profiles: [rebar]
    entrypoint: ["airflow", "db", "migrate"]
    environment:
      AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: "postgresql+psycopg2://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/airflow"
    networks: [shepard]
    depends_on: [postgres]

  airflow-webserver:
    image: apache/airflow:2.9
    profiles: [rebar]
    ports: ["8085:8080"]
    environment:
      AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: "postgresql+psycopg2://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/airflow"
      AIRFLOW__CELERY__BROKER_URL:         "redis://redis:6379/0"
      AIRFLOW__CELERY__RESULT_BACKEND:     "db+postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/airflow"
      AIRFLOW__WEBSERVER__SECRET_KEY:      "${AIRFLOW_SECRET_KEY}"
      AIRFLOW_CONN_SHEPARD_DEFAULT:        "${AIRFLOW_CONN_SHEPARD_DEFAULT}"   # Airflow Connection URI
    volumes:
      - ./rebar/dags:/opt/airflow/dags:ro
    networks: [shepard]
    depends_on: [airflow-db-init, redis]
    restart: unless-stopped

  airflow-scheduler:
    image: apache/airflow:2.9
    profiles: [rebar]
    command: scheduler
    environment:
      AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: "postgresql+psycopg2://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/airflow"
      AIRFLOW__CELERY__BROKER_URL:         "redis://redis:6379/0"
    volumes:
      - ./rebar/dags:/opt/airflow/dags:ro
    networks: [shepard]
    depends_on: [airflow-db-init, redis]
    restart: unless-stopped

  airflow-worker:
    image: local/airflow-shepard:latest   # base apache/airflow:2.9 + shepard-py + shepard-plugin-airflow
    profiles: [rebar]
    command: celery worker
    environment:
      AIRFLOW__DATABASE__SQL_ALCHEMY_CONN: "postgresql+psycopg2://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/airflow"
      AIRFLOW__CELERY__BROKER_URL:         "redis://redis:6379/0"
      MLFLOW_TRACKING_URI:                 "http://mlflow:5000"
      MLFLOW_S3_ENDPOINT_URL:              "http://minio:9000"
      AWS_ACCESS_KEY_ID:                   "${MINIO_ROOT_USER}"
      AWS_SECRET_ACCESS_KEY:               "${MINIO_ROOT_PASSWORD}"
    volumes:
      - ./rebar/dags:/opt/airflow/dags:ro
      - /tmp/airflow-worker:/tmp          # scratch space for model artifacts
    networks: [shepard]
    depends_on: [airflow-scheduler, minio]
    restart: unless-stopped

  mlflow:
    image: ghcr.io/mlflow/mlflow:2.12
    profiles: [rebar]
    ports: ["5000:5000"]
    environment:
      MLFLOW_S3_ENDPOINT_URL:  "http://minio:9000"
      AWS_ACCESS_KEY_ID:       "${MINIO_ROOT_USER}"
      AWS_SECRET_ACCESS_KEY:   "${MINIO_ROOT_PASSWORD}"
    command: >
      mlflow server
        --backend-store-uri postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/mlflow
        --default-artifact-root s3://shepard-artifacts/mlflow
        --host 0.0.0.0 --port 5000
    networks: [shepard]
    depends_on: [minio, postgres]
    restart: unless-stopped

  marquez:
    image: marquezproject/marquez:0.46
    profiles: [rebar]
    ports: ["5001:5000", "5002:5001"]
    environment:
      MARQUEZ_CONFIG: /opt/marquez/marquez.yml
    volumes:
      - ./rebar/marquez.yml:/opt/marquez/marquez.yml:ro
    networks: [shepard]
    depends_on: [postgres]
    restart: unless-stopped

  marquez-bridge:
    image: local/marquez-bridge:latest
    profiles: [rebar]
    environment:
      MARQUEZ_BASE_URL:  "http://marquez:5000"
      SHEPARD_BASE_URL:  "${SHEPARD_BASE_URL}"
      SHEPARD_API_KEY:   "${MARQUEZ_BRIDGE_SHEPARD_API_KEY}"   # or OIDC client credentials
    networks: [shepard]
    depends_on: [marquez]
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    profiles: [rebar]
    networks: [shepard]
    restart: unless-stopped
```

Bring up:

```
docker compose --profile rebar up -d
```

**Notes:**
- MinIO (`minio`) and Postgres (`postgres`) are already in the base Shepard stack.
  The `rebar` profile reuses them; it does not start a second instance.
- Marquez uses its own Postgres schema (`marquez` database). Add
  `MARQUEZ_DB_HOST=postgres`, `MARQUEZ_DB_NAME=marquez`,
  `MARQUEZ_DB_USER`, `MARQUEZ_DB_PASSWORD` to `marquez.yml` and create the
  database with `createdb marquez` before first start.
- The Zoraxy rule for `https://airflow.nuclide.systems → <host>:8085` and
  `https://mlflow.nuclide.systems → <host>:5000` completes the public URL
  routing.

---

## 9. Security and Data Hygiene

- **Airflow → Shepard authentication.** The Airflow Connection `shepard_default` holds a
  Shepard API key stored in the Airflow secrets backend (Vault, environment variable,
  or Fernet-encrypted DB column). The key is never in DAG source code or XCom state.
- **Marquez bridge authentication.** Either a Keycloak service-account client
  (confidential, `marquez-bridge`) exchanging client credentials for a bearer token,
  or a Shepard API key in `MARQUEZ_BRIDGE_SHEPARD_API_KEY`. The bridge holds Write
  permission on the Collections it writes Predecessor links to; it does not require
  admin role.
- **MinIO access scoping.** The Airflow worker's MinIO credentials are scoped to the
  `shepard-artifacts/mlflow/` prefix via a MinIO IAM policy. The worker cannot read or
  write Shepard's own storage prefixes (file uploads, video, etc.).
- **OpenLineage events are metadata only.** The Marquez bridge parses dataset URIs
  and calls Shepard predecessor endpoints. No raw data crosses the bridge. The bridge
  never reads DataObject payloads.
- **MLflow OIDC.** MLflow's web UI can be placed behind Keycloak auth proxy (e.g.
  oauth2-proxy sidecar) using the same realm. This is optional for initial deployment
  but recommended before sharing the MLflow endpoint with multiple teams.

---

## 10. Related

- `aidocs/integrations/81-jupyterhub-integration.md` — companion integration; `shepard-py`
  SDK (J2b) is the shared transport layer used by both integrations
- `aidocs/platform/47-dev-experience-and-plugin-system.md §3.2` — plugin-first rule;
  `shepard-plugin-airflow` follows the plugin shape
- `aidocs/platform/30-mcp-plugin-design.md` — MCP surface; an AI agent can use the MCP tools
  to discover which Shepard DataObjects a given MLflow run trained on, complementing the
  lineage bridge
- `aidocs/platform/87-timeseries-appid-migration.md` — TS-IDa backfill; once shipped,
  `ShepardReadOperator` transitions from the 5-tuple channel identity to `timeseries_app_id`
- IMP1 (`/v2/import/validate` + `/v2/import/plans/{commitId}`) — used by `ShepardWriteOperator`
- P10a/P10b (`POST /v2/sql/timeseries`) — used by `ShepardReadOperator`
- ANC-1 (`POST /v2/collections/{c}/data-objects/{do}/predecessors`) — used by
  `ShepardProvenanceOperator` and the Marquez bridge
