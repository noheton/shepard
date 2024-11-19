import {
  ApikeyApi,
  type CreateApiKeyRequest,
  type DeleteApiKeyRequest,
  type GetAllApiKeysRequest,
  type GetApiKeyRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class ApiKeyService {
  static getApiKey(params: GetApiKeyRequest) {
    const api = new ApikeyApi(getConfiguration());
    return api.getApiKey(params);
  }

  static getAllApiKeys(params: GetAllApiKeysRequest) {
    const api = new ApikeyApi(getConfiguration());
    return api.getAllApiKeys(params);
  }

  static createApiKey(params: CreateApiKeyRequest) {
    const api = new ApikeyApi(getConfiguration());
    return api.createApiKey(params);
  }

  static deleteApiKey(params: DeleteApiKeyRequest) {
    const api = new ApikeyApi(getConfiguration());
    return api.deleteApiKey(params);
  }
}
