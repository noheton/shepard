<template>
  <v-snackbar
    v-model="show"
    :timeout="-1"
    location="bottom right"
    :color="authExpired || pendingReload ? 'warning' : 'info'"
    class="stale-bundle-banner"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon class="me-2">{{
        authExpired ? 'mdi-account-clock'
        : pendingReload ? 'mdi-refresh'
        : 'mdi-update'
      }}</v-icon>
      <div v-if="authExpired">
        Your session has expired.
        <div class="text-caption text-medium-emphasis">
          Sign in again to continue — your work is still here.
        </div>
      </div>
      <div v-else-if="pendingReload">
        Reloading shepard in {{ reloadCountdown }}s…
        <div class="text-caption text-medium-emphasis">
          A new version was deployed. Your page will refresh automatically.
        </div>
      </div>
      <div v-else>
        A newer version of shepard is available.
        <div class="text-caption text-medium-emphasis">
          You're on {{ initial }}, the server is now {{ latest }}.
          Refresh to load the new version.
        </div>
      </div>
    </div>
    <template #actions>
      <template v-if="authExpired">
        <v-btn variant="tonal" color="white" @click="reauth">Sign in again</v-btn>
        <v-btn variant="text" color="white" @click="dismiss">Later</v-btn>
      </template>
      <template v-else>
        <v-btn variant="tonal" color="white" @click="refresh">Refresh now</v-btn>
        <v-btn variant="text" color="white" @click="dismiss">Later</v-btn>
      </template>
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
/**
 * StaleBundleBanner — shows when a new frontend build is detected,
 * either via backend-version poll (version path) or via a chunk 404
 * after a deploy rotated hashes (chunk-reload path). Also shown when
 * a periodic auth check finds a RefreshTokenError (session-expired path).
 *
 * State lives in useStaleBundle() — a module-level composable so
 * Nuxt plugins can trigger the banner from outside setup().
 *
 * Poll cadence: 90 s idle + immediate check on tab-focus.
 * Chunk-reload path: 3 s countdown then hard reload, cancellable.
 * Auth-expired path: user prompted to sign in again or dismiss.
 */
import { onBeforeUnmount, onMounted } from "vue";
import { useStaleBundle } from "~/composables/layout/useStaleBundle";

const {
  show,
  initial,
  latest,
  pendingReload,
  reloadCountdown,
  authExpired,
  initVersion,
  setVersions,
  triggerAuthExpired,
  dismiss,
  refresh,
} = useStaleBundle();

const { data, signIn } = useAuth();
const route = useRoute();

let timer: ReturnType<typeof setInterval> | null = null;

async function fetchVersion(): Promise<string | null> {
  try {
    const cfg = useRuntimeConfig();
    const base = cfg.public.backendApiUrl as string;
    if (!base) return null;
    const res = await $fetch<{ shepardVersion?: string }>(`${base}/versionz`, {
      credentials: "include",
      retry: 0,
      timeout: 5_000,
    });
    return res?.shepardVersion ?? null;
  } catch {
    return null;
  }
}

function checkAuth() {
  if (route.path.startsWith("/auth")) return;
  if ((data.value as { error?: string } | null)?.error === "RefreshTokenError") {
    triggerAuthExpired();
  }
}

async function tick() {
  checkAuth();
  const v = await fetchVersion();
  if (v == null) return;
  if (initial.value === null) {
    initVersion(v);
    return;
  }
  setVersions(initial.value, v);
}

function onVisibility() {
  if (typeof document === "undefined") return;
  if (document.visibilityState === "visible") void tick();
}

async function reauth() {
  if (route.path !== "/") {
    const redirectCookie = useCookie(signInRedirectCookie, { sameSite: "strict" });
    redirectCookie.value = route.fullPath;
  }
  await signIn(undefined);
}

onMounted(() => {
  void tick();
  // 90 s poll — fast enough to catch most deploys before chunk 404s hit.
  timer = setInterval(() => void tick(), 90_000);
  if (typeof document !== "undefined") {
    document.addEventListener("visibilitychange", onVisibility);
  }
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
  if (typeof document !== "undefined") {
    document.removeEventListener("visibilitychange", onVisibility);
  }
});
</script>

<style scoped>
.stale-bundle-banner :deep(.v-snackbar__content) {
  max-width: 480px;
}
</style>
