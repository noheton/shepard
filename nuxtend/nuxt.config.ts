import vuetify, { transformAssetUrls } from "vite-plugin-vuetify";
export default defineNuxtConfig({
  compatibilityDate: "2024-09-06",
  devtools: { enabled: true },

  build: {
    transpile: ["vuetify"],
  },

  future: {
    compatibilityVersion: 4,
  },

  modules: [
    (_options, nuxt) => {
      nuxt.hooks.hook("vite:extendConfig", config => {
        // @ts-expect-error - This is set by the official Vuetify documentation
        config.plugins.push(vuetify({ autoImport: true }));
      });
    },
    "@nuxt/eslint",
    "@sidebase/nuxt-auth",
  ],

  auth: {
    isEnabled: true,
    baseURL: `${process.env.AUTH_ORIGIN}api/auth/`,
    provider: {
      type: "authjs",
      trustHost: false,
      defaultProvider: "oidc",
      addDefaultCallbackUrl: true,
    },
    sessionRefresh: {
      enablePeriodically: 10000,
    },
  },

  // This reverts the new srcDir default from `app` back to your root directory
  srcDir: ".",
  // This specifies the directory prefix for `app/router.options.ts` and `app/spa-loading-template.html`
  dir: {
    app: "app",
  },

  vite: {
    vue: {
      template: {
        transformAssetUrls,
      },
    },
  },

  // Environment variables
  runtimeConfig: {
    public: {
      backendApiUrl: "",
    },
  },
});
