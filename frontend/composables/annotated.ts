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
