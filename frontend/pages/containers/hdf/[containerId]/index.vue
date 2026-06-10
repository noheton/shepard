<script setup lang="ts">
// /containers/hdf/[containerId] — full HDF5 container browser (A5-UI-PHASE-1).
//
// Backend endpoints used (V2CONV-A7-HDF: unified /v2/containers surface):
//   GET    /v2/containers/{appId}        — container metadata (kind=hdf)
//   DELETE /v2/containers/{appId}        — delete container
//   GET    /v2/containers/{appId}/file   — download raw HDF5
//
// hsdsDomain + description arrive in the unified ContainerV2IO `payload` map.
//
// Dataset/group browsing (HSDS sidecar proxy) and per-container file upload
// are not yet in the backend (A5b/A5c). The page shows a clear empty state
// for those sections pointing at the design doc.

const route = useRoute();
const router = useRouter();

// containerId is the appId (UUID v7) for HDF containers — not a numeric id.
const appId = computed(() => String(route.params.containerId ?? ""));

// ─── v2 base URL helper (same derivation as FileContainerAccessor) ──────────
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

// ─── Container metadata ──────────────────────────────────────────────────────
// Unified ContainerV2IO: common fields flat; hdf-specific fields under `payload`.
interface ContainerV2IO {
  id: number;
  appId: string;
  name: string;
  kind?: string;
  attributes?: Record<string, string>;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
  payload?: { hsdsDomain?: string | null; description?: string | null } | null;
}

// View-model flattening the unified payload back into the fields the template reads.
interface HdfContainerData extends ContainerV2IO {
  description?: string | null;
  hsdsDomain?: string | null;
}

const container = ref<HdfContainerData | null>(null);
const loadingContainer = ref(true);
const containerError = ref<string | null>(null);

// Permissions are coarse: owner can delete; we infer from role endpoint.
// For simplicity, show delete only when the caller has the owner role (standard
// pattern). A dedicated roles fetch would require a role endpoint not yet
// present for HDF — omit the delete guard for now; the DELETE will 403 server-side.
const canDelete = ref(false);

async function fetchContainer() {
  loadingContainer.value = true;
  containerError.value = null;
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  if (!token) {
    containerError.value = "Not authenticated";
    loadingContainer.value = false;
    return;
  }
  try {
    const resp = await fetch(
      `${v2BaseUrl()}/v2/containers/${encodeURIComponent(appId.value)}`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
        },
      },
    );
    if (resp.status === 404) {
      containerError.value =
        "Container not found. The HDF5 plugin may not be enabled on this instance.";
      return;
    }
    if (!resp.ok) {
      containerError.value = `Server returned HTTP ${resp.status}`;
      return;
    }
    const io = (await resp.json()) as ContainerV2IO;
    // Flatten the unified payload map into the view-model the template reads.
    container.value = {
      ...io,
      hsdsDomain: io.payload?.hsdsDomain ?? null,
      description: io.payload?.description ?? null,
    };
    // Show delete button for the owner field (owners can delete).
    // Permissions check: the server will 403 on delete if the caller isn't owner.
    canDelete.value = true;
  } catch (e) {
    containerError.value = e instanceof Error ? e.message : "Unexpected error";
  } finally {
    loadingContainer.value = false;
  }
}

// ─── Delete ──────────────────────────────────────────────────────────────────
const deleteDialog = ref(false);
const deleting = ref(false);

async function confirmDelete() {
  deleting.value = true;
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  if (!token) {
    handleError(new Error("Not authenticated"), "deleting HDF container");
    deleting.value = false;
    return;
  }
  try {
    const resp = await fetch(
      `${v2BaseUrl()}/v2/containers/${encodeURIComponent(appId.value)}`,
      {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      },
    );
    if (resp.ok || resp.status === 204) {
      emitSuccess(`Deleted HDF container "${container.value?.name ?? appId.value}"`);
      await router.push(containersPath);
    } else {
      handleError(
        new Error(`HTTP ${resp.status}`),
        "deleting HDF container",
      );
    }
  } catch (e) {
    handleError(e as Error, "deleting HDF container");
  } finally {
    deleting.value = false;
    deleteDialog.value = false;
  }
}

// ─── Download ────────────────────────────────────────────────────────────────
const downloadUrl = computed(
  () =>
    `${v2BaseUrl()}/v2/containers/${encodeURIComponent(appId.value)}/file`,
);

// ─── Attributes display ──────────────────────────────────────────────────────
const attributeEntries = computed<{ key: string; value: string }[]>(() => {
  const attrs = container.value?.attributes;
  if (!attrs) return [];
  return Object.entries(attrs).map(([key, value]) => ({ key, value }));
});

// ─── Lifecycle ───────────────────────────────────────────────────────────────
useHead({
  title: () =>
    container.value?.name
      ? `${container.value.name} (HDF5) — shepard`
      : "HDF5 Container — shepard",
});

fetchContainer();
</script>

