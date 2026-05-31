<script setup lang="ts">
/**
 * NTF1 admin pane — replaces the `notifications-admin` placeholder shipped
 * 2026-05-24 (`placeholderRegistry.ts:60-69`).
 *
 * Backend reality (2026-05-31):
 *   • In-app transport      — shipped (NotificationService + bell panel)
 *   • SMTP transport        — backend not yet implemented (NTF1-BACKEND-SMTP)
 *   • Matrix transport      — backend not yet implemented (NTF1-BACKEND-MATRIX)
 *   • Transport CRUD model  — backend not yet implemented (NTF1-BACKEND-*)
 *
 * So this pane ships three honest cards rather than faking a transport
 * table against endpoints that don't exist:
 *   • In-app card — working smoke-test form against POST /v2/admin/notifications/test
 *   • SMTP card   — info banner citing NTF1-BACKEND-SMTP
 *   • Matrix card — info banner citing NTF1-BACKEND-MATRIX
 *
 * When the transport CRUD backend lands, this pane gets a real table on
 * top of these three cards and the form moves under per-transport rows.
 *
 * Cross-refs:
 *   • aidocs/16-dispatcher-backlog.md → PLACEHOLDER-REPLACE-NTF1
 *   • aidocs/integrations/40-notification-system.md (canonical design)
 *   • aidocs/agent-findings/ui-scrutinizer-2026-05-30.md §Placeholder catalogue row 3
 */
import { AdminFragments } from "./adminMenuItems";
import type {
  NotificationAudience,
  NotificationCategory,
} from "~/composables/context/admin/useNotificationsAdmin";
import {
  buildTestRequest,
  canSendTest,
  targetUsernameError as targetUsernameErrorFn,
  useNotificationsAdmin,
} from "~/composables/context/admin/useNotificationsAdmin";

const { isSending, lastResult, error, sendTest } = useNotificationsAdmin();

// ─── In-app smoke-test form state ─────────────────────────────────────────────
const audience = ref<NotificationAudience>("INSTANCE_ADMIN");
const targetUsername = ref("");
const category = ref<NotificationCategory>("INFO");
const title = ref("Test notification");
const body = ref("This is a test notification sent from the admin panel.");
const actionUrl = ref("");
const successOpen = ref(false);

const audienceOptions: { label: string; value: NotificationAudience }[] = [
  { label: "Instance admins", value: "INSTANCE_ADMIN" },
  { label: "Specific user", value: "USER" },
  { label: "Everyone", value: "ALL" },
];

const categoryOptions: { label: string; value: NotificationCategory }[] = [
  { label: "Info", value: "INFO" },
  { label: "Warning", value: "WARNING" },
  { label: "Action required", value: "ACTION_REQUIRED" },
];

const targetUsernameError = computed(() =>
  targetUsernameErrorFn(audience.value, targetUsername.value),
);

const canSend = computed(() =>
  canSendTest(title.value, audience.value, targetUsername.value),
);

async function onSend() {
  if (!canSend.value) return;
  successOpen.value = false;
  const payload = buildTestRequest({
    audience: audience.value,
    targetUsername: targetUsername.value,
    category: category.value,
    title: title.value,
    body: body.value,
    actionUrl: actionUrl.value,
  });
  const result = await sendTest(payload);
  if (result !== null) successOpen.value = true;
}
</script>

