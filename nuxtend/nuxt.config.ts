import vuetify, { transformAssetUrls } from "vite-plugin-vuetify";

function getSessionRefreshInterval(): number {
  if (process.env.SESSION_REFRESH_INTERVAL) {
    const interval: number = parseInt(process.env.SESSION_REFRESH_INTERVAL);
    if (!Number.isNaN(interval)) {
      return interval;
    }
  }
  return 30000; // 30 secs default
}

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

  components: [{ path: "~/components", pathPrefix: false }],

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
    originEnvKey: "AUTH_ORIGIN",
    provider: {
      type: "authjs",
      trustHost: false,
      defaultProvider: "oidc",
      addDefaultCallbackUrl: true,
    },
    sessionRefresh: {
      // how often the session is refreshed
      // triggers jwt callback in NuxtAuthHandler
      enablePeriodically: getSessionRefreshInterval(),
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

  css: ["@/styles/main.scss"],

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
    sessionRefreshInterval: getSessionRefreshInterval(),
    public: {
      backendApiUrl: "",
    },
  },
});
