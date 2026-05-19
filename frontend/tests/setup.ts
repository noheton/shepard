/**
 * Vitest global setup — polyfills Nuxt auto-imports for composable unit tests.
 *
 * Nuxt normally generates these bindings at build time from the Nuxt app
 * context.  In a plain Vitest run there is no Nuxt runtime, so we expose the
 * real Vue reactive primitives as globals and stub the Nuxt-specific
 * composables with minimal implementations that individual tests can override.
 */
import { ref, computed, reactive, watch, watchEffect, nextTick } from "vue";
import { vi } from "vitest";

// Vue reactive primitives
Object.assign(globalThis, { ref, computed, reactive, watch, watchEffect, nextTick });

// Nuxt built-ins — default stubs, overridden per test as needed
Object.assign(globalThis, {
  handleError: vi.fn(),
  useAuth: () => ({
    refresh: vi.fn().mockResolvedValue(undefined),
    data: ref<{ accessToken: string } | null>(null),
    signIn: vi.fn().mockResolvedValue(undefined),
  }),
  useRouter: () => ({
    currentRoute: ref({ fullPath: "/" }),
  }),
  useRuntimeConfig: () => ({ public: { backendApiUrl: "http://localhost:8080" } }),
});
