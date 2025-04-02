import {
  SemanticAnnotationApi,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";

function api() {
  return createApiInstance(SemanticAnnotationApi);
}

export interface Annotated {
  fetchAnnotations(): Promise<SemanticAnnotation[]>;
  deleteAnnotation(annotationId: number): Promise<void>;
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
}
