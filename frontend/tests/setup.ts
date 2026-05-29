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

/**
 * Minimal useAsyncData stub (PERF11).
 *
 * Mirrors the Nuxt useAsyncData contract used by the three SSR-preloaded
 * composables: useFetchRecentCollections, useFetchCollection,
 * useFetchDataObjectMap. The real Nuxt composable serialises the result
 * into the SSR payload; this test-stub simply executes the async function
 * immediately (synchronously schedules it) and exposes { data, pending,
 * error, refresh } refs so tests can drive the same behaviour.
 *
 * Individual tests can override the global with vi.stubGlobal if they need
 * fine-grained control over pending state.
 */
function makeUseAsyncData() {
  return function useAsyncData<T>(
    _key: string,
    fn: () => Promise<T>,
    options?: { default?: () => T },
  ) {
    const defaultVal = options?.default ? options.default() : undefined;
    const data = ref<T | undefined>(defaultVal as T | undefined);
    const pending = ref(true);
    const error = ref<Error | null>(null);

    async function refresh() {
      pending.value = true;
      error.value = null;
      try {
        data.value = await fn();
      } catch (e) {
        error.value = e instanceof Error ? e : new Error(String(e));
      } finally {
        pending.value = false;
      }
    }

    // Kick off the initial fetch (mirrors Nuxt's immediate execution).
    void refresh();

    return { data, pending, error, refresh };
  };
}

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
  onUnmounted: vi.fn(),
});
