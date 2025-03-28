import {
  SemanticAnnotationApi,
  type DeleteCollectionAnnotationRequest,
  type DeleteDataObjectAnnotationRequest,
  type DeleteReferenceAnnotationRequest,
  type GetAllCollectionAnnotationsRequest,
  type GetAllDataObjectAnnotationsRequest,
  type GetAllReferenceAnnotationsRequest,
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
    const cid = this.collectionId;

    return api().getAllCollectionAnnotations(
      new (class implements GetAllCollectionAnnotationsRequest {
        collectionId = cid;
      })(),
    );
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    const cid = this.collectionId;
    return api().deleteCollectionAnnotation(
      new (class implements DeleteCollectionAnnotationRequest {
        collectionId = cid;
        semanticAnnotationId = annotationId;
      })(),
    );
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
    const cid = this.collectionId;
    const did = this.dataObjectId;

    return api().getAllDataObjectAnnotations(
      new (class implements GetAllDataObjectAnnotationsRequest {
        collectionId = cid;
        dataObjectId = did;
      })(),
    );
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    const cid = this.collectionId;
    const did = this.dataObjectId;
    return api().deleteDataObjectAnnotation(
      new (class implements DeleteDataObjectAnnotationRequest {
        collectionId = cid;
        dataObjectId = did;
        semanticAnnotationId = annotationId;
      })(),
    );
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
    const cid = this.collectionId;
    const did = this.dataObjectId;
    const rid = this.referenceId;

    return api().getAllReferenceAnnotations(
      new (class implements GetAllReferenceAnnotationsRequest {
        collectionId = cid;
        dataObjectId = did;
        referenceId = rid;
      })(),
    );
  }

  deleteAnnotation(annotationId: number): Promise<void> {
    const cid = this.collectionId;
    const did = this.dataObjectId;
    const rid = this.referenceId;
    return api().deleteReferenceAnnotation(
      new (class implements DeleteReferenceAnnotationRequest {
        collectionId = cid;
        dataObjectId = did;
        referenceId = rid;
        semanticAnnotationId = annotationId;
      })(),
    );
  }
}
