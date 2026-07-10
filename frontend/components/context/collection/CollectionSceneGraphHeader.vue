<script setup lang="ts">
/**
 * V2CONV-B4-FE — hero-view band at the top of the Collection detail page when
 * the Collection carries a `sceneGraphAppId` link.
 *
 * The bespoke scene-graph subsystem dissolved into the generic MAPPING_RECIPE
 * mechanism (aidocs/platform/191 decision #2). The Collection hero link now
 * points at a MAPPING_RECIPE `ShepardTemplate` appId. This band:
 *  - resolves the linked template via `GET /v2/collections/{appId}/scene-graph`;
 *  - materializes it (`POST /v2/mappings/{templateAppId}/materialize`) to get
 *    the play envelope + the bound URDF FileReference appId;
 *  - resolves the URDF bytes (appId only — never a path/URL) and renders
 *    `UrdfCanvas`.
 *  - writer link picker pulls from `GET /v2/templates?kind=MAPPING_RECIPE`.
 */
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import EntityNotFound from "~/components/common/EntityNotFound.vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import { materializeMapping } from "~/composables/useMaterializeMapping";
import { useUrdfReferenceBlob } from "~/composables/useUrdfReferenceBlob";
import { naturalSort } from "~/utils/naturalSort";
import {
  fetchCollectionSceneGraphLink,
  linkCollectionSceneGraph,
  unlinkCollectionSceneGraph,
  listHeroViewTemplates,
  type CollectionHeroViewLinkIO,
} from "~/composables/context/useCollectionSceneGraphLink";

const props = defineProps<{
  /** Collection's UUID v7 appId. */
  collectionAppId: string;
  /** Current link from the parent fetch — `null` when unset. */
  sceneGraphAppId: string | null;
  /** Whether the caller can mutate the link (writer on Collection). */
  canLink: boolean;
}>();

const emit = defineEmits<{ changed: [] }>();

const linkInfo = ref<CollectionHeroViewLinkIO | null>(null);
const loading = ref(false);
const notFound = ref(false);
const frameCount = ref(0);
const jointCount = ref(0);

const {
  objectUrl: urdfBlobUrl,
  error: blobError,
  resolve: resolveBlob,
  revoke: revokeBlob,
} = useUrdfReferenceBlob();

// ── Link picker dialog state ────────────────────────────────────────────────

const showPicker = ref(false);
const pickerTemplateId = ref<string>("");
const pickerOptions = ref<Array<{ value: string; title: string }>>([]);
const pickerLoading = ref(false);
const pickerSaving = ref(false);

async function loadPickerOptions(): Promise<void> {
  pickerLoading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const items = await listHeroViewTemplates(accessToken);
    pickerOptions.value = naturalSort(
      items.map((it) => ({
        value: it.appId,
        title: it.name ? `${it.name} (${it.appId.slice(0, 8)}…)` : it.appId,
      })),
      (o) => o.title,
    );
  } catch {
    pickerOptions.value = [];
  } finally {
    pickerLoading.value = false;
  }
}

async function openPicker(): Promise<void> {
  pickerTemplateId.value = props.sceneGraphAppId ?? "";
  showPicker.value = true;
  await loadPickerOptions();
}

async function savePicker(): Promise<void> {
  if (!pickerTemplateId.value) return;
  pickerSaving.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const result = await linkCollectionSceneGraph(
      props.collectionAppId,
      pickerTemplateId.value,
      accessToken,
    );
    if (result) {
      showPicker.value = false;
      emit("changed");
    }
  } finally {
    pickerSaving.value = false;
  }
}

async function doUnlink(): Promise<void> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) return;
  const ok = await unlinkCollectionSceneGraph(props.collectionAppId, accessToken);
  if (ok) emit("changed");
}

// ── Loader ─────────────────────────────────────────────────────────────────

interface PlayEnvelope {
  urdfFileReferenceAppId?: string;
  frames?: unknown[];
  joints?: unknown[];
}

