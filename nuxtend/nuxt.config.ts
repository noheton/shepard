import vuetify, { transformAssetUrls } from "vite-plugin-vuetify";

export default defineNuxtConfig({
  compatibilityDate: "2024-09-06",
  devtools: { enabled: true },

  typescript: {
    typeCheck: true,
  },

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
        config.plugins.push(
          vuetify({
            autoImport: true,
            styles: { configFile: "./styles/settings.scss" },
          }),
        );
      });
    },
    "@nuxt/eslint",
    "@nuxt/fonts",
    "@sidebase/nuxt-auth",
  ],

  auth: {
    isEnabled: true,
    provider: {
      type: "authjs",
      trustHost: false,
      defaultProvider: "oidc",
      addDefaultCallbackUrl: true,
    },
    sessionRefresh: {
      enablePeriodically: 60000 * 5 - 5000,
    },
  },

  app: {
    head: {
      title: "shepard",
    },
  },

  // This reverts the new srcDir default from `app` back to your root directory
  srcDir: ".",
  // This specifies the directory prefix for `app/router.options.ts` and `app/spa-loading-template.html`
  dir: {
    app: "app",
  },

  vite: {
    css: {
      preprocessorOptions: {
        sass: {
          api: "modern-compiler",
        },
      },
    },
    vue: {
      template: {
        transformAssetUrls,
      },
    },
  },

  // Environment variables
  runtimeConfig: {
    authSecret: "",
    oidcClientId: "",
    oidcIssuer: "",
    public: {
      backendApiUrl: "",
    },
  },
});
