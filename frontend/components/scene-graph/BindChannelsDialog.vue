<script setup lang="ts">
/**
 * SCENEGRAPH-CANVAS-ANIM-1 slice 2 — "Bind channels to joints" dialog.
 * Opens from the scene-graph play page; lets the user choose a
 * TimeseriesReference and map each movable joint to a channel by its
 * shepardId (UUID v7). Saves via PATCH /v2/templates/{templateAppId}
 * (body-replace of the MAPPING_RECIPE body; design: aidocs/platform/191).
 *
 * After a successful save the parent page reloads to pick up the new bindings.
 * No numeric Neo4j IDs anywhere — all identity by appId.
 *
 * Backlog: SCENEGRAPH-CANVAS-ANIM-1 slice 2.
 */
import { useChannelsFromTimeseriesRef } from "~/composables/useChannelsFromTimeseriesRef";
import { usePatchSceneGraphBindings } from "~/composables/useSceneGraphPlay";
import { naturalSort } from "~/utils/naturalSort";

interface JointDef {
  name: string;
  type: string;
}

const props = defineProps<{
  modelValue: boolean;
  templateAppId: string;
  urdfFileReferenceAppId: string;
  /** All joints from the play envelope (movable + fixed). Fixed are shown read-only. */
  joints: JointDef[];
  currentTsRefAppId?: string | null;
  currentBindings?: { joint: string; channelSelector: string }[] | null;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "applied"): void;
}>();

const tsRefAppId = ref("");
const channelMap = ref<Record<string, string | null>>({});
const submitError = ref<string | null>(null);

const {
  channels,
  loading: loadingChannels,
  error: channelsError,
  load: fetchChannels,
  clear: clearChannels,
} = useChannelsFromTimeseriesRef();

const { loading: patching, patch } = usePatchSceneGraphBindings();

const movableJoints = computed(() =>
  props.joints.filter(j => j.type !== "fixed"),
);

// UIRULE-DROPDOWN-SEARCH-SORT: naturally ordered (numeric-aware) channel list.
const channelItems = computed(() =>
  naturalSort(
    channels.value.map(ch => ({
      title: [ch.device, ch.symbolicName, ch.field].filter(Boolean).join(" · ") || ch.shepardId,
      value: ch.shepardId,
    })),
    i => i.title,
  ),
);

function resetState() {
  tsRefAppId.value = props.currentTsRefAppId ?? "";
  const map: Record<string, string | null> = {};
  for (const j of props.joints) map[j.name] = null;
  for (const b of (props.currentBindings ?? [])) map[b.joint] = b.channelSelector;
  channelMap.value = map;
  submitError.value = null;
  clearChannels();
  if (tsRefAppId.value) fetchChannels(tsRefAppId.value);
}

watch(() => props.modelValue, open => { if (open) resetState(); });

async function onLoadChannels() {
  submitError.value = null;
  await fetchChannels(tsRefAppId.value.trim());
}

const canSave = computed(() => !!tsRefAppId.value.trim() && !patching.value);

async function onSave() {
  submitError.value = null;
  const bindings = movableJoints.value
    .filter(j => !!(channelMap.value[j.name] ?? "").trim())
    .map(j => ({
      joint: j.name,
      channelSelector: (channelMap.value[j.name] as string).trim(),
    }));
  const result = await patch({
    templateAppId: props.templateAppId,
    urdfFileReferenceAppId: props.urdfFileReferenceAppId,
    jointTimeseriesReferenceAppId: tsRefAppId.value.trim() || null,
    jointChannelBindings: bindings,
  });
  if (!result.ok) {
    submitError.value =
      result.status === 403
        ? "You don't have permission to edit this 3D view."
        : `Could not save channel bindings (HTTP ${result.status}): ${result.detail}`;
    return;
  }
  emit("update:modelValue", false);
  emit("applied");
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="660"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon size="small" color="primary">mdi-link-variant</v-icon>
        Bind channels to joints
      </v-card-title>
      <v-card-text>
        <div class="text-caption text-medium-emphasis mb-3">
          Choose a TimeseriesReference and map each movable joint to one of its
          channels. The play page will reload with live animation once saved.
        </div>

        <!-- TimeseriesReference appId row -->
        <div class="d-flex ga-2 align-center mb-4">
          <v-text-field
            v-model="tsRefAppId"
            label="TimeseriesReference appId"
            density="comfortable"
            variant="outlined"
            hide-details
            class="flex-grow-1"
            data-test="ts-ref-appid-input"
            :disabled="patching"
            @keyup.enter="onLoadChannels"
          />
          <v-btn
            variant="tonal"
            :loading="loadingChannels"
            :disabled="!tsRefAppId.trim() || patching"
            data-test="load-channels-btn"
            @click="onLoadChannels"
          >
            Load
          </v-btn>
        </div>

        <v-alert
          v-if="channelsError"
          type="warning"
          variant="tonal"
          density="compact"
          class="mb-3"
          data-test="channels-error"
        >
          {{ channelsError }}
        </v-alert>

        <!-- Per-joint channel selectors -->
        <template v-if="movableJoints.length > 0">
          <div class="text-caption text-medium-emphasis mb-2">
            {{ movableJoints.length }} movable joint(s)
            <template v-if="channels.length > 0">
              · {{ channels.length }} channel(s) available
            </template>
          </div>
          <div class="d-flex flex-column ga-2">
            <div
              v-for="joint in movableJoints"
              :key="joint.name"
              class="d-flex align-center ga-2"
            >
              <code class="text-caption" style="min-width: 130px; flex-shrink: 0">
                {{ joint.name }}
              </code>
              <v-chip size="x-small" variant="tonal" class="flex-shrink-0">
                {{ joint.type }}
              </v-chip>
              <v-autocomplete
                v-model="channelMap[joint.name]"
                :items="channelItems"
                :label="`Channel for ${joint.name}`"
                item-title="title"
                item-value="value"
                density="compact"
                variant="outlined"
                hide-details
                clearable
                class="flex-grow-1"
                :no-data-text="channels.length === 0 ? 'Load channels first' : 'No match'"
                :disabled="patching"
                :data-test="`channel-select-${joint.name}`"
              />
            </div>
          </div>
        </template>

        <v-alert v-else type="info" variant="tonal" density="compact">
          No movable joints found in the URDF.
        </v-alert>

        <v-alert
          v-if="submitError"
          type="error"
          variant="tonal"
          density="compact"
          class="mt-3"
          data-test="save-error"
        >
          {{ submitError }}
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          :disabled="patching"
          data-test="bind-channels-cancel"
          @click="$emit('update:modelValue', false)"
        >
          Cancel
        </v-btn>
        <v-btn
          color="primary"
          variant="flat"
          :loading="patching"
          :disabled="!canSave"
          data-test="bind-channels-save"
          @click="onSave"
        >
          Save &amp; reload
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
