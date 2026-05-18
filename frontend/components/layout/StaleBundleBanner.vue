<template>
  <v-snackbar
    v-model="show"
    :timeout="-1"
    location="bottom right"
    color="info"
    class="stale-bundle-banner"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon class="me-2">mdi-update</v-icon>
      <div>
        A newer version of shepard is available.
        <div class="text-caption text-medium-emphasis">
          You're on {{ initial }}, the server is now {{ latest }}.
          Refresh to load the new version.
        </div>
      </div>
    </div>
    <template #actions>
      <v-btn variant="tonal" color="white" @click="refresh">Refresh</v-btn>
      <v-btn variant="text" color="white" @click="dismiss">Later</v-btn>
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
/**
 * StaleBundleBanner — periodic compare of the backend's reported
 * version against the first version observed at this tab's mount.
 * On mismatch surfaces a Vuetify snackbar with a "Refresh" action.
 *
 * Polling cadence: 5 minutes plus a re-check on `visibilitychange →
 * visible` (the most common staleness vector is "user came back to a
 * tab they had open since yesterday's deploy"). Network blips silently
 * skip — we never want a noisy banner on transient connectivity.
 *
 * The first observed version is the reference: any subsequent value
 * that differs is "stale". Inlining the logic here (rather than a
 * shared composable under `composables/`) keeps the auto-import
 * surface simple — the banner is the only consumer, no plumbing to
 * solve.
 *
 * Complements the passive `experimental.emitRouteChunkError =
 * "automatic"` in `nuxt.config.ts`: that catches the staleness AFTER
 * a chunk 404s; this catches it BEFORE anything breaks so the user
 * has a chance to refresh on their own schedule.
 */
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

const initial = ref<string | null>(null);
const latest = ref<string | null>(null);
const stale = ref(false);
const dismissed = ref(false);

const show = computed({
  get: () => stale.value && !dismissed.value,
  set: v => {
    if (!v) dismissed.value = true;
  },
});

let timer: ReturnType<typeof setInterval> | null = null;

async function fetchBackendVersion(): Promise<string | null> {
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

async function tick() {
  const v = await fetchBackendVersion();
  if (v == null) return;
  if (initial.value === null) {
    initial.value = v;
    latest.value = v;
    return;
  }
  latest.value = v;
  stale.value = v !== initial.value;
}

function onVisibility() {
  if (typeof document === "undefined") return;
  if (document.visibilityState === "visible") void tick();
}

onMounted(() => {
  void tick();
  timer = setInterval(() => void tick(), 5 * 60 * 1000);
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

function refresh() {
  if (typeof window !== "undefined") window.location.reload();
}

function dismiss() {
  dismissed.value = true;
}
</script>

<style scoped>
.stale-bundle-banner :deep(.v-snackbar__content) {
  max-width: 480px;
}
</style>
