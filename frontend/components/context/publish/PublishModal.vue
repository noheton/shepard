<script setup lang="ts">
/**
 * KIP1e licence-picker + confirmation modal.
 *
 * The licence drop-down is the minimum-viable hard-coded SPDX list
 * per `aidocs/66 §7`. A tracked-SPDX-list integration is a later
 * slice (`aidocs/16` KIP1g once it lands).
 *
 * Note (KIP1a wire reality): the backend's
 * `POST /v2/{kind}/{appId}/publish` endpoint does not accept a body
 * — it reads the licence from the published entity's metadata.
 * Until that surface accepts a licence override (a future slice),
 * the drop-down here is **informational**: the operator confirms
 * the licence they intend to apply, the publish call fires without
 * a body, and the licence stays whatever the entity already carries
 * in `attributes.license`. We surface the dropdown today so the UI
 * contract is in place when the backend grows the body field — at
 * that point this component starts forwarding it via the parent's
 * `submit` payload.
 */

interface PublishModalProps {
  kindLabel: string; // "Collection" | "DataObject"
  entityName?: string | null;
  loading?: boolean;
}

const props = withDefaults(defineProps<PublishModalProps>(), {
  entityName: null,
  loading: false,
});

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const emit = defineEmits<{
  (event: "submit", payload: { licence: string }): void;
}>();

// Hard-coded SPDX list per the KIP1e brief; small enough to inline,
// tracked-SPDX integration is a follow-up slice.
const LICENCES = [
  { value: "CC-BY-4.0", title: "CC-BY-4.0 — Creative Commons Attribution 4.0" },
  {
    value: "CC-BY-SA-4.0",
    title: "CC-BY-SA-4.0 — CC Attribution-ShareAlike 4.0",
  },
  { value: "CC0-1.0", title: "CC0-1.0 — Public domain dedication" },
  { value: "MIT", title: "MIT" },
  { value: "Apache-2.0", title: "Apache License 2.0" },
  { value: "LGPL-3.0", title: "LGPL-3.0 — GNU Lesser GPL v3" },
  { value: "GPL-3.0", title: "GPL-3.0 — GNU GPL v3" },
];

const selectedLicence = ref<string>("CC-BY-4.0");

const cardTitleId = "kip1e-publish-modal-title";

function submit() {
  emit("submit", { licence: selectedLicence.value });
}

function close() {
  showDialog.value = false;
}
</script>

<template>
  <v-dialog
    v-model="showDialog"
    persistent
    max-width="600"
    :aria-labelledby="cardTitleId"
    @keydown.esc="close"
  >
    <v-card :loading="props.loading" color="canvas">
      <template #title>
        <div :id="cardTitleId" class="d-flex justify-space-between align-baseline">
          <div class="text-h4 text-wrap">
            Publish {{ props.kindLabel
            }}<span v-if="props.entityName"> — {{ props.entityName }}</span>
          </div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            aria-label="Close publish dialog"
            @click="close"
          />
        </div>
      </template>
      <template #text>
        <div class="d-flex flex-column ga-4">
          <p class="text-body-1">
            Publishing this {{ props.kindLabel.toLowerCase() }} mints a permanent
            identifier (PID). Per HMC convention, PIDs are
            <strong>append-only</strong> — published metadata can be updated,
            but the PID itself stays.
          </p>
          <v-select
            v-model="selectedLicence"
            :items="LICENCES"
            item-title="title"
            item-value="value"
            label="Licence"
            hint="The licence that will be associated with this publication. Today this confirms intent; the licence stored on the entity's metadata is what the KIP record cites."
            persistent-hint
            variant="outlined"
            density="compact"
            aria-label="Select a licence for this publication"
          />
          <p class="text-caption text-medium-emphasis">
            Need more detail? See the
            <a
              href="/help/publish-data-object/"
              target="_blank"
              rel="noopener"
              >Publish help page</a
            >.
          </p>
        </div>
      </template>
      <template #actions>
        <v-row justify="end">
          <v-spacer />
          <v-col cols="auto">
            <v-btn variant="flat" color="treeview" @click="close">
              Cancel
            </v-btn>
            <v-btn
              :disabled="props.loading"
              :loading="props.loading"
              color="primary"
              variant="flat"
              class="ml-4"
              autofocus
              @click="submit"
            >
              Publish
            </v-btn>
          </v-col>
        </v-row>
      </template>
    </v-card>
  </v-dialog>
</template>

<style scoped lang="scss"></style>
