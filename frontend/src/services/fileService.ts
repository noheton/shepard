import {
  FileContainerApi,
  type CreateFileContainerRequest,
  type CreateFileRequest,
  type DeleteFileContainerRequest,
  type DeleteFileRequest,
  type EditFilePermissionsRequest,
  type GetAllFileContainersRequest,
  type GetAllFilesRequest,
  type GetFileContainerRequest,
  type GetFilePermissionsRequest,
  type GetFileRequest,
  type GetFileRolesRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class FileService {
  static createFile(params: CreateFileRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.createFile(params);
  }

  static deleteFile(params: DeleteFileRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.deleteFile(params);
  }

  static getFile(params: GetFileRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getFile(params);
  }

  static getAllFiles(params: GetAllFilesRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getAllFiles(params);
  }

  static getFilePermissions(params: GetFilePermissionsRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getFilePermissions(params);
  }

  static editFilePermissions(params: EditFilePermissionsRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.editFilePermissions(params);
  }

  static createFileContainer(params: CreateFileContainerRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.createFileContainer(params);
  }

  static deleteFileContainer(params: DeleteFileContainerRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.deleteFileContainer(params);
  }

  static getFileContainer(params: GetFileContainerRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getFileContainer(params);
  }

  static getAllFileContainers(params: GetAllFileContainersRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getAllFileContainers(params);
  }

  static getFileRoles(params: GetFileRolesRequest) {
    const api = new FileContainerApi(getConfiguration());
    return api.getFileRoles(params);
  }
}
