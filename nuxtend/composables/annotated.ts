import {
  SemanticAnnotationApi,
  TimeseriesContainerApi,
  type SemanticAnnotation,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";

function api() {
  return createApiInstance(SemanticAnnotationApi);
}

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

  constructor(collectionId: number) {
    this.collectionId = collectionId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return api().getAllCollectionAnnotations({
      collectionId: this.collectionId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return api().deleteCollectionAnnotation({
      collectionId: this.collectionId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return api().createCollectionAnnotation({
      collectionId: this.collectionId,
      semanticAnnotation: annotation,
    });
  }
}

export class AnnotatedDataObject implements Annotated {
  collectionId: number;
  dataObjectId: number;

  constructor(collectionId: number, dataObjectId: number) {
    this.collectionId = collectionId;
    this.dataObjectId = dataObjectId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return api().getAllDataObjectAnnotations({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return api().deleteDataObjectAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return api().createDataObjectAnnotation({
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

  constructor(collectionId: number, dataobjectId: number, referenceId: number) {
    this.collectionId = collectionId;
    this.dataObjectId = dataobjectId;
    this.referenceId = referenceId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return api().getAllReferenceAnnotations({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
    });
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    return api().deleteReferenceAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
      semanticAnnotationId: annotationId,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return api().createReferenceAnnotation({
      collectionId: this.collectionId,
      dataObjectId: this.dataObjectId,
      referenceId: this.referenceId,
      semanticAnnotation: annotation,
    });
  }
}

export class AnnotatedTimeseries implements Annotated {
  api = createApiInstance(TimeseriesContainerApi);
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
    return this.api.deleteAnnotationOfTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
      semanticAnnotationId: annotationId,
    });
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    return this.api.getAllAnnotationsOfTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
    });
  }

  addAnnotation(annotation: AnnotationToAdd): Promise<SemanticAnnotation> {
    return this.api.createAnnotationForTimeseries({
      timeseriesContainerId: this.entity.containerId!,
      timeseriesId: this.entity.id!,
      semanticAnnotation: annotation,
    });
  }
}
