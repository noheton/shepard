/**
 * Vitest global setup — polyfills Nuxt auto-imports for composable unit tests.
 *
 * Nuxt normally generates these bindings at build time from the Nuxt app
 * context.  In a plain Vitest run there is no Nuxt runtime, so we expose the
 * real Vue reactive primitives as globals and stub the Nuxt-specific
 * composables with minimal implementations that individual tests can override.
 */
import { ref, computed, reactive, watch, watchEffect, nextTick, isRef } from "vue";
import { vi } from "vitest";

// Vue reactive primitives
Object.assign(globalThis, { ref, computed, reactive, watch, watchEffect, nextTick, isRef });

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
  useAsyncData: makeUseAsyncData(),
});

/**
 * Minimal useAsyncData mock that mirrors Nuxt's key-deduplication contract:
 * - Same key → same reactive { data, pending, error } refs across calls.
 * - immediate: false → fetcher is not called on construction; call execute() manually.
 * - refresh() / execute() re-run the fetcher and update the shared refs.
 * - In-flight deduplication: a second execute() while one is running reuses the promise.
 */
function makeUseAsyncData() {
  type AsyncEntry = {
    data: ReturnType<typeof ref>;
    pending: ReturnType<typeof ref<boolean>>;
    error: ReturnType<typeof ref>;
    inFlight: Promise<void> | null;
  };
  const store = new Map<string, AsyncEntry>();

  return function useAsyncData(
    key: string,
    fetcher: () => Promise<unknown>,
    options?: { server?: boolean; immediate?: boolean; default?: () => unknown },
  ) {
    if (!store.has(key)) {
      store.set(key, {
        data: ref(options?.default ? options.default() : null),
        pending: ref(false),
        error: ref(null),
        inFlight: null,
      });
    }
    const entry = store.get(key)!;

    async function execute() {
      if (entry.inFlight !== null) return entry.inFlight;
      entry.pending.value = true;
      entry.error.value = null;
      entry.inFlight = fetcher()
        .then(result => {
          entry.data.value = result;
        })
        .catch(e => {
          entry.error.value = e;
          entry.data.value = options?.default ? options.default() : null;
        })
        .finally(() => {
          entry.pending.value = false;
          entry.inFlight = null;
        });
      return entry.inFlight;
    }

    if (options?.immediate !== false) {
      void execute();
    }

    return { data: entry.data, pending: entry.pending, error: entry.error, refresh: execute, execute };
  };
}
