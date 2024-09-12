import { HealthzApi } from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class HealthzService {
  static getServerHealth() {
    const api = new HealthzApi(getConfiguration());
    return api.getServerHealth();
  }
}
