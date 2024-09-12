import {
  ReferenceApi,
  type DeleteBasicReferenceRequest,
  type GetBasicReferenceRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class BasicReferenceService {
  static getBasicReference(params: GetBasicReferenceRequest) {
    const api = new ReferenceApi(getConfiguration());
    return api.getBasicReference(params);
  }

  static deleteBasicReference(params: DeleteBasicReferenceRequest) {
    const api = new ReferenceApi(getConfiguration());
    return api.deleteBasicReference(params);
  }
}
