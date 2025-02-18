import { Params } from "k6/http";
import { getSettings } from "./settings";

export function buildUri(relativePath: string): string {
  const url = getSettings().BACKEND_BASE_URL + relativePath;
  return url;
}

export function buildParamsWithApiKey(): Params {
  return {
    timeout: "120s",
    headers: {
      "X-API-KEY": getSettings().API_KEY,
    },
  };
}
