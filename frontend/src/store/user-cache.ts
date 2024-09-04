import { Configuration, UserApi, type User } from "@/generated/openapi";
import getEnv from "@/utils/env";
import type { ActionContext } from "vuex";
import type { RootState } from ".";

export interface UserCacheState {
  users: Map<string, User>;
  pending: string[];
}

export default {
  namespaced: true,
  state: {
    users: new Map<string, User>(),
    pending: [],
  },
  mutations: {
    addUserToCache: (state: UserCacheState, user: User) => {
      if (user.username) {
        state.users = new Map([
          ...state.users.entries(),
          [user.username, user],
        ]);
      }
    },
    addUserToPending: (state: UserCacheState, username: string) => {
      if (!state.pending.includes(username)) state.pending.push(username);
    },
    removeUserFromPending: (state: UserCacheState, username: string) => {
      state.pending = state.pending.filter(i => i !== username);
    },
  },
  getters: {
    getAllUsers: (state: UserCacheState) => {
      return state.users;
    },
    getUserFromCache: (state: UserCacheState) => (username: string) => {
      return state.users.get(username);
    },
    isUserInCache: (state: UserCacheState) => (username: string) => {
      return state.users.has(username);
    },
    isUserPending: (state: UserCacheState) => (username: string) => {
      return state.pending.includes(username);
    },
  },
  actions: {
    fetchUser: (
      context: ActionContext<UserCacheState, RootState>,
      username: string,
    ) => {
      if (context.getters.isUserPending(username)) return;
      context.commit("addUserToPending", username);
      const conf = new Configuration({
        basePath: getEnv("VITE_BACKEND"),
        accessToken: context.rootState.oidcStore.access_token || "",
      });
      const userApi = new UserApi(conf);
      userApi
        .getUser({ username })
        .then(response => {
          context.commit("addUserToCache", response);
        })
        .finally(() => {
          context.commit("removeUserFromPending", username);
        });
    },
  },
};
