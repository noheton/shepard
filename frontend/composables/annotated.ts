import {
  SemanticAnnotationApi,
  TimeseriesContainerApi,
  type SemanticAnnotation,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "./common/api/useShepardApi";

export type AnnotationToAdd = Omit<
  SemanticAnnotation,
  "id" | "name" | "propertyName" | "valueName"
>;

export interface Annotated {
  fetchAnnotations(): Promise<SemanticAnnotation[]>;

  deleteAnnotation(annotationId: number): Promise<void>;

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation>;
}

export class AnnotatedCollection implements Annotated {
  collectionId: number;
  semanticAnnotationApi = useShepardApi(SemanticAnnotationApi);

  constructor(collectionId: number) {
    this.collectionId = collectionId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return this.semanticAnnotationApi.value.getAllCollectionAnnotations({
      collectionId: this.collectionId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return this.semanticAnnotationApi.value.deleteCollectionAnnotation({
      collectionId: this.collectionId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.semanticAnnotationApi.value.createCollectionAnnotation({
      collectionId: this.collectionId,
      semanticAnnotation: annotation,
    });
  }
}

export class AnnotatedDataObject implements Annotated {
  collectionId: number;
  dataObjectId: number;
  semanticAnnotationApi = useShepardApi(SemanticAnnotationApi);

  constructor(collectionId: number, dataObjectId: number) {
    this.collectionId = collectionId;
    this.dataObjectId = dataObjectId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return this.semanticAnnotationApi.value.getAllDataObjectAnnotations({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return this.semanticAnnotationApi.value.deleteDataObjectAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.semanticAnnotationApi.value.createDataObjectAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      semanticAnnotation: annotation,
    });
  }
}

export class AnnotatedReference implements Annotated {
  collectionId: number;
  dataObjectId: number;
  referenceId: number;
  semanticAnnotationApi = useShepardApi(SemanticAnnotationApi);

  constructor(collectionId: number, dataobjectId: number, referenceId: number) {
    this.collectionId = collectionId;
    this.dataObjectId = dataobjectId;
    this.referenceId = referenceId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return this.semanticAnnotationApi.value.getAllReferenceAnnotations({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return this.semanticAnnotationApi.value.deleteReferenceAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.semanticAnnotationApi.value.createReferenceAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
      semanticAnnotation: annotation,
    });
  }
}

// ─── SA-CONT: container-level annotations via /v2/ endpoints ────────────────
//
// Shared raw-fetch wiring. The generated SemanticAnnotationApi only covers the
// upstream /shepard/api/ targets (collection, data-object, reference,
// timeseries-channel); the /v2/{type}-containers/{id}/annotations endpoints
// are new on this fork, so we hit them directly until the OpenAPI client is
// regenerated.

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

// Wire shape of the AnnotationIO objects returned by GET /v2/annotations
interface AnnotationV2Wire {
  appId: string;
  propertyName?: string;
  propertyIri?: string;
  valueName?: string;
  valueIri?: string;
  predicateLabel?: string;
  predicateIri?: string;
  objectLiteral?: string;
  objectIri?: string;
}

function mapAnnotationV2ToLegacy(
  item: AnnotationV2Wire,
  fakeId: number,
): SemanticAnnotation {
  return {
    id: fakeId,
    name: item.propertyName ?? item.predicateLabel ?? "",
    propertyName: item.propertyName ?? item.predicateLabel ?? "",
    propertyIRI: item.propertyIri ?? item.predicateIri ?? "",
    valueName: item.valueName ?? item.objectLiteral ?? "",
    valueIRI: item.valueIri ?? item.objectIri ?? "",
    propertyRepositoryId: 0,
    valueRepositoryId: 0,
  };
}

// APISIMP-CONTAINER-ANNOTATED-FE-DEAD-ENDPOINTS: the per-kind
// /v2/{type}-containers/{id}/annotations endpoints were deleted in
// APISIMP-SA-CONT-DELETE (2026-06-10). Container-level annotations are now
// addressed via the generic GET|POST|DELETE /v2/annotations surface.
abstract class ContainerAnnotated implements Annotated {
  abstract readonly subjectKind: string; // e.g. "TimeseriesContainer"
  readonly containerAppId: string;
  // fakeId (sequential integer) → real annotation appId for delete lookup
  private readonly _appIdMap = new Map<number, string>();

  constructor(containerAppId: string) {
    this.containerAppId = containerAppId;
  }

  private annotationsUrl(suffix?: string): string {
    const base = `${v2BaseUrl()}/v2/annotations`;
    return suffix ? `${base}/${suffix}` : base;
  }

  async fetchAnnotations(): Promise<SemanticAnnotation[]> {
    if (!this.containerAppId) return [];
    const headers = await authHeaders();
    const url =
      `${this.annotationsUrl()}?subjectAppId=` +
      `${encodeURIComponent(this.containerAppId)}&pageSize=200`;
    const response = await fetch(url, { headers });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const items = (await response.json()) as AnnotationV2Wire[];
    this._appIdMap.clear();
    return items.map((item, idx) => {
      this._appIdMap.set(idx, item.appId);
      return mapAnnotationV2ToLegacy(item, idx);
    });
  }

  async deleteAnnotation(annotationId: number): Promise<void> {
    const annotationAppId = this._appIdMap.get(annotationId);
    if (!annotationAppId)
      throw new Error(`Unknown annotation fakeId ${annotationId}`);
    const headers = await authHeaders();
    const response = await fetch(this.annotationsUrl(annotationAppId), {
      method: "DELETE",
      headers,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
  }

  async addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    const headers = await authHeaders();
    const body: Record<string, unknown> = {
      subjectAppId: this.containerAppId,
      subjectKind: this.subjectKind,
      predicateIri: annotation.propertyIRI,
    };
    if (annotation.valueIRI) {
      body.objectIri = annotation.valueIRI;
    } else {
      body.objectLiteral = "";
    }
    const response = await fetch(this.annotationsUrl(), {
      method: "POST",
      headers,
      body: JSON.stringify(body),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const created = (await response.json()) as AnnotationV2Wire;
    const fakeId = Date.now();
    this._appIdMap.set(fakeId, created.appId);
    return mapAnnotationV2ToLegacy(created, fakeId);
  }
}

export class AnnotatedTimeseriesContainer extends ContainerAnnotated {
  readonly subjectKind = "TimeseriesContainer";
}

export class AnnotatedFileContainer extends ContainerAnnotated {
  readonly subjectKind = "FileContainer";
}

export class AnnotatedStructuredDataContainer extends ContainerAnnotated {
  readonly subjectKind = "StructuredDataContainer";
}

// ─── TS-SEMANTIC-REST: channelShepardId-keyed channel annotations (v2) ──────
//
// Wraps `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations`
// (TimeseriesChannelAnnotationRest). Distinct from {@link AnnotatedTimeseries}
// which still hits the upstream numeric-id-keyed `/shepard/api/...` route.
// Channels created before TS-SEMANTIC-01 dual-write shipped will GET an empty
// list and POST will 404 — surface that to the user as "no annotations yet".

export class AnnotatedChannel implements Annotated {
  readonly containerAppId: string;
  readonly channelShepardId: string;

  constructor(containerAppId: string, channelShepardId: string) {
    this.containerAppId = containerAppId;
    this.channelShepardId = channelShepardId;
  }

  private endpoint(annotationId?: number): string {
    const base =
      `${v2BaseUrl()}/v2/timeseries-containers/${this.containerAppId}` +
      `/channels/${encodeURIComponent(this.channelShepardId)}/annotations`;
    return annotationId === undefined ? base : `${base}/${annotationId}`;
  }

  async fetchAnnotations(): Promise<SemanticAnnotation[]> {
    const headers = await authHeaders();
    const response = await fetch(this.endpoint(), { headers });
    if (!response.ok) {
      // 404 = channel has no AnnotatableTimeseries node yet (pre-TS-SEMANTIC-01
      // channel) — treat as "no annotations" rather than an error.
      if (response.status === 404) return [];
      throw new Error(`HTTP ${response.status}`);
    }
    return (await response.json()) as SemanticAnnotation[];
  }

  async deleteAnnotation(annotationId: number): Promise<void> {
    const headers = await authHeaders();
    const response = await fetch(this.endpoint(annotationId), {
      method: "DELETE",
      headers,
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
  }

  async addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    const headers = await authHeaders();
    const response = await fetch(this.endpoint(), {
      method: "POST",
      headers,
      body: JSON.stringify(annotation),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return (await response.json()) as SemanticAnnotation;
  }
}

// ─── Per-Timeseries-channel annotations (covered by upstream API) ───────────

export class AnnotatedTimeseries implements Annotated {
  api = useShepardApi(TimeseriesContainerApi);
  entity: TimeseriesEntity;

  constructor(entity: TimeseriesEntity) {
    if (entity.id === undefined || entity.containerId === undefined) {
      throw new Error(
        "The annotated timeseries entity does not have an id or a container!",
      );
    }
    this.entity = entity;
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return this.api.value.deleteAnnotationOfTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
      semanticAnnotationId: annotationId,
    });
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return this.api.value.getAllAnnotationsOfTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.api.value.createAnnotationForTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
      semanticAnnotation: annotation,
    });
  }
}
