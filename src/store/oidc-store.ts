import getEnv from "@/utils/env";
import { nanoid } from "nanoid";
import { vuexOidcCreateStoreModule } from "vuex-oidc";

const loco = window.location;
const appRootUrl = `${loco.protocol}//${loco.host}${import.meta.env.BASE_URL}`;

const clientSettings = {
  authority: getEnv("VITE_OIDC_AUTHORITY"),
  client_id: getEnv("VITE_CLIENT_ID"),
  redirect_uri: appRootUrl + "oidc-callback",
  responseType: "code",
  scope: "openid email profile",
  automaticSilentRenew: true,
  automaticSilentSignin: false,
  accessTokenExpiringNotificationTimeInSeconds: 10,
  extraQueryParams: { nonce: nanoid() },
};

export default vuexOidcCreateStoreModule(clientSettings, {
  namespaced: true,
  routeBase: import.meta.env.BASE_URL,
});
