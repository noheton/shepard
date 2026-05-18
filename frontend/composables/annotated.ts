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

abstract class ContainerAnnotated implements Annotated {
  abstract readonly basePath: string; // e.g. "timeseries-containers"
  readonly containerId: number;

  constructor(containerId: number) {
    this.containerId = containerId;
  }

  private endpoint(annotationId?: number): string {
    const base = `${v2BaseUrl()}/v2/${this.basePath}/${this.containerId}/annotations`;
    return annotationId === undefined ? base : `${base}/${annotationId}`;
  }

  async fetchAnnotations(): Promise<SemanticAnnotation[]> {
    const headers = await authHeaders();
    const response = await fetch(this.endpoint(), { headers });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
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

export class AnnotatedTimeseriesContainer extends ContainerAnnotated {
  readonly basePath = "timeseries-containers";
}

export class AnnotatedFileContainer extends ContainerAnnotated {
  readonly basePath = "file-containers";
}

export class AnnotatedStructuredDataContainer extends ContainerAnnotated {
  readonly basePath = "structured-data-containers";
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
