import {
  SemanticAnnotationApi,
  type CreateCollectionAnnotationRequest,
  type CreateDataObjectAnnotationRequest,
  type CreateReferenceAnnotationRequest,
  type DeleteCollectionAnnotationRequest,
  type DeleteDataObjectAnnotationRequest,
  type DeleteReferenceAnnotationRequest,
  type GetAllCollectionAnnotationsRequest,
  type GetAllDataObjectAnnotationsRequest,
  type GetAllReferenceAnnotationsRequest,
  type GetCollectionAnnotationRequest,
  type GetDataObjectAnnotationRequest,
  type GetReferenceAnnotationRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class SemanticAnnotationService {
  static createCollectionAnnotation(params: CreateCollectionAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.createCollectionAnnotation(params);
  }
  static deleteCollectionAnnotation(params: DeleteCollectionAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.deleteCollectionAnnotation(params);
  }
  static getAllCollectionAnnotations(
    params: GetAllCollectionAnnotationsRequest,
  ) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getAllCollectionAnnotations(params);
  }
  static getCollectionAnnotation(params: GetCollectionAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getCollectionAnnotation(params);
  }

  static createDataObjectAnnotation(params: CreateDataObjectAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.createDataObjectAnnotation(params);
  }
  static deleteDataObjectAnnotation(params: DeleteDataObjectAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.deleteDataObjectAnnotation(params);
  }
  static getAllDataObjectAnnotations(
    params: GetAllDataObjectAnnotationsRequest,
  ) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getAllDataObjectAnnotations(params);
  }
  static getDataObjectAnnotation(params: GetDataObjectAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getDataObjectAnnotation(params);
  }

  static createReferenceAnnotation(params: CreateReferenceAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.createReferenceAnnotation(params);
  }
  static deleteReferenceAnnotation(params: DeleteReferenceAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.deleteReferenceAnnotation(params);
  }
  static getAllReferenceAnnotations(params: GetAllReferenceAnnotationsRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getAllReferenceAnnotations(params);
  }
  static getReferenceAnnotation(params: GetReferenceAnnotationRequest) {
    const api = new SemanticAnnotationApi(getConfiguration());
    return api.getReferenceAnnotation(params);
  }
}
