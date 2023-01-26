import {
  SemanticRepositoryApi,
  type CreateSemanticRepositoryRequest,
  type DeleteSemanticRepositoryRequest,
  type GetSemanticRepositoryRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class SemanticRepositoryService {
  static createSemanticRepository(params: CreateSemanticRepositoryRequest) {
    const api = new SemanticRepositoryApi(getConfiguration());
    return api.createSemanticRepository(params);
  }
  static deleteSemanticRepository(params: DeleteSemanticRepositoryRequest) {
    const api = new SemanticRepositoryApi(getConfiguration());
    return api.deleteSemanticRepository(params);
  }
  static getAllSemanticRepositories(params: RequestInit) {
    const api = new SemanticRepositoryApi(getConfiguration());
    return api.getAllSemanticRepositories(params);
  }
  static getSemanticRepository(params: GetSemanticRepositoryRequest) {
    const api = new SemanticRepositoryApi(getConfiguration());
    return api.getSemanticRepository(params);
  }
}
