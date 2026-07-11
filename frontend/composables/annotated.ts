import {
  ContainersApi,
  SemanticAnnotationsApi,
  type AnnotationV2,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "./common/api/useV2ShepardApi";

export type AnnotationToAdd = Omit<
  SemanticAnnotation,
  "id" | "name" | "propertyName" | "valueName"
>;

export interface Annotated {
  fetchAnnotations(): Promise<SemanticAnnotation[]>;

  deleteAnnotation(annotationId: number): Promise<void>;

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation>;
}

// ─── v2 polymorphic annotation surface (SEMA-V6-004) ────────────────────────
//
// The frontend addresses every entity by `appId`. The v2 `/v2/annotations`
// surface (SemanticAnnotationsApi: listAnnotations / createAnnotation /
// deleteAnnotation, keyed by `subjectAppId` + `subjectKind`) is the single
// canonical annotation API. AnnotatedCollection / AnnotatedDataObject /
// AnnotatedReference / the container variants are all thin subclasses of
// {@link SubjectAnnotated} that differ only in their `subjectKind`.
//
// The numeric Neo4j `.id` was dropped from v2 entities, so the old v1
// `SemanticAnnotationApi` (singular, keyed on numeric collectionId /
// dataObjectId) could no longer be addressed — it crashed with
// "Required parameter collectionId was null or undefined". This surface
// replaces it entirely.

/**
 * Maps the v6 {@link AnnotationV2} wire shape onto the legacy
 * {@link SemanticAnnotation} the UI chips / lists still consume. `fakeId`
 * is a sequential integer the UI uses as a v-for key and delete handle; the
 * real `appId` is kept in the owning class's `_appIdMap` for the v2 delete.
 */
function mapAnnotationV2ToLegacy(
  item: AnnotationV2,
  fakeId: number,
): SemanticAnnotation {
  return {
    id: fakeId,
    name: item.predicateLabel ?? "",
    propertyName: item.predicateLabel ?? "",
    propertyIRI: item.predicateIri ?? "",
    valueName: item.objectLiteral ?? "",
    valueIRI: item.objectIri ?? "",
    propertyRepositoryId: 0,
    valueRepositoryId: 0,
  };
}

/**
 * Base class for every entity-scoped annotation accessor. Routes through the
 * typed {@link SemanticAnnotationsApi} via {@link useV2ShepardApi} (so the URL
 * is `/v2/annotations`, never the v1-helper `/shepard/api/v2/...` 404 shape),
 * keyed by `subjectAppId` (a v7 UUID string) + `subjectKind`.
 */
abstract class SubjectAnnotated implements Annotated {
  abstract readonly subjectKind: string; // e.g. "DataObject", "Collection"
  readonly subjectAppId: string;
  annotationsApi = useV2ShepardApi(SemanticAnnotationsApi);
  // fakeId (sequential integer) → real annotation appId for delete lookup
  private readonly _appIdMap = new Map<number, string>();

  constructor(subjectAppId: string) {
    this.subjectAppId = subjectAppId;
  }

  async fetchAnnotations(): Promise<SemanticAnnotation[]> {
    if (!this.subjectAppId) return [];
    try {
      const page = await this.annotationsApi.value.listAnnotations({
        subjectAppId: this.subjectAppId,
        subjectKind: this.subjectKind,
        pageSize: 200,
      });
      this._appIdMap.clear();
      return (page.items ?? []).map((item, idx) => {
        this._appIdMap.set(idx, item.appId);
        return mapAnnotationV2ToLegacy(item, idx);
      });
    } catch (err: unknown) {
      // 403: caller has no direct permission on this entity's annotations node
      // (e.g. a Collection Reader whose access is inherited, not direct).
      // Degrade silently to empty list — the panel is informational, not gated.
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403) return [];
      throw err;
    }
  }

  async deleteAnnotation(annotationId: number): Promise<void> {
    const annotationAppId = this._appIdMap.get(annotationId);
    if (!annotationAppId)
      throw new Error(`Unknown annotation fakeId ${annotationId}`);
    await this.annotationsApi.value.deleteAnnotation({ appId: annotationAppId });
  }

  async addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    const created = await this.annotationsApi.value.createAnnotation({
      createAnnotationV2: {
        subjectAppId: this.subjectAppId,
        subjectKind: this.subjectKind,
        predicateIri: annotation.propertyIRI,
        ...(annotation.valueIRI
          ? { objectIri: annotation.valueIRI }
          : { objectLiteral: "" }),
      },
    });
    const fakeId = Date.now();
    this._appIdMap.set(fakeId, created.appId);
    return mapAnnotationV2ToLegacy(created, fakeId);
  }
}

