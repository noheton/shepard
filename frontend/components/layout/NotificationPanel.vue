<template>
  <!-- Notification panel — slides in from top-right when the bell is clicked -->
  <v-navigation-drawer
    v-model="open"
    location="right"
    width="400"
    temporary
    class="notification-panel"
  >
    <v-toolbar density="compact" class="bg-canvas">
      <v-toolbar-title class="text-body-1 font-weight-medium">Notifications</v-toolbar-title>
      <template #append>
        <v-btn
          size="small"
          variant="text"
          :disabled="unreadCount === 0"
          @click="markAllRead"
        >
          Mark all read
        </v-btn>
        <v-btn icon="mdi-close" size="small" variant="text" @click="open = false" />
      </template>
    </v-toolbar>

    <v-divider />

    <div v-if="isLoading" class="d-flex justify-center pa-6">
      <v-progress-circular indeterminate size="28" />
    </div>

    <div v-else-if="notifications.length === 0" class="pa-6 text-center text-medium-emphasis">
      <v-icon size="40" class="mb-2">mdi-bell-off-outline</v-icon>
      <div class="text-body-2">No notifications</div>
    </div>

    <v-list v-else lines="two" class="pa-0">
      <template v-for="(n, i) in notifications" :key="n.appId">
        <v-divider v-if="i > 0" />
        <v-list-item
          :class="{ 'unread-item': !n.read }"
          :prepend-icon="categoryIcon(n.category)"
          :base-color="categoryColor(n.category)"
          rounded="0"
        >
          <template #title>
            <span class="text-body-2 font-weight-medium">{{ n.title }}</span>
          </template>
          <template #subtitle>
            <div class="text-caption mt-1">{{ formatBody(n.body) }}</div>
            <div class="text-caption text-medium-emphasis mt-1">{{ formatTime(n.createdAtMillis) }}</div>
          </template>
          <template #append>
            <div class="d-flex flex-column align-end ga-1">
              <v-btn
                v-if="n.actionUrl"
                size="x-small"
                variant="tonal"
                :href="n.actionUrl"
                @click="markRead(n.appId)"
              >
                Go
              </v-btn>
              <v-btn
                v-if="!n.read"
                size="x-small"
                variant="text"
                @click="markRead(n.appId)"
              >
                Read
              </v-btn>
              <v-btn
                size="x-small"
                variant="text"
                color="error"
                icon="mdi-close"
                @click="dismiss(n.appId)"
              />
            </div>
          </template>
        </v-list-item>
      </template>
    </v-list>
  </v-navigation-drawer>
</template>

<script setup lang="ts">
import type { NotificationIO } from "~/composables/context/useFetchNotifications";

const props = defineProps<{
  modelValue: boolean;
  notifications: NotificationIO[];
  unreadCount: number;
  isLoading: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [val: boolean];
  markRead: [appId: string];
  dismiss: [appId: string];
}>();

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});

function markRead(appId: string) { emit("markRead", appId); }
function dismiss(appId: string) { emit("dismiss", appId); }

function markAllRead() {
  props.notifications
    .filter(n => !n.read)
    .forEach(n => emit("markRead", n.appId));
}

function categoryIcon(category: string): string {
  switch (category) {
    case "WARNING": return "mdi-alert-outline";
    case "ACTION_REQUIRED": return "mdi-bell-alert-outline";
    default: return "mdi-information-outline";
  }
}

function categoryColor(category: string): string {
  switch (category) {
    case "WARNING": return "warning";
    case "ACTION_REQUIRED": return "error";
    default: return "primary";
  }
}

function formatBody(body: string): string {
  // Strip markdown for the subtitle snippet — full body could be rendered in future
  return body.replace(/[*_`#>\[\]]/g, "").slice(0, 120);
}

function formatTime(millis: number): string {
  const d = new Date(millis);
  return d.toLocaleString(undefined, {
    dateStyle: "short",
    timeStyle: "short",
  });
}
</script>

<style scoped>
.notification-panel {
  top: 64px !important; /* align below app bar */
}
.unread-item {
  background-color: rgba(var(--v-theme-primary), 0.06);
  border-left: 3px solid rgb(var(--v-theme-primary));
}
</style>
