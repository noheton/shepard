<script setup lang="ts">
import { AdminFragments } from "./adminMenuItems";

/**
 * V1COMPAT.0 — admin pane for the legacy v1 surface control plane.
 *
 * Surfaces:
 *   1. Current :LegacyV1Config.enabled state with a toggle that
 *      PATCHes /v2/admin/legacy/v1/config (RFC 7396 merge-patch).
 *   2. Live in-memory hit counters from /v2/admin/legacy/v1/stats:
 *      total hits, top endpoints, top principals, first/most-recent
 *      hit timestamps.
 *   3. A non-blocking warning when stats show recent hits AND the
 *      admin is about to flip the toggle off — operator should
 *      know they're about to 410 live traffic.
 *
 * Backend endpoints (all @RolesAllowed instance-admin):
 *   - GET    /v2/admin/legacy/v1/config
 *   - PATCH  /v2/admin/legacy/v1/config  (Content-Type:
 *            application/merge-patch+json; body {"enabled": bool})
 *   - GET    /v2/admin/legacy/v1/stats?topN=N
 */

function getV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

interface ConfigDto {
  enabled: boolean;
  appId?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

interface EndpointCount {
  pathPattern: string;
  hits: number;
}

interface PrincipalCount {
  principalSub: string;
  hits: number;
}

interface StatsDto {
  totalHits: number;
  byEndpoint: EndpointCount[];
  byPrincipal: PrincipalCount[];
  firstHitAt?: string | null;
  mostRecentHitAt?: string | null;
}

const cfg = ref<ConfigDto | null>(null);
const stats = ref<StatsDto | null>(null);
const loadingCfg = ref(false);
const loadingStats = ref(false);
const patching = ref(false);
const fetchError = ref<string | null>(null);
const confirmDialog = ref(false);
const pendingEnabledValue = ref<boolean | null>(null);

async function authHeaders(): Promise<Headers | null> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  if (!token) return null;
  const h = new Headers();
  h.set("Authorization", `Bearer ${token}`);
  h.set("Accept", "application/json");
  return h;
}

async function loadConfig() {
  loadingCfg.value = true;
  fetchError.value = null;
  try {
    const headers = await authHeaders();
    if (!headers) {
      fetchError.value = "No session token available — sign in again.";
      return;
    }
    const res = await fetch(`${getV2BaseUrl()}/v2/admin/legacy/v1/config`, { headers });
    if (res.ok) {
      cfg.value = (await res.json()) as ConfigDto;
    } else {
      fetchError.value = `GET /config returned ${res.status}.`;
    }
  } catch (e) {
    fetchError.value = `GET /config failed: ${(e as Error).message}`;
  } finally {
    loadingCfg.value = false;
  }
}

async function loadStats() {
  loadingStats.value = true;
  try {
    const headers = await authHeaders();
    if (!headers) return;
    const res = await fetch(`${getV2BaseUrl()}/v2/admin/legacy/v1/stats?topN=20`, { headers });
    if (res.ok) {
      stats.value = (await res.json()) as StatsDto;
    }
  } catch {
    // non-fatal — config controls are still useful without stats
  } finally {
    loadingStats.value = false;
  }
}

function recentlyActive(): boolean {
  if (!stats.value || stats.value.totalHits === 0) return false;
  if (!stats.value.mostRecentHitAt) return false;
  const dt = Date.parse(stats.value.mostRecentHitAt);
  return Number.isFinite(dt) && Date.now() - dt < 7 * 24 * 60 * 60 * 1000;
}

function onToggleClick(targetValue: boolean) {
  // When flipping off AND there's recent traffic, confirm first.
  if (cfg.value && cfg.value.enabled && !targetValue && recentlyActive()) {
    pendingEnabledValue.value = targetValue;
    confirmDialog.value = true;
    return;
  }
  void applyPatch(targetValue);
}

async function applyPatch(enabled: boolean) {
  patching.value = true;
  fetchError.value = null;
  try {
    const headers = await authHeaders();
    if (!headers) {
      fetchError.value = "No session token available — sign in again.";
      return;
    }
    headers.set("Content-Type", "application/merge-patch+json");
    const res = await fetch(`${getV2BaseUrl()}/v2/admin/legacy/v1/config`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({ enabled }),
    });
    if (res.ok) {
      cfg.value = (await res.json()) as ConfigDto;
    } else {
      fetchError.value = `PATCH /config returned ${res.status}.`;
    }
  } catch (e) {
    fetchError.value = `PATCH /config failed: ${(e as Error).message}`;
  } finally {
    patching.value = false;
    confirmDialog.value = false;
    pendingEnabledValue.value = null;
  }
}

function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const dt = new Date(iso);
  return Number.isNaN(dt.getTime()) ? iso : dt.toLocaleString();
}

void loadConfig();
void loadStats();
</script>

