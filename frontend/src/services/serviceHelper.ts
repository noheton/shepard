import { Configuration } from "@/generated/openapi";
import store from "@/store";
import getEnv from "@/utils/env";

export function getConfiguration(): Configuration {
  const token = store.getters["oidcStore/oidcAccessToken"];
  const config = new Configuration({
    basePath: getEnv("VITE_BACKEND"),
    accessToken: token,
  });
  return config;
}
