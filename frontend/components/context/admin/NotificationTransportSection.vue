<script setup lang="ts">
/**
 * NTF1-UI-TRANSPORT-CRUD-FOLLOWUP — per-kind section (SMTP or MATRIX) of
 * the notifications admin pane.
 *
 * Renders: list of configured transports (v-data-table) + collapsible
 * Add form + per-row Edit dialog + per-row Delete confirmation + per-row
 * Smoke-test button. Field set varies by `kind` but the plumbing (list +
 * create + edit dialog) is identical, so this component is parametrised
 * rather than duplicated per kind.
 *
 * Write-only credential contract — see `buildPatchBody` in the composable.
 * Edit dialog leaves the password / access-token field blank on open; an
 * empty submit preserves the stored value, a non-empty submit rotates it.
 */
import type {
  NotificationTransport,
  TransportFormState,
  TransportKind,
} from "~/composables/context/admin/useNotificationTransports";

const props = defineProps<{
  kind: Extract<TransportKind, "SMTP" | "MATRIX">;
  items: NotificationTransport[];
  isSaving: boolean;
  isTesting: string | null;
}>();

const emit = defineEmits<{
  create: [form: TransportFormState];
  patch: [appId: string, form: TransportFormState];
  remove: [appId: string];
  test: [appId: string];
}>();

const filtered = computed(() =>
  props.items.filter((t) => t.kind === props.kind),
);

const isSmtp = computed(() => props.kind === "SMTP");
const kindLabel = computed(() => (isSmtp.value ? "SMTP" : "Matrix"));
const icon = computed(() => (isSmtp.value ? "mdi-email-outline" : "mdi-matrix"));

// ─── Add-form state ───────────────────────────────────────────────────────
const showAdd = ref(false);
const addForm = ref<TransportFormState>(defaultForm());

function defaultForm(): TransportFormState {
  return {
    kind: props.kind,
    name: "",
    enabled: true, // sensible default — operator just configured a working sender
    smtpHost: "",
    smtpPort: 587,
    smtpUsername: "",
    smtpPassword: "",
    smtpFrom: "",
    smtpTls: true,
    matrixHomeserver: "",
    matrixAccessToken: "",
    matrixDefaultRoom: "",
  };
}

function resetAdd() {
  addForm.value = defaultForm();
  showAdd.value = false;
}

function canSubmitAdd(): boolean {
  if (!addForm.value.name.trim()) return false;
  if (isSmtp.value) {
    return !!addForm.value.smtpHost?.trim() && !!addForm.value.smtpFrom?.trim();
  }
  return !!addForm.value.matrixHomeserver?.trim();
}

async function onCreate() {
  if (!canSubmitAdd()) return;
  emit("create", { ...addForm.value });
  resetAdd();
}

// ─── Edit dialog ──────────────────────────────────────────────────────────
const editOpen = ref(false);
const editing = ref<NotificationTransport | null>(null);
const editForm = ref<TransportFormState>(defaultForm());

function openEdit(t: NotificationTransport) {
  editing.value = t;
  // Credential fields ALWAYS blank on open — caller re-enters to rotate.
  editForm.value = {
    kind: props.kind,
    name: t.name ?? "",
    enabled: t.enabled,
    smtpHost: t.smtpHost ?? "",
    smtpPort: t.smtpPort ?? null,
    smtpUsername: t.smtpUsername ?? "",
    smtpPassword: "", // write-only — blank means "keep existing"
    smtpFrom: t.smtpFrom ?? "",
    smtpTls: t.smtpTls ?? undefined,
    matrixHomeserver: t.matrixHomeserver ?? "",
    matrixAccessToken: "", // write-only — blank means "keep existing"
    matrixDefaultRoom: t.matrixDefaultRoom ?? "",
  };
  editOpen.value = true;
}

function onSavePatch() {
  if (!editing.value) return;
  emit("patch", editing.value.appId, { ...editForm.value });
  editOpen.value = false;
  editing.value = null;
}

// ─── Delete confirmation ──────────────────────────────────────────────────
const deleteOpen = ref(false);
const deleting = ref<NotificationTransport | null>(null);
function askDelete(t: NotificationTransport) {
  deleting.value = t;
  deleteOpen.value = true;
}
function confirmDelete() {
  if (deleting.value) emit("remove", deleting.value.appId);
  deleteOpen.value = false;
  deleting.value = null;
}