<template>
  <div :id="AdminFragments.LEGACY_V1" class="d-flex flex-column ga-4">
    <div class="d-flex align-center ga-3">
      <h4 class="text-h4">Legacy v1 surface</h4>
      <v-btn
        icon="mdi-refresh"
        variant="text"
        size="small"
        :loading="loadingCfg || loadingStats"
        @click="() => { void loadConfig(); void loadStats(); }"
      />
    </div>

    <v-alert type="info" variant="tonal" density="compact">
      The legacy <code>/shepard/api/...</code> surface is shepard's
      byte-compat contract with upstream 5.2.0. Default state is on.
      Flip off only when downstream tools no longer need it; the
      stats below show who's still hitting v1.
    </v-alert>

    <v-alert v-if="fetchError" type="error" variant="tonal">
      {{ fetchError }}
    </v-alert>

    <!-- Config card -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-toggle-switch-outline</v-icon>
        Runtime toggle (:LegacyV1Config.enabled)
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="loadingCfg && !cfg" indeterminate />
        <template v-if="cfg">
          <div class="d-flex align-center ga-4">
            <v-switch
              :model-value="cfg.enabled"
              :loading="patching"
              :disabled="patching"
              color="primary"
              density="compact"
              hide-details
              :label="cfg.enabled ? 'v1 surface ON (default)' : 'v1 surface OFF — every /shepard/api/... returns 410 Gone'"
              @update:model-value="(v) => onToggleClick(Boolean(v))"
            />
          </div>
          <div class="text-caption text-medium-emphasis mt-2">
            <span v-if="cfg.updatedBy">
              Last changed by <strong>{{ cfg.updatedBy }}</strong>
              {{ cfg.updatedAt ? `at ${fmtDate(cfg.updatedAt)}` : "" }}.
            </span>
            <span v-else>Never changed at runtime; current value is the deploy-time install default.</span>
          </div>
        </template>
      </v-card-text>
    </v-card>

    <!-- Stats card -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-counter</v-icon>
        Hit counters (since process start)
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="loadingStats && !stats" indeterminate />
        <template v-if="stats">
          <v-row dense class="mb-2">
            <v-col cols="12" sm="6" md="3">
              <div class="text-caption text-medium-emphasis">Total hits</div>
              <div class="text-h5">{{ stats.totalHits.toLocaleString() }}</div>
            </v-col>
            <v-col cols="12" sm="6" md="4">
              <div class="text-caption text-medium-emphasis">First hit</div>
              <div class="text-body-2">{{ fmtDate(stats.firstHitAt) }}</div>
            </v-col>
            <v-col cols="12" sm="6" md="4">
              <div class="text-caption text-medium-emphasis">Most recent</div>
              <div class="text-body-2">{{ fmtDate(stats.mostRecentHitAt) }}</div>
            </v-col>
          </v-row>

          <div v-if="stats.totalHits === 0" class="text-medium-emphasis">
            No v1 traffic observed this process. Safe to flip off.
          </div>

          <v-row v-else dense>
            <v-col cols="12" md="6">
              <div class="text-caption text-medium-emphasis mb-1">
                Top endpoints
              </div>
              <v-list density="compact" lines="one">
                <v-list-item
                  v-for="e in stats.byEndpoint"
                  :key="e.pathPattern"
                  :title="e.pathPattern"
                  :subtitle="`${e.hits.toLocaleString()} hits`"
                />
              </v-list>
            </v-col>
            <v-col cols="12" md="6">
              <div class="text-caption text-medium-emphasis mb-1">
                Top principals
              </div>
              <v-list density="compact" lines="one">
                <v-list-item
                  v-for="p in stats.byPrincipal"
                  :key="p.principalSub"
                  :title="p.principalSub"
                  :subtitle="`${p.hits.toLocaleString()} hits`"
                />
              </v-list>
            </v-col>
          </v-row>
        </template>
      </v-card-text>
    </v-card>

    <!-- Confirm-flip-off dialog -->
    <v-dialog v-model="confirmDialog" max-width="540">
      <v-card>
        <v-card-title>Disable the v1 surface?</v-card-title>
        <v-card-text>
          <div>
            The stats show v1 traffic within the last 7 days. Flipping
            the toggle off will return HTTP 410 Gone for every
            <code>/shepard/api/...</code> request — any downstream tool
            still on v1 breaks immediately.
          </div>
          <div v-if="stats" class="mt-3 text-caption text-medium-emphasis">
            Most recent hit: {{ fmtDate(stats.mostRecentHitAt) }}<br>
            Total hits this process: {{ stats.totalHits.toLocaleString() }}
          </div>
          <div class="mt-3">
            You can re-enable at any time with no data loss.
          </div>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmDialog = false; pendingEnabledValue = null">Cancel</v-btn>
          <v-btn
            color="warning"
            variant="tonal"
            :loading="patching"
            @click="() => pendingEnabledValue !== null && applyPatch(pendingEnabledValue)"
          >
            Disable v1
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
code {
  background: rgba(0, 0, 0, 0.06);
  border-radius: 3px;
  padding: 1px 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