<template>
  <div :id="AdminFragments.NOTIFICATIONS_ADMIN" class="d-flex flex-column ga-4">
    <!-- Header row -->
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">Notification transports</h4>
    </div>

    <p class="text-body-2 text-medium-emphasis">
      Configure how Shepard delivers notifications and send smoke-tests per
      transport. Today only the <strong>in-app</strong> transport ships; SMTP
      and Matrix transports are designed
      (<code>aidocs/integrations/40-notification-system.md</code>) but their
      backend CRUD is still in the queue — those cards will gain forms once
      <code>NTF1-BACKEND-*</code> lands.
    </p>

    <!-- ───────────────── In-app transport (working) ────────────────────── -->
    <v-card variant="outlined" data-testid="transport-card-in-app">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="success">mdi-bell-outline</v-icon>
        In-app
        <v-chip size="x-small" color="success" variant="tonal" class="ml-2">
          enabled
        </v-chip>
        <v-spacer />
        <v-chip size="x-small" variant="outlined">always on</v-chip>
      </v-card-title>
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-4">
          In-app notifications appear in each recipient's bell panel within
          one poll cycle (~30 seconds). Hits
          <code>POST /v2/admin/notifications/test</code>.
        </p>

        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          class="mb-4"
          closable
          @click:close="error = null"
        >
          {{ error }}
        </v-alert>

        <v-alert
          v-if="successOpen && lastResult"
          type="success"
          variant="tonal"
          class="mb-4"
          closable
          @click:close="successOpen = false"
        >
          Test notification published.
        </v-alert>

        <v-row dense>
          <v-col cols="12" sm="6">
            <v-select
              v-model="audience"
              :items="audienceOptions"
              item-title="label"
              item-value="value"
              label="Audience"
              variant="outlined"
              density="comfortable"
            />
          </v-col>
          <v-col cols="12" sm="6">
            <v-select
              v-model="category"
              :items="categoryOptions"
              item-title="label"
              item-value="value"
              label="Severity"
              variant="outlined"
              density="comfortable"
            />
          </v-col>
          <v-col v-if="audience === 'USER'" cols="12">
            <v-text-field
              v-model="targetUsername"
              label="Target username"
              :error-messages="targetUsernameError ?? undefined"
              variant="outlined"
              density="comfortable"
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="title"
              label="Title"
              variant="outlined"
              density="comfortable"
            />
          </v-col>
          <v-col cols="12">
            <v-textarea
              v-model="body"
              label="Body (Markdown)"
              variant="outlined"
              density="comfortable"
              rows="3"
              auto-grow
            />
          </v-col>
          <v-col cols="12">
            <v-text-field
              v-model="actionUrl"
              label="Action URL (optional)"
              placeholder="https://…"
              variant="outlined"
              density="comfortable"
            />
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn
          color="primary"
          variant="tonal"
          prepend-icon="mdi-send-outline"
          :loading="isSending"
          :disabled="!canSend"
          data-testid="send-test-button"
          @click="onSend"
        >
          Send test
        </v-btn>
      </v-card-actions>
    </v-card>

    <!-- ───────────────── SMTP transport (backend pending) ──────────────── -->
    <v-card variant="outlined" data-testid="transport-card-smtp">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="medium-emphasis">mdi-email-outline</v-icon>
        SMTP
        <v-chip size="x-small" variant="tonal" class="ml-2">
          backend pending
        </v-chip>
      </v-card-title>
      <v-card-text>
        <v-alert type="info" variant="tonal">
          Email delivery via SMTP is designed but the transport-CRUD backend
          has not landed yet. The add/edit form (host, port, username,
          write-only password, sender address, TLS toggle) ships with
          <code>NTF1-BACKEND-SMTP</code>. Credentials will be write-only on
          GET responses — the UI will not display SMTP passwords once stored.
          See <code>aidocs/integrations/40-notification-system.md</code>.
        </v-alert>
      </v-card-text>
    </v-card>

    <!-- ───────────────── Matrix transport (backend pending) ────────────── -->
    <v-card variant="outlined" data-testid="transport-card-matrix">
      <v-card-title class="d-flex align-center ga-2 pt-4 pb-2">
        <v-icon color="medium-emphasis">mdi-matrix</v-icon>
        Matrix
        <v-chip size="x-small" variant="tonal" class="ml-2">
          backend pending
        </v-chip>
      </v-card-title>
      <v-card-text>
        <v-alert type="info" variant="tonal">
          Matrix delivery is designed (MTX1a, <code>shepard-plugin-matrix-notify</code>)
          but the transport-CRUD backend has not landed yet. The add/edit
          form (homeserver URL, write-only access token, default room ID)
          ships with <code>NTF1-BACKEND-MATRIX</code>. Access tokens will be
          write-only on GET responses — the UI will not display them once
          stored. See <code>aidocs/integrations/40-notification-system.md</code>.
        </v-alert>
      </v-card-text>
    </v-card>

    <div class="text-caption text-medium-emphasis">
      Future shape: per-transport edit / delete / smoke-test rows in a table.
      Tracked under <code>PLACEHOLDER-REPLACE-NTF1</code>
      (this pane) plus <code>NTF1-BACKEND-*</code> rows in
      <code>aidocs/16-dispatcher-backlog.md</code>.
    </div>
  </div>
</template>
