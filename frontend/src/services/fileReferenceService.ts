import {
  FileReferenceApi,
  type CreateFileReferenceRequest,
  type DeleteFileReferenceRequest,
  type GetAllFileReferencesRequest,
  type GetFilePayloadRequest,
  type GetFileReferenceRequest,
  type GetFilesRequest,
} from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class FileReferenceService {
  static getFileReference(params: GetFileReferenceRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.getFileReference(params);
  }

  static getAllFileReferences(params: GetAllFileReferencesRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.getAllFileReferences(params);
  }

  static createFileReference(params: CreateFileReferenceRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.createFileReference(params);
  }

  static deleteFileReference(params: DeleteFileReferenceRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.deleteFileReference(params);
  }

  static getFilePayload(params: GetFilePayloadRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.getFilePayload(params);
  }

  static getFiles(params: GetFilesRequest) {
    const api = new FileReferenceApi(getConfiguration());
    return api.getFiles(params);
  }
}