export class AnnotatedCollection extends SubjectAnnotated {
  readonly subjectKind = "Collection";
}

export class AnnotatedDataObject extends SubjectAnnotated {
  readonly subjectKind = "DataObject";
}

/**
 * Reference annotations. `subjectKind` is the concrete reference kind so the
 * backend's polymorphic permission walk and SHACL constraints resolve the
 * right shape; callers that don't know the concrete kind may pass the generic
 * default `"Reference"`.
 */
export class AnnotatedReference extends SubjectAnnotated {
  readonly subjectKind: string;

  constructor(referenceAppId: string, referenceKind = "Reference") {
    super(referenceAppId);
    this.subjectKind = referenceKind;
  }
}

export class AnnotatedTimeseriesContainer extends SubjectAnnotated {
  readonly subjectKind = "TimeseriesContainer";
}

export class AnnotatedFileContainer extends SubjectAnnotated {
  readonly subjectKind = "FileContainer";
}

export class AnnotatedStructuredDataContainer extends SubjectAnnotated {
  readonly subjectKind = "StructuredDataContainer";
}

// ─── TS-SEMANTIC-REST: channelShepardId-keyed channel annotations (v2) ──────
//
// Wraps `/v2/containers/{containerAppId}/channels/{channelShepardId}/annotations`
// via the typed `TimeseriesChannelAnnotationsApi` (V2UI-CHANNEL-ANNO-CLIENT).
// Channels created before TS-SEMANTIC-01 dual-write shipped will return 404 on
// GET/POST — treated as "no annotations yet" (empty list).
//
// Delete uses the UUID `appId` returned in the annotation payload
// (added to SemanticAnnotationIO in the same commit). The `_appIdMap` caches
// numeric-id → appId so the `Annotated` interface's numeric-id delete handle
// keeps working without changing callers.

export class AnnotatedChannel implements Annotated {
  readonly containerAppId: string;
  readonly channelShepardId: string;
  channelAnnotationsApi = useV2ShepardApi(ContainersApi);
  private readonly _appIdMap = new Map<number, string>();

  constructor(containerAppId: string, channelShepardId: string) {
    this.containerAppId = containerAppId;
    this.channelShepardId = channelShepardId;
  }

  async fetchAnnotations(): Promise<SemanticAnnotation[]> {
    try {
      const items = await this.channelAnnotationsApi.value.listChannelAnnotations({
        appId: this.containerAppId,
        channelShepardId: this.channelShepardId,
      });
      this._appIdMap.clear();
      for (const item of items) {
        // `appId` is present in the wire response (SemanticAnnotationIO) but not
        // yet in the generated TS model (awaiting client regen after this backend
        // change). Cast until the next regen cycle.
        const appId = (item as SemanticAnnotation & { appId?: string }).appId;
        if (appId) this._appIdMap.set(item.id, appId);
      }
      return items;
    } catch (err: unknown) {
      // 404 = channel has no AnnotatableTimeseries node yet (pre-TS-SEMANTIC-01)
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 404) return [];
      throw err;
    }
  }

  async deleteAnnotation(annotationId: number): Promise<void> {
    const annotationAppId = this._appIdMap.get(annotationId);
    if (!annotationAppId)
      throw new Error(`No appId cached for channel annotation id=${annotationId}; call fetchAnnotations() first`);
    await this.channelAnnotationsApi.value.deleteChannelAnnotation({
      appId: this.containerAppId,
      channelShepardId: this.channelShepardId,
      annotationAppId,
    });
    this._appIdMap.delete(annotationId);
  }

  async addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.channelAnnotationsApi.value.createChannelAnnotation({
      appId: this.containerAppId,
      channelShepardId: this.channelShepardId,
      semanticAnnotation: annotation,
    });
  }
}