function lastTestChip(t: NotificationTransport): { color: string; text: string } | null {
  if (!t.lastTestResult) return null;
  const when = t.lastTestedAt ? formatRelative(t.lastTestedAt) : "";
  return {
    color: t.lastTestResult === "OK" ? "success" : "error",
    text: `${t.lastTestResult}${when ? " · " + when : ""}`,
  };
}

function formatRelative(ms: number): string {
  const diff = Date.now() - ms;
  if (diff < 60_000) return "just now";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
  return `${Math.floor(diff / 86_400_000)}d ago`;
}

const headers = computed(() => [
  { title: "Name", key: "name" },
  { title: "Enabled", key: "enabled", width: 100 },
  { title: "Last test", key: "lastTest", width: 220 },
  { title: "Actions", key: "actions", width: 200, sortable: false },
]);
</script>

<template>
  <v-card variant="outlined" :data-testid="`transport-card-${kind.toLowerCase()}`">
    <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
      <v-icon color="primary">{{ icon }}</v-icon>
      {{ kindLabel }}
      <v-chip size="x-small" color="info" variant="tonal" class="ml-2">
        {{ filtered.length }} configured
      </v-chip>
      <v-spacer />
      <v-btn
        size="small"
        variant="tonal"
        prepend-icon="mdi-plus"
        :data-testid="`transport-add-${kind.toLowerCase()}`"
        @click="showAdd = !showAdd"
      >
        Add transport
      </v-btn>
    </v-card-title>

    <v-card-text>
      <v-data-table
        :headers="headers"
        :items="filtered"
        :items-per-page="10"
        density="compact"
        :data-testid="`transport-table-${kind.toLowerCase()}`"
      >
        <template #[`item.enabled`]="{ item }">
          <v-chip
            size="x-small"
            :color="item.enabled ? 'success' : 'default'"
            variant="tonal"
          >
            {{ item.enabled ? "yes" : "no" }}
          </v-chip>
        </template>
        <template #[`item.lastTest`]="{ item }">
          <v-chip
            v-if="lastTestChip(item)"
            size="x-small"
            :color="lastTestChip(item)!.color"
            variant="tonal"
          >
            {{ lastTestChip(item)!.text }}
          </v-chip>
          <span v-else class="text-caption text-medium-emphasis">never tested</span>
        </template>
        <template #[`item.actions`]="{ item }">
          <div class="d-flex ga-1">
            <v-btn
              size="x-small"
              variant="text"
              :loading="isTesting === item.appId"
              :data-testid="`transport-test-${item.appId}`"
              @click="emit('test', item.appId)"
            >
              Test
            </v-btn>
            <v-btn
              size="x-small"
              variant="text"
              :data-testid="`transport-edit-${item.appId}`"
              @click="openEdit(item)"
            >
              Edit
            </v-btn>
            <v-btn
              size="x-small"
              variant="text"
              color="error"
              :data-testid="`transport-delete-${item.appId}`"
              @click="askDelete(item)"
            >
              Delete
            </v-btn>
          </div>
        </template>
        <template #no-data>
          <div class="text-caption text-medium-emphasis py-4">
            No {{ kindLabel }} transports configured yet.
          </div>
        </template>
      </v-data-table>

      <!-- ───── Add form (collapsible) ───── -->
      <v-expand-transition>
        <div v-if="showAdd" class="mt-4">
          <v-divider class="mb-3" />
          <h5 class="text-subtitle-1 mb-2">Add {{ kindLabel }} transport</h5>
          <v-row dense>
            <v-col cols="12" sm="6">
              <v-text-field
                v-model="addForm.name"
                label="Name"
                variant="outlined"
                density="comfortable"
                :data-testid="`field-name-${kind.toLowerCase()}`"
              />
            </v-col>
            <v-col cols="12" sm="6" class="d-flex align-center">
              <v-switch
                v-model="addForm.enabled"
                label="Enabled"
                color="primary"
                hide-details
                :data-testid="`field-enabled-${kind.toLowerCase()}`"
              />
            </v-col>
            <template v-if="isSmtp">
              <v-col cols="12" sm="8">
                <v-text-field v-model="addForm.smtpHost" label="SMTP host" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4">
                <v-text-field v-model.number="addForm.smtpPort" label="Port" type="number" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="addForm.smtpUsername" label="Username" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="addForm.smtpPassword"
                  label="Password"
                  type="password"
                  variant="outlined"
                  density="comfortable"
                  hint="Write-only. Never returned by the API."
                  persistent-hint
                />
              </v-col>
              <v-col cols="12" sm="8">
                <v-text-field v-model="addForm.smtpFrom" label="From address" placeholder="noreply@example.org" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4" class="d-flex align-center">
                <v-switch v-model="addForm.smtpTls" label="TLS" color="primary" hide-details />
              </v-col>
            </template>
            <template v-else>
              <v-col cols="12">
                <v-text-field v-model="addForm.matrixHomeserver" label="Homeserver URL" placeholder="https://matrix.example.org" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12">
                <v-text-field
                  v-model="addForm.matrixAccessToken"
                  label="Access token"
                  type="password"
                  variant="outlined"
                  density="comfortable"
                  hint="Write-only. Never returned by the API."
                  persistent-hint
                />
              </v-col>
              <v-col cols="12">
                <v-text-field v-model="addForm.matrixDefaultRoom" label="Default room ID" placeholder="!roomid:example.org" variant="outlined" density="comfortable" />
              </v-col>
            </template>
          </v-row>
          <div class="d-flex ga-2 mt-2">
            <v-btn
              color="primary"
              variant="tonal"
              :loading="isSaving"
              :disabled="!canSubmitAdd()"
              :data-testid="`transport-create-${kind.toLowerCase()}`"
              @click="onCreate"
            >
              Create
            </v-btn>
            <v-btn variant="text" @click="resetAdd">Cancel</v-btn>
          </div>
        </div>
      </v-expand-transition>
    </v-card-text>

    <!-- ───── Edit dialog ───── -->
    <v-dialog v-model="editOpen" max-width="640">
      <v-card>
        <v-card-title>Edit {{ kindLabel }} transport</v-card-title>
        <v-card-text>
          <v-row dense>
            <v-col cols="12" sm="6">
              <v-text-field v-model="editForm.name" label="Name" variant="outlined" density="comfortable" />
            </v-col>
            <v-col cols="12" sm="6" class="d-flex align-center">
              <v-switch v-model="editForm.enabled" label="Enabled" color="primary" hide-details />
            </v-col>
            <template v-if="isSmtp">
              <v-col cols="12" sm="8">
                <v-text-field v-model="editForm.smtpHost" label="SMTP host" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4">
                <v-text-field v-model.number="editForm.smtpPort" label="Port" type="number" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field v-model="editForm.smtpUsername" label="Username" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="editForm.smtpPassword"
                  label="Password (leave blank to keep)"
                  type="password"
                  variant="outlined"
                  density="comfortable"
                  hint="Enter a value to rotate; blank preserves the stored password."
                  persistent-hint
                  data-testid="edit-password"
                />
              </v-col>
              <v-col cols="12" sm="8">
                <v-text-field v-model="editForm.smtpFrom" label="From address" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4" class="d-flex align-center">
                <v-switch v-model="editForm.smtpTls" label="TLS" color="primary" hide-details />
              </v-col>
            </template>
            <template v-else>
              <v-col cols="12">
                <v-text-field v-model="editForm.matrixHomeserver" label="Homeserver URL" variant="outlined" density="comfortable" />
              </v-col>
              <v-col cols="12">
                <v-text-field
                  v-model="editForm.matrixAccessToken"
                  label="Access token (leave blank to keep)"
                  type="password"
                  variant="outlined"
                  density="comfortable"
                  hint="Enter a value to rotate; blank preserves the stored token."
                  persistent-hint
                  data-testid="edit-access-token"
                />
              </v-col>
              <v-col cols="12">
                <v-text-field v-model="editForm.matrixDefaultRoom" label="Default room ID" variant="outlined" density="comfortable" />
              </v-col>
            </template>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editOpen = false">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="tonal"
            :loading="isSaving"
            data-testid="edit-save"
            @click="onSavePatch"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- ───── Delete confirmation ───── -->
    <v-dialog v-model="deleteOpen" max-width="480">
      <v-card>
        <v-card-title>Delete transport?</v-card-title>
        <v-card-text>
          <p class="text-body-2">
            Delete <strong>{{ deleting?.name }}</strong> ({{ kindLabel }})? This
            cannot be undone. Historical Activity rows referencing this
            transport are preserved.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="deleteOpen = false">Cancel</v-btn>
          <v-btn color="error" variant="tonal" data-testid="confirm-delete" @click="confirmDelete">
            Delete
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>
