import getEnv from "@/utils/env";
import { nanoid } from "nanoid";
import {
  vuexOidcCreateStoreModule,
  type VuexOidcClientSettings,
} from "vuex-oidc";

const loco = window.location;
const appRootUrl = `${loco.protocol}//${loco.host}${import.meta.env.BASE_URL}`;

// TODO: Issue https://github.com/perarnborg/vuex-oidc/issues/204
const clientSettings: unknown = {
  authority: getEnv("VITE_OIDC_AUTHORITY"),
  clientId: getEnv("VITE_CLIENT_ID"),
  redirectUri: appRootUrl + "oidc-callback",
  responseType: "code",
  scope: "openid email profile",
  automaticSilentRenew: true,
  automaticSilentSignin: false,
  accessTokenExpiringNotificationTimeInSeconds: 10,
  extraQueryParams: { nonce: nanoid() },
};

export default vuexOidcCreateStoreModule(
  clientSettings as VuexOidcClientSettings,
  {
    namespaced: true,
    routeBase: import.meta.env.BASE_URL,
  },
);
