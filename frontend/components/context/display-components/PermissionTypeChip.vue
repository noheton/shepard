<script setup lang="ts">
// UX-WALK-2026-05-29-08: fallback chip for the Access column when FAIR
// `accessRights` is not set. Maps PermissionType → controlled-vocabulary
// label + Vuetify color so the column is never a bare `—`.
//
// Color encoding:
//   Public         → success (green)   → "Open"
//   PublicReadable → info    (blue)    → "Shared"
//   Private        → warning (orange)  → "Restricted"
//
// Unknown / undefined values render a neutral chip rather than nothing so
// the UI is resilient to future PermissionType additions.
import { PermissionType } from "@dlr-shepard/backend-client";

const props = defineProps<{
  /** The permissionType value from the Collection or DataObject entity. */
  permissionType: string | null | undefined;
}>();

interface PermissionTypeOption {
  label: string;
  color: string;
  description: string;
}

const PERMISSION_TYPE_MAP: Record<string, PermissionTypeOption> = {
  [PermissionType.Public]: {
    label: "Open",
    color: "success",
    description: "Public — anyone can read without authentication.",
  },
  [PermissionType.PublicReadable]: {
    label: "Shared",
    color: "info",
    description: "Public readable — authenticated users can read; write is restricted.",
  },
  [PermissionType.Private]: {
    label: "Restricted",
    color: "warning",
    description: "Private — only authorised members can access.",
  },
};

const option = computed<PermissionTypeOption | undefined>(
  () => (props.permissionType ? PERMISSION_TYPE_MAP[props.permissionType] : undefined),
);

const color = computed(() => option.value?.color ?? "default");
const label = computed(() =>
  option.value?.label ?? props.permissionType ?? "—",
);
const description = computed(() => option.value?.description ?? "");
</script>

<template>
  <v-tooltip :text="description" location="bottom" :disabled="!description">
    <template #activator="{ props: tooltipProps }">
      <v-chip
        v-bind="tooltipProps"
        :color="color"
        size="small"
        variant="tonal"
        prepend-icon="mdi-lock-outline"
        label
        data-testid="permission-type-chip"
      >
        {{ label }}
      </v-chip>
    </template>
  </v-tooltip>
</template>
