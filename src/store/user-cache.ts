import getEnv from "@/utils/env";
import { Configuration, UserApi, type User } from "@dlr-shepard/shepard-client";
import type { ActionContext } from "vuex";
import type { RootState } from ".";

export interface UserCacheState {
  users: { [key: string]: User };
  pending: string[];
}

export default {
  namespaced: true,
  state: {
    users: {},
    pending: [],
  },
  mutations: {
    addUserToCache: (state: UserCacheState, user: User) => {
      if (user.username) {
        const tmp: { [key: string]: User } = {};
        tmp[user.username] = user;
        state.users = { ...state.users, ...tmp };
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
      return state.users[username];
    },
    isUserInCache: (state: UserCacheState) => (username: string) => {
      return username in state.users;
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
