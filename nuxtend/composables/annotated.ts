import {
  SemanticAnnotationApi,
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
}

export class AnnotatedReference implements Annotated {
  collectionId: number;
  dataobjectId: number;
  referenceId: number;

  constructor(collectionId: number, dataobjectId: number, referenceId: number) {
    this.collectionId = collectionId;
    this.dataobjectId = dataobjectId;
    this.referenceId = referenceId;
  }

  fetchAnnotations(): Promise<SemanticAnnotation[]> {
    const cid = this.collectionId;
    const did = this.dataobjectId;
    const rid = this.referenceId;

    return api().getAllReferenceAnnotations(
      new (class implements GetAllReferenceAnnotationsRequest {
        collectionId = cid;
        dataObjectId = did;
        referenceId = rid;
      })(),
    );
  }
}
