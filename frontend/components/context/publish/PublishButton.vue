<script setup lang="ts">
/**
 * KIP1e Publish button for Collection / DataObject panes.
 *
 * Scope-down note (KIP1a wire reality): KIP1a's `BasicEntityIO`
 * shape is frozen per CLAUDE.md ("Frozen upstream classes are
 * untouchable") and KIP1a doesn't ship a side-channel endpoint
 * for "fetch the current publication state of an entity". So the
 * existing-publication-state display is **scoped down** for KIP1e:
 * the button always reads "Publish"; clicking always performs the
 * POST. Because the backend endpoint is idempotent — a re-POST
 * returns the existing Publication row without `?force=true` — the
 * snackbar surfaces the freshly-known PID either way. A future
 * slice can wire a `GET /v2/{kind}/{appId}/publications` helper
 * (or expose `HAS_PUBLICATION` on the v2 entity-detail shape) and
 * upgrade the button to show "Published — view PID" + the popover
 * with the resolver URL and re-mint affordance.
 *
 * See `aidocs/66 §7` (KIP1e) and the CLAUDE.md scope-down note in
 * this slice's commit message.
 */
import { useClipboard } from "@vueuse/core";
import PublishModal from "./PublishModal.vue";
import type {
  PublishableKind,
  PublicationResponse,
} from "~/composables/context/usePublishEntity";
import { usePublishEntity } from "~/composables/context/usePublishEntity";

interface PublishButtonProps {
  entityKind: PublishableKind;
  entityAppId: string | null | undefined;
  entityName?: string | null;
}

const props = withDefaults(defineProps<PublishButtonProps>(), {
  entityName: null,
});

const emit = defineEmits<{
  (event: "published", payload: PublicationResponse): void;
}>();

const { publish, isPublishing } = usePublishEntity();
const { copy: copyToClipboard } = useClipboard();

const showDialog = ref(false);
const showSnackbar = ref(false);
const lastPublication = ref<PublicationResponse | null>(null);

const kindLabel = computed(() =>
  props.entityKind === "collections" ? "Collection" : "DataObject",
);

const ariaLabel = computed(() =>
  props.entityKind === "collections"
    ? "Publish this collection"
    : "Publish this data object",
);

function openModal() {
  showDialog.value = true;
}

async function onSubmit() {
  if (!props.entityAppId) return;
  const response = await publish(props.entityKind, props.entityAppId);
  if (response) {
    showDialog.value = false;
    lastPublication.value = response;
    showSnackbar.value = true;
    emit("published", response);
  }
}

function copyResolverUrl() {
  if (!lastPublication.value) return;
  copyToClipboard(lastPublication.value.resolverUrl);
  emitSuccess("Resolver URL copied to clipboard");
}

function copyPid() {
  if (!lastPublication.value) return;
  copyToClipboard(lastPublication.value.pid);
  emitSuccess("PID copied to clipboard");
}
</script>

<template>
  <div>
    <v-tooltip
      text="Publish this item to get a persistent identifier (DOI/PID) for citation."
      location="bottom"
    >
      <template #activator="{ props: tooltipProps }">
        <v-btn
          v-bind="tooltipProps"
          :disabled="!props.entityAppId || isPublishing"
          :loading="isPublishing"
          :aria-label="ariaLabel"
          rounded="lg"
          class="mx-2"
          variant="flat"
          color="primary"
          prepend-icon="mdi-information-outline"
          @click="openModal"
        >
          Publish
        </v-btn>
      </template>
    </v-tooltip>

    <PublishModal
      v-if="showDialog"
      v-model:show-dialog="showDialog"
      :kind-label="kindLabel"
      :entity-name="props.entityName"
      :loading="isPublishing"
      @submit="onSubmit"
    />

    <v-snackbar
      v-model="showSnackbar"
      :timeout="10000"
      color="success"
      multi-line
      location="bottom right"
    >
      <div v-if="lastPublication">
        <div class="font-weight-bold">Published</div>
        <div class="text-caption mt-1">
          PID:
          <code class="ml-1">{{ lastPublication.pid }}</code>
        </div>
      </div>
      <template #actions>
        <v-btn
          variant="text"
          aria-label="Copy resolver URL to clipboard"
          @click="copyResolverUrl"
        >
          Copy resolver URL
        </v-btn>
        <v-btn
          variant="text"
          aria-label="Copy PID to clipboard"
          @click="copyPid"
        >
          Copy PID
        </v-btn>
        <v-btn
          variant="text"
          icon="mdi-close"
          aria-label="Dismiss publish notification"
          @click="showSnackbar = false"
        />
      </template>
    </v-snackbar>
  </div>
</template>

<style scoped lang="scss"></style>
