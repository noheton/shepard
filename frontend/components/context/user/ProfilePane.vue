<script setup lang="ts">
import { useFetchUserProfile } from "~/composables/context/useFetchUserProfile";
import { usePatchMe } from "~/composables/context/usePatchMe";
import { useJupyterPreference } from "~/composables/context/useJupyterPreference";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import { useShowOrcidBadge } from "~/composables/context/useShowOrcidBadge";
import { useOrcidProfile } from "~/composables/context/useOrcidProfile";

const { user, isLoading } = useFetchUserProfile();
const { patchMe, isSaving } = usePatchMe();
const { preferredJupyterUrl, isSaving: isJupyterSaving, save: saveJupyter } = useJupyterPreference();
const { advancedMode, isSaving: isAdvancedSaving, setAdvancedMode } = useAdvancedMode();
const { showOrcidBadge, isSaving: isOrcidBadgeSaving, setShowOrcidBadge } = useShowOrcidBadge();

const userOrcid = computed(() => user.value?.orcid ?? null);
const { profile: orcidProfile, loading: orcidLoading } = useOrcidProfile(userOrcid);

// appId is a fork extension not in the auto-generated User type
const userAppId = computed<string | undefined>(() => {
  const raw = (user.value as unknown as { appId?: string | null })?.appId;
  return raw ?? undefined;
});

// U1e: Avatar upload/delete
const avatarKey = ref(0); // bump to force img reload after upload
const avatarError = ref(false);
const isAvatarUploading = ref(false);
const avatarFileInput = ref<HTMLInputElement | null>(null);

function avatarUrl(appId: string) {
  // Compose against the v2 base — same rationale as the upload PUT above.
  // The avatar GET is public so no auth header is needed; just the URL.
  return `${v2BaseUrl()}/v2/users/${appId}/avatar?v=${avatarKey.value}`;
}

function onAvatarError() {
  avatarError.value = true;
}

function triggerAvatarUpload() {
  avatarFileInput.value?.click();
}

// V2 base URL + bearer token plumbing — previously the fetch was a
// bare relative `/v2/users/me/avatar` PUT with no auth header. In
// production the frontend hostname (shepard.nuclide.systems) is
// distinct from the backend (shepard-api.nuclide.systems), so the
// relative path resolved against the frontend SSR (no /v2/ route → 404)
// and the OIDC session cookie wouldn't have attached cross-origin
// anyway. Use the same plumbing as `useV2ShepardApi` so the PUT
// lands on the real backend with the user's Bearer token.
const { data: authData } = useAuth();
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit;
  return (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

async function onAvatarFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  isAvatarUploading.value = true;
  try {
    const form = new FormData();
    form.append("file", file);
    const token = (authData.value as unknown as { accessToken?: string } | null)?.accessToken;
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    const resp = await fetch(`${v2BaseUrl()}/v2/users/me/avatar`, {
      method: "PUT",
      body: form,
      headers,
    });
    if (resp.ok) {
      avatarError.value = false;
      avatarKey.value++;
    }
  } finally {
    isAvatarUploading.value = false;
    input.value = "";
  }
}

async function deleteAvatar() {
  const token = (authData.value as unknown as { accessToken?: string } | null)?.accessToken;
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  await fetch(`${v2BaseUrl()}/v2/users/me/avatar`, { method: "DELETE", headers });
  avatarError.value = true;
  avatarKey.value++;
}

const editDialog = ref(false);
const editOrcid = ref<string>("");
const editDisplayName = ref<string>("");

const jupyterUrlInput = ref<string>("");

watch(
  preferredJupyterUrl,
  (url) => {
    jupyterUrlInput.value = url;
  },
  { immediate: true },
);

function openEdit() {
  editOrcid.value = user.value?.orcid ?? "";
  editDisplayName.value = user.value?.displayName ?? "";
  editDialog.value = true;
}

async function saveEdit() {
  const updated = await patchMe({
    orcid: editOrcid.value.trim() === "" ? null : editOrcid.value.trim(),
    displayName:
      editDisplayName.value.trim() === ""
        ? null
        : editDisplayName.value.trim(),
  });
  if (updated) {
    user.value = updated;
    editDialog.value = false;
  }
}

