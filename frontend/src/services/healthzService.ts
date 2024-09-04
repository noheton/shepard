import { HealthzApi } from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class HealthzService {
  static getServerHealth() {
    const api = new HealthzApi(getConfiguration());
    return api.getServerHealth();
  }
}
