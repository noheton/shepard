import vuetify, { transformAssetUrls } from "vite-plugin-vuetify";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * D1a — copy docs/*.md (and docs/assets/) to frontend/public/docs/ so they
 * are served at /docs/<path>.md from the same origin.  Runs both at build
 * time (nitro:build:before) and during dev server start (ready hook).
 *
 * We exclude Jekyll infrastructure (_includes, _layouts, _config.yml,
 * README.md) and only copy markdown files + the assets/ subtree.
 */
function copyDocsSite(rootDir: string) {
  const src = path.resolve(rootDir, "../docs");
  const dest = path.resolve(rootDir, "public/docs");

  if (!fs.existsSync(src)) {
    console.warn("[D1a] docs/ source directory not found – skipping copy");
    return;
  }

  // Clean and recreate the destination
  fs.rmSync(dest, { recursive: true, force: true });
  fs.mkdirSync(dest, { recursive: true });

  const EXCLUDE_NAMES = new Set([
    "_includes",
    "_layouts",
    "_config.yml",
    "README.md",
    ".jekyll-cache",
    "_site",
    "node_modules",
  ]);

  function copyFiltered(srcDir: string, destDir: string) {
    const entries = fs.readdirSync(srcDir, { withFileTypes: true });
    for (const entry of entries) {
      if (EXCLUDE_NAMES.has(entry.name)) continue;
      const srcPath = path.join(srcDir, entry.name);
      const destPath = path.join(destDir, entry.name);
      if (entry.isDirectory()) {
        fs.mkdirSync(destPath, { recursive: true });
        copyFiltered(srcPath, destPath);
      } else if (
        entry.name.endsWith(".md") ||
        entry.name.endsWith(".png") ||
        entry.name.endsWith(".jpg") ||
        entry.name.endsWith(".jpeg") ||
        entry.name.endsWith(".svg") ||
        entry.name.endsWith(".gif") ||
        srcDir !== src // always copy files nested under assets/
      ) {
        fs.copyFileSync(srcPath, destPath);
      }
    }
  }

  copyFiltered(src, dest);
  console.info(`[D1a] docs copied → ${dest}`);
}

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

  // D1a — copy docs/ to public/docs/ at build time and dev-server start
  hooks: {
    "nitro:build:before"() {
      copyDocsSite(__dirname);
    },
    ready(nuxt) {
      copyDocsSite(nuxt.options.rootDir);
    },
  },


  typescript: {
    typeCheck: process.env.NUXT_TYPECHECK !== "false",
  },

  build: {
    // echarts and zrender are excluded from SSR transpile — see vite.ssr.external below.
    // vue-echarts must be transpiled for client-side ESM compat.
    transpile: ["vuetify", "vue-echarts"],
  },

  future: {
    compatibilityVersion: 4,
  },

  // Stale-cache safety net: when a lazy-loaded route chunk fails to load
  // (404 because the deploy rotated and the user's browser is still on
  // an older bundle pointing at the now-gone `_nuxt/<hash>.js` files),
  // Nuxt's `app:chunkError` hook fires. `'automatic'` makes Nuxt reload
  // the page once on next navigation — most users never notice and
  // refused-to-refresh edge cases get a console error rather than a
  // silent blank page. The visible-banner path (useBackendVersion
  // composable, separate slice) is a slower / more deliberate check
  // sitting on top of this.
  experimental: {
    emitRouteChunkError: "automatic",
  },

  // Cache-control authority: shepard decides, not the upstream proxy.
  // Caddy doesn't add caching, but the absence of any `Cache-Control` on
  // HTML responses lets intermediaries / browsers fall back to heuristic
  // caching (typically based on Last-Modified), which masks freshly
  // deployed bundles — the browser keeps reading an old index.html that
  // references rotated `_nuxt/<hash>.js` filenames, so the page boots a
  // stale app even though the deploy is fresh on disk.
  //
  // Posture:
  //   - HTML / page responses → `no-store, must-revalidate`. Cheap; the
  //     payload is tiny and revalidation guarantees the freshly-built
  //     chunk-hash table is the one the browser reads.
  //   - `/_nuxt/**` hashed assets → `immutable, 1y`. The filename IS the
  //     cache key, so they can sit in cache forever.
  //   - `/docs/**` (markdown source for the in-app /help) → 5-minute
  //     public cache; docs change often enough that 1y is wrong but they
  //     don't need the no-store treatment HTML does.
  routeRules: {
    "/**": { headers: { "Cache-Control": "no-store, must-revalidate" } },
    "/_nuxt/**": {
      headers: { "Cache-Control": "public, max-age=31536000, immutable" },
    },
    "/docs/**": {
      headers: { "Cache-Control": "public, max-age=300, must-revalidate" },
    },
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
    ssr: {
      // Keep echarts/zrender out of the Nitro SSR bundle — their circular ESM
      // dependencies cause a TDZ error when bundled by Rollup. Charts are always
      // wrapped in <ClientOnly> so they never render on the server.
      external: ["echarts", "zrender"],
    },
    css: {
      preprocessorOptions: {
        sass: {
          api: "modern-compiler",
        },
        scss: {
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
      // Base URL for /v2/ endpoints (server root, no /shepard/api suffix).
      // Example: "http://localhost:8080". If unset, derived from backendApiUrl
      // by stripping the /shepard/api suffix.
      backendV2ApiUrl: "",
    },
  },
});
