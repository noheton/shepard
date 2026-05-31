<script setup lang="ts">
/**
 * NTF1-UI-TRANSPORT-CRUD-FOLLOWUP — standalone create / edit dialog for
 * :NotificationTransport rows.
 *
 * Used by AdminNotificationsPane and NotificationTransportSection as an
 * alternative to the inline-collapsible add form: receives an optional
 * `initial` prop (undefined = create, populated = edit) and emits
 * `save` with the form state when the operator hits Save.
 *
 * Validation (name required; smtpHost required when kind=SMTP;
 * matrixHomeserver required when kind=MATRIX) is done inline — no
 * external validation library required.
 *
 * Credential fields (smtpPassword, matrixAccessToken) are always blank
 * on open in edit mode — the write-only contract described in
 * useNotificationTransports.buildPatchBody applies.
 */
import type {
  NotificationTransport,
  TransportFormState,
  TransportKind,
} from "~/composables/context/admin/useNotificationTransports";

const props = defineProps<{
  modelValue: boolean;
  /** Populated for edit; absent for create. */
  initial?: NotificationTransport | null;
  isSaving?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [open: boolean];
  save: [form: TransportFormState];
}>();

// ─── local form state ────────────────────────────────────────────────────────

function blankForm(kind: TransportKind = "SMTP"): TransportFormState {
  return {
    kind,
    name: "",
    enabled: true,
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

const form = ref<TransportFormState>(blankForm());

/**
 * Whenever the dialog opens, reset form to initial values (edit mode) or
 * blank (create mode). Credential fields start blank in both modes.
 */
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    if (props.initial) {
      form.value = {
        kind: (props.initial.kind as TransportKind) ?? "SMTP",
        name: props.initial.name ?? "",
        enabled: props.initial.enabled ?? true,
        smtpHost: props.initial.smtpHost ?? "",
        smtpPort: props.initial.smtpPort ?? 587,
        smtpUsername: props.initial.smtpUsername ?? "",
        smtpPassword: "", // write-only — always blank on open
        smtpFrom: props.initial.smtpFrom ?? "",
        smtpTls: props.initial.smtpTls ?? true,
        matrixHomeserver: props.initial.matrixHomeserver ?? "",
        matrixAccessToken: "", // write-only — always blank on open
        matrixDefaultRoom: props.initial.matrixDefaultRoom ?? "",
      };
    } else {
      form.value = blankForm();
    }
  },
  { immediate: false },
);

const isEdit = computed(() => !!props.initial);
const title = computed(() =>
  isEdit.value ? `Edit ${kindLabel.value} transport` : `Add ${kindLabel.value} transport`,
);

const kindOptions: { label: string; value: TransportKind }[] = [
  { label: "SMTP (email)", value: "SMTP" },
  { label: "Matrix (chat)", value: "MATRIX" },
  { label: "In-app (bell panel)", value: "INAPP" },
];

const kindLabel = computed(() => {
  switch (form.value.kind) {
    case "SMTP":
      return "SMTP";
    case "MATRIX":
      return "Matrix";
    case "INAPP":
      return "In-app";
    default:
      return "transport";
  }
});

const isSmtp = computed(() => form.value.kind === "SMTP");
const isMatrix = computed(() => form.value.kind === "MATRIX");

// ─── validation ──────────────────────────────────────────────────────────────

const nameError = computed(() =>
  form.value.name.trim().length === 0 ? "Name is required." : null,
);

const smtpHostError = computed(() => {
  if (!isSmtp.value) return null;
  return (form.value.smtpHost ?? "").trim().length === 0
    ? "SMTP host is required."
    : null;
});

const matrixHomeserverError = computed(() => {
  if (!isMatrix.value) return null;
  return (form.value.matrixHomeserver ?? "").trim().length === 0
    ? "Homeserver URL is required."
    : null;
});

const canSave = computed(
  () =>
    !nameError.value &&
    !smtpHostError.value &&
    !matrixHomeserverError.value,
);

// ─── actions ─────────────────────────────────────────────────────────────────

function onCancel() {
  emit("update:modelValue", false);
}