async function saveJupyterUrl() {
  await saveJupyter(jupyterUrlInput.value.trim());
}
</script>

<template>
  <div class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between">
      <h4 class="text-h4">Profile</h4>
      <v-btn
        v-if="user && !isLoading"
        variant="tonal"
        size="small"
        prepend-icon="mdi-pencil"
        @click="openEdit"
      >
        Edit
      </v-btn>
    </div>

    <!-- U1e: avatar (+ ORCID badge overlay when set & opt-in) -->
    <div v-if="user && !isLoading" class="d-flex align-center ga-4">
      <div class="avatar-wrapper">
        <v-avatar size="80" color="primary">
          <v-img
            v-if="!avatarError && userAppId"
            :src="avatarUrl(userAppId)"
            cover
            @error="onAvatarError"
          />
          <span v-else class="text-h5 font-weight-medium">
            {{ (user.effectiveDisplayName ?? user.username ?? "?").charAt(0).toUpperCase() }}
          </span>
        </v-avatar>
        <!-- ORCID badge — bottom-right of avatar. Encourages adoption
             by making a configured ORCID immediately visible. User
             can hide via the Display settings switch below. -->
        <a
          v-if="user.orcid && showOrcidBadge"
          :href="`https://orcid.org/${user.orcid}`"
          target="_blank"
          rel="noopener"
          class="orcid-badge"
          :title="`ORCID: ${user.orcid} — click to view on orcid.org`"
        >
          <svg viewBox="0 0 256 256" width="24" height="24" aria-label="ORCID iD" role="img">
            <circle cx="128" cy="128" r="128" fill="#A6CE39" />
            <g fill="#FFFFFF">
              <rect x="83" y="105" width="14" height="78" />
              <circle cx="90" cy="88" r="9" />
              <path d="M115 105 h35 c25 0 41 18 41 39 0 22 -18 39 -41 39 h-35 z M129 117 v54 h19 c20 0 28 -14 28 -27 0 -16 -10 -27 -28 -27 z" />
            </g>
          </svg>
        </a>
      </div>
      <div class="d-flex flex-column ga-1">
        <input
          ref="avatarFileInput"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          class="d-none"
          @change="onAvatarFileSelected"
        />
        <v-btn
          size="small"
          variant="tonal"
          prepend-icon="mdi-camera"
          :loading="isAvatarUploading"
          :disabled="isAvatarUploading"
          @click="triggerAvatarUpload"
        >
          Upload avatar
        </v-btn>
        <v-btn
          v-if="!avatarError && userAppId"
          size="small"
          variant="text"
          color="error"
          prepend-icon="mdi-delete-outline"
          @click="deleteAvatar"
        >
          Remove
        </v-btn>
      </div>
    </div>

    <centered-loading-spinner v-if="isLoading" />
    <v-table v-if="user && !isLoading" class="table">
      <tbody>
        <tr>
          <th>Username</th>
          <td>{{ user.username }}</td>
        </tr>
        <tr v-if="user.effectiveDisplayName">
          <th>Display Name</th>
          <td>{{ user.effectiveDisplayName }}</td>
        </tr>
        <tr>
          <th>First Name</th>
          <td>{{ user.firstName }}</td>
        </tr>
        <tr>
          <th>Last Name</th>
          <td>{{ user.lastName }}</td>
        </tr>
        <tr>
          <th>E-Mail</th>
          <td>{{ user.email }}</td>
        </tr>
        <tr>
          <th>ORCID</th>
          <td>
            <a
              v-if="user.orcid"
              :href="`https://orcid.org/${user.orcid}`"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ user.orcid }}
            </a>
            <span v-else class="text-disabled">Not set</span>
          </td>
        </tr>
      </tbody>
    </v-table>

    <!-- ORCID public profile data — keywords + recent works -->
    <div v-if="user && user.orcid && !isLoading" class="d-flex flex-column ga-2">
      <h5 class="text-h5">ORCID Profile</h5>
      <centered-loading-spinner v-if="orcidLoading" />
      <template v-else-if="orcidProfile">
        <div v-if="orcidProfile.keywords.length" class="d-flex flex-wrap ga-1 align-center">
          <span class="text-body-2 text-medium-emphasis me-1">Keywords:</span>
          <v-chip
            v-for="kw in orcidProfile.keywords"
            :key="kw"
            size="x-small"
            variant="tonal"
          >{{ kw }}</v-chip>
        </div>
        <div v-if="orcidProfile.works.length" class="d-flex flex-column ga-1">
          <span class="text-body-2 text-medium-emphasis">Recent publications:</span>
          <ol class="ps-4 ma-0">
            <li
              v-for="work in orcidProfile.works"
              :key="work.title"
              class="text-body-2"
            >
              <a
                v-if="work.url"
                :href="work.url"
                target="_blank"
                rel="noopener noreferrer"
              >{{ work.title }}</a>
              <span v-else>{{ work.title }}</span>
              <span v-if="work.year" class="text-medium-emphasis ms-1">({{ work.year }})</span>
            </li>
          </ol>
        </div>
        <p
          v-if="!orcidProfile.keywords.length && !orcidProfile.works.length"
          class="text-body-2 text-medium-emphasis"
        >
          No public keywords or works found on this ORCID record.
        </p>
      </template>
    </div>

    <!-- JupyterHub URL section -->
    <div v-if="user && !isLoading" class="d-flex flex-column ga-2">
      <h5 class="text-h5">JupyterHub</h5>
      <v-text-field
        v-model="jupyterUrlInput"
        label="JupyterHub base URL"
        placeholder="https://myhub.example.com"
        hint="Set your JupyterHub base URL to enable 'Open in JupyterHub' buttons. Stored in your user preferences."
        persistent-hint
        variant="outlined"
        density="comfortable"
        clearable
        :loading="isJupyterSaving"
        :disabled="isJupyterSaving"
      >
        <template #append>
          <v-btn
            color="primary"
            variant="flat"
            density="comfortable"
            :loading="isJupyterSaving"
            :disabled="isJupyterSaving"
            @click="saveJupyterUrl"
          >
            Save
          </v-btn>
        </template>
      </v-text-field>
    </div>

    <!-- Advanced mode toggle -->
    <div v-if="user && !isLoading" class="d-flex flex-column ga-2">
      <h5 class="text-h5">Display settings</h5>
      <v-switch
        :model-value="advancedMode"
        label="Advanced mode"
        :loading="isAdvancedSaving"
        :disabled="isAdvancedSaving"
        color="primary"
        density="comfortable"
        hide-details
        @update:model-value="val => setAdvancedMode(Boolean(val))"
      />
      <p class="text-body-2 text-medium-emphasis">
        Shows advanced features like container management and low-level data views. Off by default for a simpler experience.
      </p>
      <v-switch
        v-if="user && user.orcid"
        :model-value="showOrcidBadge"
        label="Show ORCID badge on my avatar"
        :loading="isOrcidBadgeSaving"
        :disabled="isOrcidBadgeSaving"
        color="primary"
        density="comfortable"
        hide-details
        @update:model-value="val => setShowOrcidBadge(Boolean(val))"
      />
      <p v-if="user && user.orcid" class="text-body-2 text-medium-emphasis">
        When on, a green ORCID badge overlays the bottom-right of your avatar, linking to your ORCID profile. Visible to everyone viewing your profile.
      </p>
    </div>

    <!-- Edit dialog -->
    <v-dialog v-model="editDialog" max-width="480">
      <v-card>
        <v-card-title>Edit Profile</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="editDisplayName"
            label="Display Name"
            hint="Overrides first/last name in the UI. Leave blank to use your name."
            persistent-hint
            class="mb-4"
          />
          <v-text-field
            v-model="editOrcid"
            label="ORCID"
            placeholder="0000-0002-1825-0097"
            hint="16-digit identifier from orcid.org. Leave blank to clear."
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" :disabled="isSaving" @click="editDialog = false">
            Cancel
          </v-btn>
          <v-btn
            variant="tonal"
            color="primary"
            :loading="isSaving"
            @click="saveEdit"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss">
.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
}
.orcid-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: white;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.15s ease;
  text-decoration: none;
}
.orcid-badge:hover {
  transform: scale(1.1);
}
.orcid-badge svg {
  display: block;
}
</style>
