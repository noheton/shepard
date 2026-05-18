<script setup lang="ts">
import { AboutFragments } from "./aboutMenuItems";

// Wire shape returned by GET /v2/instance/identity (matches the admin GET).
interface InstanceIdentity {
  rorId?: string;
  organizationName?: string;
  rorUrl?: string;
}

// Subset of the ROR v2 organisation response we render on the page.
// ror.org returns far more; we cherry-pick the fields a researcher actually
// wants to see at a glance.
interface RorOrganization {
  id?: string;
  names?: { value: string; types?: string[]; lang?: string | null }[];
  types?: string[];
  established?: number;
  status?: string;
  locations?: {
    geonames_details?: {
      name?: string;
      country_name?: string;
    };
  }[];
  links?: { type?: string; value?: string }[];
}

const identity = ref<InstanceIdentity | null>(null);
const identityLoading = ref(true);
const rorData = ref<RorOrganization | null>(null);
const rorLoading = ref(false);
const rorError = ref<string | null>(null);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchIdentity() {
  identityLoading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const response = await fetch(`${v2BaseUrl()}/v2/instance/identity`, {
      headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
    });
    if (response.ok) {
      identity.value = (await response.json()) as InstanceIdentity;
    }
  } catch (e) {
    handleError(e as Error, "fetching instance identity");
  } finally {
    identityLoading.value = false;
  }
}

async function fetchRorData(rorId: string) {
  rorLoading.value = true;
  rorError.value = null;
  try {
    // ror.org's public API — no auth, CORS-enabled. v2 returns richer data
    // than v1; both work.
    const response = await fetch(`https://api.ror.org/v2/organizations/${rorId}`);
    if (!response.ok) {
      rorError.value = `ror.org returned HTTP ${response.status}`;
      return;
    }
    rorData.value = (await response.json()) as RorOrganization;
  } catch (e) {
    rorError.value = (e as Error).message;
  } finally {
    rorLoading.value = false;
  }
}

watch(identity, value => {
  if (value?.rorId) fetchRorData(value.rorId);
});

fetchIdentity();

// Display helpers
const displayName = computed(() => {
  // Prefer the ror_display name from ror.org, fall back to the stored
  // organizationName, fall back to the rorId so something always shows.
  if (rorData.value?.names) {
    const display = rorData.value.names.find(n => n.types?.includes("ror_display"));
    if (display) return display.value;
    const first = rorData.value.names[0];
    if (first) return first.value;
  }
  return identity.value?.organizationName ?? identity.value?.rorId ?? null;
});

const otherNames = computed(() => {
  if (!rorData.value?.names) return [];
  return rorData.value.names
    .filter(n => !n.types?.includes("ror_display"))
    .map(n => n.value)
    .filter((v, i, arr) => arr.indexOf(v) === i)
    .slice(0, 6);
});

const primaryLink = computed(() => {
  if (!rorData.value?.links) return null;
  const website = rorData.value.links.find(l => l.type === "website");
  return website?.value ?? rorData.value.links[0]?.value ?? null;
});

const location = computed(() => {
  const loc = rorData.value?.locations?.[0]?.geonames_details;
  if (!loc) return null;
  return [loc.name, loc.country_name].filter(Boolean).join(", ");
});
</script>

<template>
  <div :id="AboutFragments.ORGANIZATION" class="d-flex flex-column ga-3">
    <div class="text-h4 pb-2">Organization</div>

    <div v-if="identityLoading" class="d-flex align-center ga-2 text-medium-emphasis">
      <v-progress-circular indeterminate size="16" width="2" />
      Loading instance identity…
    </div>

    <div
      v-else-if="!identity?.rorId"
      class="text-medium-emphasis text-body-2"
    >
      No organisation has been configured for this instance yet.
      An admin can set one via
      <code>PATCH /v2/admin/instance/ror</code>
      (or the matching <code>shepard-admin instance ror set</code> CLI).
    </div>

    <template v-else>
      <div class="text-h5">{{ displayName ?? identity.rorId }}</div>

      <div v-if="rorLoading" class="d-flex align-center ga-2 text-medium-emphasis text-body-2">
        <v-progress-circular indeterminate size="14" width="2" />
        Fetching details from ror.org…
      </div>
      <v-alert
        v-else-if="rorError"
        type="warning"
        variant="tonal"
        density="compact"
        class="my-2"
      >
        Could not load extra details from ror.org: {{ rorError }}
      </v-alert>

      <div class="info-grid mt-2">
        <span class="info-label">ROR ID</span>
        <span>
          <a
            :href="identity.rorUrl"
            target="_blank"
            rel="external noopener"
            class="text-primary"
          >{{ identity.rorUrl }}</a>
        </span>

        <template v-if="location">
          <span class="info-label">Location</span>
          <span>{{ location }}</span>
        </template>

        <template v-if="rorData?.types?.length">
          <span class="info-label">Type</span>
          <span>{{ rorData.types.join(", ") }}</span>
        </template>

        <template v-if="rorData?.established">
          <span class="info-label">Established</span>
          <span>{{ rorData.established }}</span>
        </template>

        <template v-if="primaryLink">
          <span class="info-label">Website</span>
          <span>
            <a
              :href="primaryLink"
              target="_blank"
              rel="external noopener"
              class="text-primary"
            >{{ primaryLink }}</a>
          </span>
        </template>

        <template v-if="otherNames.length">
          <span class="info-label">Other names</span>
          <span class="d-flex flex-wrap ga-1">
            <v-chip
              v-for="(n, i) in otherNames"
              :key="i"
              size="x-small"
              variant="tonal"
            >{{ n }}</v-chip>
          </span>
        </template>
      </div>

      <p class="text-caption text-medium-emphasis mt-2">
        Organisation details fetched live from
        <a href="https://ror.org" target="_blank" rel="external noopener" class="text-primary">ror.org</a>.
        Only the ROR id is stored on the shepard instance.
      </p>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.info-grid {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 16px;
  row-gap: 6px;
  font-size: 0.9rem;
}
.info-label {
  font-weight: 500;
  color: rgb(var(--v-theme-textbody2));
  white-space: nowrap;
}
</style>
