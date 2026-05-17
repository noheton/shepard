<script setup lang="ts">
import {
  ShepardTemplateApi,
  type ShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useFetchTemplates } from "~/composables/context/admin/useFetchTemplates";
import { AdminFragments } from "./adminMenuItems";

const { templates, isLoading, refresh } = useFetchTemplates();

// Show retired templates toggle
const showRetired = ref(false);

// Filter state
const filterKind = ref<string | null>(null);
const filterTag = ref<string | null>(null);

// Dialog state
const dialogOpen = ref(false);
const editingTemplate = ref<ShepardTemplateIO | null>(null);

// Retire confirmation
const retireTarget = ref<ShepardTemplateIO | null>(null);
const retireConfirmOpen = ref(false);
const isRetiring = ref(false);
const retireError = ref<string | null>(null);

const KIND_OPTIONS = [
  { title: "All", value: null },
  { title: "DataObject", value: "DATAOBJECT_RECIPE" },
  { title: "Collection", value: "COLLECTION_RECIPE" },
  { title: "Experiment", value: "EXPERIMENT_RECIPE" },
];

const TABLE_HEADERS = [
  { title: "Name", key: "name", sortable: true },
  { title: "Kind", key: "templateKind", sortable: true },
  { title: "Version", key: "version", sortable: true, width: "80px" },
  { title: "Tags", key: "tags", sortable: false },
  { title: "Created", key: "createdAt", sortable: true },
  { title: "Status", key: "retired", sortable: true, width: "100px" },
  { title: "Actions", key: "actions", sortable: false, width: "100px" },
];

const allTags = computed<string[]>(() => {
  const tagSet = new Set<string>();
  for (const t of templates.value) {
    if (t.tags) t.tags.forEach((tag) => tagSet.add(tag));
  }
  return Array.from(tagSet).sort();
});

const filteredTemplates = computed(() => {
  return templates.value.filter((t) => {
    if (!showRetired.value && t.retired) return false;
    if (filterKind.value && t.templateKind !== filterKind.value) return false;
    if (filterTag.value && !(t.tags ?? []).includes(filterTag.value)) return false;
    return true;
  });
});

function kindLabel(kind: string): string {
  switch (kind) {
    case "DATAOBJECT_RECIPE": return "DataObject";
    case "COLLECTION_RECIPE": return "Collection";
    case "EXPERIMENT_RECIPE": return "Experiment";
    default: return kind;
  }
}

function kindColor(kind: string): string {
  switch (kind) {
    case "DATAOBJECT_RECIPE": return "primary";
    case "COLLECTION_RECIPE": return "secondary";
    case "EXPERIMENT_RECIPE": return "success";
    default: return "default";
  }
}

