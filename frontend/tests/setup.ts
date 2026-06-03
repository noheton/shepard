/**
 * Vitest global setup — polyfills Nuxt auto-imports for composable unit tests.
 *
 * Nuxt normally generates these bindings at build time from the Nuxt app
 * context.  In a plain Vitest run there is no Nuxt runtime, so we expose the
 * real Vue reactive primitives as globals and stub the Nuxt-specific
 * composables with minimal implementations that individual tests can override.
 */
import {
  ref,
  computed,
  reactive,
  watch,
  watchEffect,
  nextTick,
  isRef,
  toValue,
  toRef,
  unref,
} from "vue";
import { vi } from "vitest";

// Vue reactive primitives. `toValue` is required by composables that accept a
// `MaybeRefOrGetter` id (BUG-COLL-APPID-ROUTE-007-PAGE) — without it the
// auto-imported global is undefined under plain Vitest.
Object.assign(globalThis, {
  ref,
  computed,
  reactive,
  watch,
  watchEffect,
  nextTick,
  isRef,
  toValue,
  toRef,
  unref,
});

// Nuxt `useState` — SSR-friendly shared state keyed by a string. Under plain
// Vitest there is no Nuxt payload, so back it with a module-level Map of refs
// so repeated `useState("key")` calls in the same run share one ref (matching
// Nuxt's cross-call identity contract). Required by composables that pull in
// `useStaleRoleSession` transitively (e.g. via `useV2ShepardApi`).
const useStateStore = new Map<string, ReturnType<typeof ref>>();
function useState<T>(key: string, init?: () => T) {
  if (!useStateStore.has(key)) {
    useStateStore.set(key, ref(init ? init() : undefined));
  }
  return useStateStore.get(key) as ReturnType<typeof ref>;
}

// Nuxt built-ins — default stubs, overridden per test as needed
Object.assign(globalThis, {
  useState,
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
