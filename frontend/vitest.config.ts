import { defineConfig } from "vitest/config";
import { resolve } from "path";

export default defineConfig({
  // Nuxt sets `import.meta.client` true in the browser bundle. Composables gate
  // client-only data fetches (personalised, auth-bound) behind it. Under plain
  // Vitest there is no Nuxt build, so statically replace the token with `true`
  // to exercise the client path the composables actually run in production.
  define: {
    "import.meta.client": true,
  },
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
    setupFiles: ["tests/setup.ts"],
  },
  resolve: {
    alias: {
      "~": resolve(__dirname, "."),
    },
  },
});
