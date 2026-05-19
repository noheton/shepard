<template>
  <v-snackbar
    v-model="show"
    :timeout="-1"
    location="bottom right"
    color="warning"
    multi-line
  >
    <div class="d-flex align-center">
      <v-icon class="me-2">mdi-clock-alert-outline</v-icon>
      <div>
        Your session expires soon.
        <div class="text-caption text-medium-emphasis">
          Click "Stay signed in" to continue your work without interruption.
        </div>
      </div>
    </div>
    <template #actions>
      <v-btn variant="tonal" color="white" :loading="refreshing" @click="staySignedIn">
        Stay signed in
      </v-btn>
      <v-btn variant="text" color="white" @click="show = false">Dismiss</v-btn>
    </template>
  </v-snackbar>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";

const { refresh, data } = useAuth();

const show = ref(false);
const refreshing = ref(false);
let timer: ReturnType<typeof setTimeout> | null = null;

/** Decode the JWT exp claim without any external library. */
function getExpSeconds(token: string | null | undefined): number | null {
  if (!token) return null;
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return typeof decoded.exp === "number" ? decoded.exp : null;
  } catch {
    return null;
  }
}

const WARN_BEFORE_MS = 5 * 60 * 1000; // 5 minutes
const FALLBACK_MS = 30 * 60 * 1000;   // 30-minute fallback if token unparseable

function scheduleWarning(token: string | null | undefined) {
  if (timer) clearTimeout(timer);
  timer = null;
  show.value = false;

  // No token → user is not signed in or just signed out. Nothing to schedule.
  if (!token) return;

  const exp = getExpSeconds(token);
  // Fall back to a 30-minute timer when the token is present but unparseable
  // (e.g. an opaque token from an unusual IdP config). This avoids silent
  // no-warning scenarios for non-standard JWT shapes.
  const delay = exp != null
    ? exp * 1000 - Date.now() - WARN_BEFORE_MS
    : FALLBACK_MS - WARN_BEFORE_MS;

  if (delay <= 0) {
    show.value = true;
    return;
  }
  timer = setTimeout(() => { show.value = true; }, delay);
}

async function staySignedIn() {
  refreshing.value = true;
  try {
    await refresh();
    show.value = false;
  } finally {
    refreshing.value = false;
  }
}

// Re-schedule whenever the access token changes (e.g., after refresh()).
watch(() => data.value?.accessToken, token => scheduleWarning(token));

onMounted(() => scheduleWarning(data.value?.accessToken));
onUnmounted(() => { if (timer) clearTimeout(timer); });
</script>
