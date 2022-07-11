import {
  CreateFileContainerRequest,
  CreateFileRequest,
  DeleteFileContainerRequest,
  DeleteFileRequest,
  EditFilePermissionsRequest,
  FileApi,
  GetAllFileContainersRequest,
  GetAllFilesRequest,
  GetFileContainerRequest,
  GetFilePermissionsRequest,
  GetFileRequest,
  GetFileRolesRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class FileService {
  static createFile(params: CreateFileRequest) {
    const api = new FileApi(getConfiguration());
    return api.createFile(params);
  }

  static deleteFile(params: DeleteFileRequest) {
    const api = new FileApi(getConfiguration());
    return api.deleteFile(params);
  }

  static getFile(params: GetFileRequest) {
    const api = new FileApi(getConfiguration());
    return api.getFile(params);
  }

  static getAllFiles(params: GetAllFilesRequest) {
    const api = new FileApi(getConfiguration());
    return api.getAllFiles(params);
  }

  static getFilePermissions(params: GetFilePermissionsRequest) {
    const api = new FileApi(getConfiguration());
    return api.getFilePermissions(params);
  }

  static editFilePermissions(params: EditFilePermissionsRequest) {
    const api = new FileApi(getConfiguration());
    return api.editFilePermissions(params);
  }

  static createFileContainer(params: CreateFileContainerRequest) {
    const api = new FileApi(getConfiguration());
    return api.createFileContainer(params);
  }

  static deleteFileContainer(params: DeleteFileContainerRequest) {
    const api = new FileApi(getConfiguration());
    return api.deleteFileContainer(params);
  }

  static getFileContainer(params: GetFileContainerRequest) {
    const api = new FileApi(getConfiguration());
    return api.getFileContainer(params);
  }

  static getAllFileContainers(params: GetAllFileContainersRequest) {
    const api = new FileApi(getConfiguration());
    return api.getAllFileContainers(params);
  }

  static getFileRoles(params: GetFileRolesRequest) {
    const api = new FileApi(getConfiguration());
    return api.getFileRoles(params);
  }
}