function onSave() {
  if (!canSave.value) return;
  emit("save", { ...form.value });
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="640"
    :persistent="isSaving"
    data-testid="notification-transport-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title class="pt-4">{{ title }}</v-card-title>

      <v-card-text>
        <v-row dense>
          <!-- Kind selector (disabled in edit mode — kind is immutable after create) -->
          <v-col cols="12" sm="6">
            <v-select
              v-model="form.kind"
              :items="kindOptions"
              item-title="label"
              item-value="value"
              label="Transport kind"
              variant="outlined"
              density="comfortable"
              :disabled="isEdit"
              data-testid="field-kind"
            />
          </v-col>

          <!-- Enabled switch -->
          <v-col cols="12" sm="6" class="d-flex align-center">
            <v-switch
              v-model="form.enabled"
              label="Enabled"
              color="primary"
              hide-details
              data-testid="field-enabled"
            />
          </v-col>

          <!-- Name -->
          <v-col cols="12">
            <v-text-field
              v-model="form.name"
              label="Name"
              variant="outlined"
              density="comfortable"
              :error-messages="nameError ?? undefined"
              data-testid="field-name"
            />
          </v-col>

          <!-- ─── SMTP fields ───────────────────────────────────────────── -->
          <template v-if="isSmtp">
            <v-col cols="12" sm="8">
              <v-text-field
                v-model="form.smtpHost"
                label="SMTP host"
                placeholder="smtp.example.org"
                variant="outlined"
                density="comfortable"
                :error-messages="smtpHostError ?? undefined"
                data-testid="field-smtp-host"
              />
            </v-col>
            <v-col cols="12" sm="4">
              <v-text-field
                v-model.number="form.smtpPort"
                label="Port"
                type="number"
                variant="outlined"
                density="comfortable"
                data-testid="field-smtp-port"
              />
            </v-col>
            <v-col cols="12" sm="6">
              <v-text-field
                v-model="form.smtpUsername"
                label="Username"
                variant="outlined"
                density="comfortable"
                data-testid="field-smtp-username"
              />
            </v-col>
            <v-col cols="12" sm="6">
              <v-text-field
                v-model="form.smtpPassword"
                label="Password"
                :placeholder="isEdit ? 'Leave blank to keep existing' : ''"
                type="password"
                variant="outlined"
                density="comfortable"
                hint="Write-only. Never returned by the API."
                persistent-hint
                data-testid="field-smtp-password"
              />
            </v-col>
            <v-col cols="12" sm="8">
              <v-text-field
                v-model="form.smtpFrom"
                label="From address"
                placeholder="noreply@example.org"
                variant="outlined"
                density="comfortable"
                data-testid="field-smtp-from"
              />
            </v-col>
            <v-col cols="12" sm="4" class="d-flex align-center">
              <v-switch
                v-model="form.smtpTls"
                label="TLS"
                color="primary"
                hide-details
                data-testid="field-smtp-tls"
              />
            </v-col>
          </template>

          <!-- ─── Matrix fields ─────────────────────────────────────────── -->
          <template v-if="isMatrix">
            <v-col cols="12">
              <v-text-field
                v-model="form.matrixHomeserver"
                label="Homeserver URL"
                placeholder="https://matrix.example.org"
                variant="outlined"
                density="comfortable"
                :error-messages="matrixHomeserverError ?? undefined"
                data-testid="field-matrix-homeserver"
              />
            </v-col>
            <v-col cols="12">
              <v-text-field
                v-model="form.matrixAccessToken"
                label="Access token"
                :placeholder="isEdit ? 'Leave blank to keep existing' : ''"
                type="password"
                variant="outlined"
                density="comfortable"
                hint="Write-only. Never returned by the API."
                persistent-hint
                data-testid="field-matrix-access-token"
              />
            </v-col>
            <v-col cols="12">
              <v-text-field
                v-model="form.matrixDefaultRoom"
                label="Default room ID"
                placeholder="!roomid:example.org"
                variant="outlined"
                density="comfortable"
                data-testid="field-matrix-default-room"
              />
            </v-col>
          </template>
        </v-row>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          :disabled="isSaving"
          data-testid="dialog-cancel"
          @click="onCancel"
        >
          Cancel
        </v-btn>
        <v-btn
          color="primary"
          variant="tonal"
          :loading="isSaving"
          :disabled="!canSave"
          data-testid="dialog-save"
          @click="onSave"
        >
          {{ isEdit ? "Save" : "Create" }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
