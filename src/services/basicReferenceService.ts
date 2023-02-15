import {
  ReferenceApi,
  type DeleteBasicReferenceRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class BasicReferenceService {
  static deleteBasicReference(params: DeleteBasicReferenceRequest) {
    const api = new ReferenceApi(getConfiguration());
    return api.deleteBasicReference(params);
  }
}
