<script setup lang="ts">
import {
  instanceOfUser,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import {
  SearchType,
  useMemberSearch,
  type Member,
} from "../../../composables/common/permissions/useMemberSearch";
import type { AutoCompleteItem } from "../AutocompleteInput.vue";
const emit = defineEmits<{
  (e: "memberSelect", value: Member): void;
}>();

const model = defineModel<Member>();
defineSlots();

const { searchType } = withDefaults(
  defineProps<{
    label: string;
    searchType?: SearchType;
  }>(),
  { searchType: SearchType.MEMBER },
);
const searchString = ref<string | undefined>(undefined);
const searchDone = ref<boolean>(false);

const { searchResults, isLoading, startSearch } = useMemberSearch(
  searchString,
  () => {
    searchDone.value = true;
  },
  searchType,
);

const mapToAutocompleteItem = (member: Member): AutoCompleteItem => {
  if (instanceOfUser(member))
    return {
      title: `${member.firstName} ${member.lastName}`,
      value: member as User,
    };
  else
    return {
      title: `${member.name} (User Group, ID: ${member.id})`,
      value: member as UserGroup,
    };
};

const onSelect = (selectedItem: AutoCompleteItem | null) => {
  if (selectedItem?.value) {
    emit("memberSelect", selectedItem.value as Member);
  }
};
</script>
<template>
  <AutocompleteInput
    v-model:search-string="searchString"
    v-model:search-done="searchDone"
    :model-value="model ? mapToAutocompleteItem(model) : undefined"
    :label="label"
    density="compact"
    :is-loading="isLoading"
    :item-list="searchResults.map(mapToAutocompleteItem)"
    :start-search="startSearch"
    @search-ended="onSelect"
  >
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
  </AutocompleteInput>
</template>
