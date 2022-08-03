import { Configuration } from "@dlr-shepard/shepard-client";
import getEnv from "../utils/env";
import store from "../utils/vuex-store";

export function getConfiguration(): Configuration {
  const token = store.getters["oidcStore/oidcAccessToken"];
  const config = new Configuration({
    basePath: getEnv("VITE_BACKEND"),
    accessToken: token,
  });
  return config;
}