async function loadHeroView(): Promise<void> {
  revokeBlob();
  linkInfo.value = null;
  notFound.value = false;
  frameCount.value = 0;
  jointCount.value = 0;
  if (!props.sceneGraphAppId) return;

  loading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      notFound.value = true;
      return;
    }

    const info = await fetchCollectionSceneGraphLink(props.collectionAppId, accessToken);
    if (!info) {
      notFound.value = true;
      return;
    }
    linkInfo.value = info;

    const result = await materializeMapping(info.sceneGraphAppId, {});
    if (result.outputKind !== "VIEW" || !result.viewModel) {
      notFound.value = true;
      return;
    }
    const env = result.viewModel as unknown as PlayEnvelope;
    frameCount.value = env.frames?.length ?? 0;
    jointCount.value = env.joints?.length ?? 0;
    if (!env.urdfFileReferenceAppId) {
      notFound.value = true;
      return;
    }
    await resolveBlob(env.urdfFileReferenceAppId);
    if (blobError.value) notFound.value = true;
  } catch {
    notFound.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(loadHeroView);
watch(() => props.sceneGraphAppId, () => loadHeroView());
onBeforeUnmount(revokeBlob);
</script>

<template>
  <div class="collection-scenegraph-header">
    <!-- Branch 1: hero view linked + resolves cleanly → render the URDF viewer. -->
    <template v-if="sceneGraphAppId && !notFound">
      <div v-if="loading" class="scene-loading">
        <v-progress-circular indeterminate size="32" />
        <span class="ml-3 text-caption">Loading 3D view…</span>
      </div>
      <div v-else-if="urdfBlobUrl" class="scene-viewer-wrap">
        <ClientOnly>
          <UrdfCanvas
            :urdf-url="urdfBlobUrl"
            :show-axes="true"
            background-color="#101418"
            class="scene-viewer-canvas"
          />
        </ClientOnly>
        <div class="scene-caption">
          <strong v-if="linkInfo?.templateName">{{ linkInfo.templateName }}</strong>
          <span v-else class="text-medium-emphasis">{{ sceneGraphAppId.slice(0, 8) }}…</span>
          <span class="text-caption text-medium-emphasis">
            {{ frameCount }} frames · {{ jointCount }} joints
          </span>
          <v-spacer />
          <v-btn
            variant="text"
            size="small"
            data-testid="scene-open-btn"
            :to="`/scene-graphs/play/${encodeURIComponent(sceneGraphAppId)}`"
            >Open</v-btn
          >
          <v-btn
            v-if="canLink"
            variant="text"
            size="small"
            data-testid="scene-unlink-btn"
            @click="doUnlink"
            >Unlink</v-btn
          >
          <v-btn
            v-if="canLink"
            variant="text"
            size="small"
            data-testid="scene-replace-btn"
            @click="openPicker"
            >Replace</v-btn
          >
        </div>
      </div>
    </template>

    <!-- Branch 2: linked but dangling → EntityNotFound. -->
    <template v-else-if="sceneGraphAppId && notFound">
      <EntityNotFound entity-kind="Collection" :requested-id="sceneGraphAppId" />
    </template>

    <!-- Branch 3: no link + writer → CTA. -->
    <template v-else-if="canLink">
      <div class="scene-link-cta" data-testid="scene-link-empty">
        <span class="text-medium-emphasis text-caption"> No 3D view linked yet. </span>
        <v-btn
          variant="tonal"
          size="small"
          prepend-icon="mdi-link-variant"
          data-testid="scene-link-btn"
          @click="openPicker"
          >Link 3D view</v-btn
        >
      </div>
    </template>

    <!-- Branch 4: no link + no permission → render nothing. -->

    <!-- Picker dialog (shared by link + replace). -->
    <v-dialog v-model="showPicker" max-width="640">
      <v-card>
        <v-card-title>Link a 3D view</v-card-title>
        <v-card-text>
          <!-- UIRULE-DROPDOWN-SEARCH-SORT: template list — searchable + natural order. -->
          <v-autocomplete
            v-model="pickerTemplateId"
            :items="pickerOptions"
            :loading="pickerLoading"
            auto-select-first
            item-title="title"
            item-value="value"
            label="MAPPING_RECIPE template (3D view)"
            data-testid="scene-picker-select"
          />
          <p class="text-caption text-medium-emphasis">
            Pick a scene-graph-play MAPPING_RECIPE template. Create one from a URDF
            FileReference's "Create 3D view from this URDF" button.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showPicker = false">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="pickerSaving"
            :disabled="!pickerTemplateId"
            data-testid="scene-picker-save"
            @click="savePicker"
            >Link</v-btn
          >
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.collection-scenegraph-header {
  width: 100%;
}
.scene-viewer-wrap {
  position: relative;
  width: 100%;
  height: 360px;
  background: #0d0d0d;
}
.scene-viewer-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.scene-caption {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 16px;
  background: rgba(0, 0, 0, 0.55);
  color: #eee;
}
.scene-loading {
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0d0d0d;
  color: #eee;
}
.scene-link-cta {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.04);
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}
</style>