function formatDate(millis: number | null | undefined): string {
  if (!millis) return "—";
  return new Date(millis).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function openCreate() {
  editingTemplate.value = null;
  dialogOpen.value = true;
}

function openEdit(template: ShepardTemplateIO) {
  editingTemplate.value = template;
  dialogOpen.value = true;
}

function openRetireConfirm(template: ShepardTemplateIO) {
  retireTarget.value = template;
  retireError.value = null;
  retireConfirmOpen.value = true;
}

async function confirmRetire() {
  if (!retireTarget.value) return;
  retireError.value = null;
  isRetiring.value = true;
  try {
    await useV2ShepardApi(ShepardTemplateApi).value.retireTemplate({
      appId: retireTarget.value.appId,
    });
    retireConfirmOpen.value = false;
    retireTarget.value = null;
    await refresh(showRetired.value);
  } catch (error: unknown) {
    const msg = (error as { message?: string })?.message ?? "Unknown error";
    retireError.value = `Failed to retire template: ${msg}`;
    handleError(error, "retiring template");
  } finally {
    isRetiring.value = false;
  }
}

async function onSaved() {
  await refresh(showRetired.value);
}

watch(showRetired, (val) => {
  refresh(val);
});
</script>

<template>
  <div :id="AdminFragments.TEMPLATES" class="d-flex flex-column ga-4">
    <div class="d-flex align-center justify-space-between flex-wrap ga-2">
      <h4 class="text-h4">Templates</h4>
      <v-btn
        color="primary"
        variant="tonal"
        prepend-icon="mdi-plus"
        @click="openCreate"
      >
        New template
      </v-btn>
    </div>

    <!-- Filters -->
    <div class="d-flex align-center flex-wrap ga-3">
      <v-chip-group
        v-model="filterKind"
        selected-class="text-primary"
        class="flex-shrink-0"
      >
        <v-chip
          v-for="opt in KIND_OPTIONS"
          :key="opt.value ?? 'all'"
          :value="opt.value"
          filter
          variant="outlined"
          size="small"
        >
          {{ opt.title }}
        </v-chip>
      </v-chip-group>

      <v-combobox
        v-model="filterTag"
        :items="allTags"
        label="Filter by tag"
        clearable
        variant="outlined"
        density="compact"
        hide-details
        style="max-width: 220px"
      />

      <v-switch
        v-model="showRetired"
        label="Show retired"
        hide-details
        density="compact"
        color="secondary"
        class="ml-auto flex-shrink-0"
      />
    </div>

    <centered-loading-spinner v-if="isLoading && templates.length === 0" />

    <EmptyListIcon
      v-else-if="!isLoading && filteredTemplates.length === 0"
      label="No templates found"
    />

    <v-data-table
      v-else
      :headers="TABLE_HEADERS"
      :items="filteredTemplates"
      :loading="isLoading"
      item-value="appId"
      density="compact"
      hover
    >
      <!-- Kind column -->
      <template #item.templateKind="{ item }">
        <v-chip :color="kindColor(item.templateKind)" variant="tonal" size="small">
          {{ kindLabel(item.templateKind) }}
        </v-chip>
      </template>

      <!-- Tags column -->
      <template #item.tags="{ item }">
        <div class="d-flex flex-wrap ga-1">
          <v-chip
            v-for="tag in (item.tags ?? [])"
            :key="tag"
            size="x-small"
            variant="outlined"
          >
            {{ tag }}
          </v-chip>
          <span v-if="!item.tags || item.tags.length === 0" class="text-medium-emphasis text-body-2">—</span>
        </div>
      </template>

      <!-- Created date column -->
      <template #item.createdAt="{ item }">
        <span class="text-body-2">{{ formatDate(item.createdAt) }}</span>
      </template>

      <!-- Status column -->
      <template #item.retired="{ item }">
        <v-chip
          :color="item.retired ? 'error' : 'success'"
          variant="tonal"
          size="small"
        >
          {{ item.retired ? "Retired" : "Active" }}
        </v-chip>
      </template>

      <!-- Actions column -->
      <template #item.actions="{ item }">
        <div class="d-flex ga-1">
          <v-btn
            :disabled="item.retired"
            icon="mdi-pencil-outline"
            size="small"
            variant="text"
            :title="item.retired ? 'Cannot edit a retired template' : 'Edit template'"
            @click="openEdit(item)"
          />
          <v-btn
            :disabled="item.retired"
            icon="mdi-archive-arrow-down-outline"
            size="small"
            variant="text"
            color="warning"
            :title="item.retired ? 'Already retired' : 'Retire template'"
            @click="openRetireConfirm(item)"
          />
        </div>
      </template>
    </v-data-table>

    <!-- Create / Edit dialog -->
    <AdminTemplateDialog
      v-model="dialogOpen"
      :template="editingTemplate"
      @saved="onSaved"
    />

    <!-- Retire confirmation dialog -->
    <v-dialog v-model="retireConfirmOpen" max-width="480">
      <v-card>
        <v-card-title class="text-h6 pa-4">Retire template?</v-card-title>
        <v-card-text class="pa-4">
          <v-alert v-if="retireError" type="error" class="mb-3">{{ retireError }}</v-alert>
          <p>
            <strong>{{ retireTarget?.name }}</strong> (v{{ retireTarget?.version }}) will be
            marked as retired. Existing usages remain intact — this only hides it from new
            template pickers.
          </p>
        </v-card-text>
        <v-card-actions class="pa-4">
          <v-spacer />
          <v-btn variant="text" :disabled="isRetiring" @click="retireConfirmOpen = false">Cancel</v-btn>
          <v-btn color="warning" variant="tonal" :loading="isRetiring" @click="confirmRetire">
            Retire
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss"></style>
