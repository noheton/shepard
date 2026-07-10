<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";
import Select from "~/components/common/Select.vue";

const props = defineProps<{
  limitedPermissionSet?: PermissionType[];
  noRequiredHint?: boolean;
}>();

const permissionType = defineModel<PermissionType>("permissionType", {
  required: true,
});

function permissionLabel(p: PermissionType): string {
  return p === PermissionType.PublicReadable ? "Public Readable" : p;
}

// UIRULE-DROPDOWN-SEARCH-SORT: this is a 3-option visibility set
// (Private / Public Readable / Public) whose enum order is meaningful, so it is
// NOT natural-sorted. It goes through the searchable shared <Select> wrapper;
// mapping to {title,value} keeps the *visible* label == item-title so the shared
// autocomplete filters on the text the user actually sees (no space-mismatch on
// "Public Readable" vs "PublicReadable").
const permissionItems = computed(() =>
  (props.limitedPermissionSet ?? Object.values(PermissionType)).map(p => ({
    title: permissionLabel(p),
    value: p,
  })),
);
</script>

<template>
  <Select
    v-model:model-value="permissionType"
    :items="permissionItems"
    item-title="title"
    item-value="value"
    :label="`Permissions${noRequiredHint ? '' : '*'}`"
    variant="outlined"
    density="compact"
    color="primary"
    require
    hide-details
  />
</template>