<template>
  <v-container fluid style="max-width: 1200px; margin: auto">
    <!-- Loading state -->
    <CenteredLoadingSpinner v-if="loadingContainer" />

    <!-- Error state (feature off / not found) -->
    <v-alert
      v-else-if="containerError"
      type="warning"
      variant="tonal"
      class="mb-4"
      :title="'HDF5 container unavailable'"
      :text="containerError"
      prepend-icon="mdi-flask-outline"
    >
      <template #append>
        <v-btn
          size="small"
          variant="text"
          href="https://github.com/noheton/shepard/blob/main/aidocs/data/35-hdf5-hsds-implementation-design.md"
          target="_blank"
          prepend-icon="mdi-open-in-new"
        >
          A5 design doc
        </v-btn>
      </template>
    </v-alert>

    <!-- Main content -->
    <template v-else-if="container">
      <!-- Breadcrumbs -->
      <v-row no-gutters class="mb-1">
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              { title: 'Containers', to: containersPath },
              {
                title: container.name,
                to: '/containers/hdf/' + appId,
              },
            ]"
          />
        </v-col>
      </v-row>

      <!-- Title + actions row -->
      <v-row no-gutters class="mb-4">
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <v-col class="ml-n1 pb-2" cols="12">
                <h1 class="text-h4">{{ container.name }}</h1>
                <p
                  v-if="container.description"
                  class="text-body-2 text-medium-emphasis mt-1"
                >
                  {{ container.description }}
                </p>
              </v-col>
            </v-row>
            <!-- Metadata chips -->
            <v-row class="text-body-2 text-medium-emphasis mb-2" no-gutters>
              <v-col cols="auto" class="mr-4">
                <strong>Container Type:</strong> HDF5 Container
              </v-col>
              <v-col cols="auto" class="mr-4">
                <strong>ID:</strong> {{ container.id }}
              </v-col>
              <v-col v-if="container.createdAt" cols="auto" class="mr-4">
                <strong>Created:</strong>
                {{ new Date(container.createdAt).toLocaleDateString() }}
                {{ container.createdBy ? `by ${container.createdBy}` : "" }}
              </v-col>
              <v-col v-if="container.updatedAt" cols="auto" class="mr-4">
                <strong>Updated:</strong>
                {{ new Date(container.updatedAt).toLocaleDateString() }}
              </v-col>
              <v-spacer />
              <v-col cols="auto" class="d-flex align-center ga-2">
                <!-- Download -->
                <v-btn
                  size="small"
                  variant="tonal"
                  prepend-icon="mdi-download"
                  color="primary"
                  :href="downloadUrl"
                >
                  Download HDF5
                </v-btn>
                <!-- Delete -->
                <v-btn
                  v-if="canDelete"
                  size="small"
                  variant="tonal"
                  color="error"
                  prepend-icon="mdi-delete-outline"
                  @click="deleteDialog = true"
                >
                  Delete
                </v-btn>
              </v-col>
            </v-row>
          </v-container>
        </v-col>
      </v-row>

      <!-- HSDS info chip -->
      <v-row v-if="container.hsdsDomain" no-gutters class="mb-4">
        <v-col cols="12">
          <v-chip
            size="small"
            variant="tonal"
            prepend-icon="mdi-server-outline"
            color="secondary"
          >
            HSDS domain: {{ container.hsdsDomain }}
          </v-chip>
        </v-col>
      </v-row>

      <!-- Dataset browser — not yet available -->
      <ExpansionPanels class="mb-4" :default-open="[0]">
        <ExpansionPanelItem title="HDF5 Datasets &amp; Groups">
          <v-empty-state
            class="ma-4"
            icon="mdi-folder-outline"
            headline="Dataset browser not yet available"
            text="In-browser HDF5 dataset and group navigation (A5b / A5c) is under development. Download the full HDF5 file above to explore it locally with h5py or HDFView. To access the HSDS sidecar directly, see the A5 design doc."
          >
            <template #actions>
              <v-btn
                variant="tonal"
                size="small"
                prepend-icon="mdi-download"
                color="primary"
                :href="downloadUrl"
              >
                Download HDF5 file
              </v-btn>
              <v-btn
                variant="text"
                size="small"
                prepend-icon="mdi-open-in-new"
                href="https://github.com/noheton/shepard/blob/main/aidocs/data/35-hdf5-hsds-implementation-design.md"
                target="_blank"
              >
                A5 design doc
              </v-btn>
            </template>
          </v-empty-state>
        </ExpansionPanelItem>
      </ExpansionPanels>

      <!-- Attributes -->
      <ExpansionPanels
        v-if="attributeEntries.length > 0"
        class="mb-4"
        :default-open="[0]"
      >
        <ExpansionPanelItem title="Attributes" :count="attributeEntries.length">
          <v-table density="compact" class="ma-2">
            <thead>
              <tr>
                <th>Key</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="attr in attributeEntries" :key="attr.key">
                <td class="font-weight-medium">{{ attr.key }}</td>
                <td>{{ attr.value }}</td>
              </tr>
            </tbody>
          </v-table>
        </ExpansionPanelItem>
      </ExpansionPanels>

      <!-- Referenced by — endpoint not yet implemented for HDF containers -->
      <ExpansionPanels class="mb-4">
        <ExpansionPanelItem title="Referenced by">
          <div class="pa-4 text-body-2 text-medium-emphasis">
            Linked-data-objects lookup for HDF5 containers is not yet
            implemented (planned for A5c). Once shipped, DataObjects that hold
            an HDF reference to this container will appear here.
          </div>
        </ExpansionPanelItem>
      </ExpansionPanels>
    </template>

    <!-- Delete confirmation dialog -->
    <v-dialog v-model="deleteDialog" max-width="480">
      <v-card>
        <v-card-title class="text-subtitle-1">Delete HDF5 container?</v-card-title>
        <v-card-text>
          <p>
            This will permanently delete
            <strong>{{ container?.name }}</strong> and drop its HSDS domain.
            Any HDF5 data stored in the sidecar will be lost.
          </p>
          <p class="text-caption text-error mt-2">This action cannot be undone.</p>
        </v-card-text>
        <v-card-actions class="justify-end">
          <v-btn variant="text" :disabled="deleting" @click="deleteDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="error"
            variant="tonal"
            :loading="deleting"
            @click="confirmDelete"
          >
            Delete
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>
