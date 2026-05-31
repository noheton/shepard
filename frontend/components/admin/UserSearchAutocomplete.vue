<script lang="ts" setup>
/**
 * UserSearchAutocomplete — admin-context user picker.
 *
 * Thin wrapper over `AutocompleteInput` + `usePermissionUserSearch`.
 * Emits the chosen `User` (or `null` when cleared). Used by the three
 * admin panes that key actions on `username`:
 *   - AdminInstanceAdminsPane (ADM-MANAGE)
 *   - AdminUserOrcidPane      (ADM-USR-ORCID)
 *   - AdminUserGitPane        (ADM-USR-GIT)
 *
 * The label is configurable so each pane can frame the search clearly.
 */
import type { User } from "@dlr-shepard/backend-client";
import { usePermissionUserSearch } from "~/composables/common/permissions/usePermissionUserSearch";
import AutocompleteInput, {
  type AutoCompleteItem,
} from "~/components/common/AutocompleteInput.vue";

withDefaults(
  defineProps<{
    label?: string;
    density?: "default" | "comfortable" | "compact";
  }>(),
  {
    label: "Search users",
    density: "comfortable",
  },
);

const emit = defineEmits<{
  (e: "userSelected", value: User | null): void;
}>();

const searchString = ref<string | undefined>(undefined);
const searchDone = ref<boolean>(false);

const { ownerSearchResults, isLoading, startSearch } = usePermissionUserSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
);

const selectedItem = ref<AutoCompleteItem | undefined>(undefined);

function userLabel(u: User): string {
  const name = `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim();
  return name.length > 0 ? `${name} (${u.username})` : u.username;
}

const mapToAutocompleteItem = (user: User): AutoCompleteItem => ({
  title: userLabel(user),
  value: user,
});

function onSelect(item: AutoCompleteItem | null) {
  if (item?.value) {
    selectedItem.value = item;
    emit("userSelected", item.value as User);
  } else {
    selectedItem.value = undefined;
    emit("userSelected", null);
  }
}
</script>

<template>
  <AutocompleteInput
    v-model="selectedItem"
    v-model:search-done="searchDone"
    v-model:search-string="searchString"
    :is-loading="isLoading"
    :item-list="ownerSearchResults.map(mapToAutocompleteItem)"
    :start-search="startSearch"
    :density="density"
    :label="label"
    data-testid="user-search-autocomplete"
    @search-ended="onSelect"
  />
</template>
