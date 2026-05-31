<script setup lang="ts">
/**
 * ADM-MANAGE — grant/revoke the instance-admin role.
 *
 * Replaces the PlaceholderFragmentPane previously mounted at
 * `/admin#instance-admins`. Backlog row: PLACEHOLDER-REPLACE-ADM-MANAGE.
 */
import type { User } from "@dlr-shepard/backend-client";
import { AdminFragments } from "./adminMenuItems";
import { useInstanceAdmins } from "~/composables/context/admin/useInstanceAdmins";
import UserSearchAutocomplete from "~/components/admin/UserSearchAutocomplete.vue";

const { grants, isLoading, isActing, error, refresh, grant, revoke } =
  useInstanceAdmins();

const selectedUser = ref<User | null>(null);
const grantError = ref<string | null>(null);

// Initial load.
refresh();

const isAlreadyAdmin = computed(() => {
  if (!selectedUser.value) return false;
  return grants.value.some(g => g.username === selectedUser.value!.username);
});

async function onGrant() {
  if (!selectedUser.value) return;
  grantError.value = null;
  const ok = await grant(selectedUser.value.username);
  if (!ok) {
    grantError.value = error.value ?? "Grant failed";
  } else {
    selectedUser.value = null;
  }
}

const revokingUser = ref<string | null>(null);
async function onRevoke(username: string) {
  revokingUser.value = username;
  try {
    await revoke(username);
  } finally {
    revokingUser.value = null;
  }
}

function formatGrantedAt(grantedAt?: string | null): string {
  if (!grantedAt) return "—";
  try {
    return new Date(grantedAt).toLocaleString();
  } catch {
    return grantedAt;
  }
}
</script>

<template>
  <div :id="AdminFragments.INSTANCE_ADMINS" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <div class="d-flex align-center ga-3">
        <h4 class="text-h4">Instance Administrators</h4>
        <v-btn
          icon="mdi-refresh"
          variant="text"
          size="small"
          :loading="isLoading"
          data-testid="instance-admins-refresh"
          @click="refresh"
        />
      </div>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Users with the <code>instance-admin</code> role can configure this
      Shepard instance, manage other admins, and act on behalf of any user.
      Grants are recorded in Neo4j as <code>:InstanceAdminGrant</code> nodes;
      IdP-sourced grants are also surfaced here. The OIDC role itself is
      managed in your identity provider and not affected by this pane.
    </p>

    <v-alert
      v-if="error"
      type="error"
      variant="tonal"
      density="compact"
      data-testid="instance-admins-error"
    >
      {{ error }}
    </v-alert>

    <!-- Grant section -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-shield-plus-outline</v-icon>
        Grant instance-admin
      </v-card-title>
      <v-card-text>
        <div class="d-flex flex-wrap align-center ga-3">
          <div style="min-width: 320px; flex: 1 1 320px">
            <UserSearchAutocomplete
              label="User to promote"
              @user-selected="(u) => (selectedUser = u)"
            />
          </div>
          <v-btn
            color="primary"
            variant="tonal"
            :disabled="!selectedUser || isAlreadyAdmin || isActing"
            :loading="isActing"
            prepend-icon="mdi-shield-crown-outline"
            data-testid="instance-admins-grant-btn"
            @click="onGrant"
          >
            Grant
          </v-btn>
        </div>
        <v-alert
          v-if="isAlreadyAdmin"
          type="info"
          variant="tonal"
          density="compact"
          class="mt-3"
          data-testid="instance-admins-already-admin"
        >
          {{ selectedUser?.username }} is already an instance-admin.
        </v-alert>
        <v-alert
          v-if="grantError"
          type="error"
          variant="tonal"
          density="compact"
          class="mt-3"
        >
          {{ grantError }}
        </v-alert>
      </v-card-text>
    </v-card>

    <!-- Current grants table -->
    <v-card variant="outlined">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="primary">mdi-shield-account-outline</v-icon>
        Current admins ({{ grants.length }})
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="isLoading && grants.length === 0" indeterminate />
        <p
          v-else-if="grants.length === 0"
          class="text-body-2 text-medium-emphasis"
        >
          No instance-admins recorded.
        </p>
        <v-table v-else density="compact" data-testid="instance-admins-table">
          <thead>
            <tr>
              <th>Username</th>
              <th>Source</th>
              <th>Granted by</th>
              <th>Granted at</th>
              <th class="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in grants" :key="`${g.username}-${g.source}`">
              <td>
                <code>{{ g.username }}</code>
              </td>
              <td>
                <v-chip
                  size="x-small"
                  :color="g.source === 'IdP' ? 'info' : 'primary'"
                  variant="tonal"
                >
                  {{ g.source }}
                </v-chip>
              </td>
              <td>{{ g.grantedBy ?? "—" }}</td>
              <td>{{ formatGrantedAt(g.grantedAt) }}</td>
              <td class="text-right">
                <v-btn
                  v-if="g.source !== 'IdP'"
                  size="x-small"
                  variant="text"
                  color="error"
                  prepend-icon="mdi-shield-remove-outline"
                  :loading="revokingUser === g.username"
                  :disabled="isActing"
                  :data-testid="`instance-admins-revoke-${g.username}`"
                  @click="onRevoke(g.username)"
                >
                  Revoke
                </v-btn>
                <span
                  v-else
                  class="text-caption text-medium-emphasis"
                  title="Revoke in the identity provider"
                >
                  IdP-managed
                </span>
              </td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>
  </div>
</template>
