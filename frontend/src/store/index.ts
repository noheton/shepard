import Vue from "vue";
import Vuex from "vuex";
import type { VuexOidcState } from "vuex-oidc";
import oidcStore from "./oidc-store";
import userCache, { type UserCacheState } from "./user-cache";

Vue.use(Vuex);

export interface RootState {
  oidcStore: VuexOidcState;
  userCache: UserCacheState;
}
export default new Vuex.Store({
  modules: {
    oidcStore: oidcStore,
    userCache: userCache,
  },
});
