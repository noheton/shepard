<script lang="ts" setup>
/**
 * U1c2 — Role-in-current-context chips for the collection sidebar header.
 *
 * Renders a chip for the user's effective role (Owner / Editor / Reader)
 * and, when applicable, an Instance Admin chip alongside.
 *
 * Renders nothing when `roleLabel` is null (no access, not yet loaded,
 * or the user is unauthenticated).
 */

interface CollectionRoleChipProps {
  roleLabel: string | null;
  isInstanceAdmin: boolean;
}

const props = defineProps<CollectionRoleChipProps>();

const roleColor = computed<string>(() => {
  if (props.roleLabel === "Owner") return "primary";
  if (props.roleLabel === "Editor") return "secondary";
  return "";
});
</script>

<template>
  <div v-if="roleLabel || isInstanceAdmin" class="d-flex align-center ga-1 mt-1">
    <v-chip
      v-if="roleLabel"
      :color="roleColor"
      size="small"
      variant="tonal"
      density="compact"
    >
      {{ roleLabel }}
    </v-chip>
    <v-chip
      v-if="isInstanceAdmin"
      color="warning"
      size="small"
      variant="tonal"
      density="compact"
    >
      Admin
    </v-chip>
  </div>
</template>
