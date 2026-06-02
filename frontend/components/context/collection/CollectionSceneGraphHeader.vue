<script setup lang="ts">
/**
 * COLL-SCENE-2-UI — hero scene-graph band rendered at the top of the
 * Collection detail page when the Collection carries a
 * `sceneGraphAppId` link.
 *
 * Behaviour:
 *  - `sceneGraphAppId` non-null on the Collection prop:
 *    fetch `GET /v2/collections/{appId}/scene-graph` for the scene
 *    identity tuple, then `GET /v2/scene-graphs/{appId}/export.urdf`
 *    for renderable URDF XML. Pass the result as a blob:URL to
 *    `UrdfCanvas`. ~360px tall full-bleed band.
 *  - `sceneGraphAppId` null AND `canLink` true (writer on Collection):
 *    render a small "Link scene-graph" affordance that pops a v-select
 *    picker pulling from `GET /v2/scene-graphs`.
 *  - `sceneGraphAppId` null AND `canLink` false: render nothing.
 *  - Link resolves to 404/403 (scene was wiped or revoked): render
 *    `EntityNotFound` for the band instead of crashing.
 *
 * GAP-6 in `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md`. The
 * MFFD `MFZ.rdk` URDF flat-lays a real industrial robot cell, the LUMEN
 * showcase carries a bench scene — once linked, the Collection landing
 * page becomes a digital-twin entry point instead of a folder tree.
 */
import { onMounted, onBeforeUnmount, ref, watch } from "vue";
import EntityNotFound from "~/components/common/EntityNotFound.vue";
import UrdfCanvas from "~/components/shapes/UrdfCanvas.vue";
import {
  fetchCollectionSceneGraphLink,
  fetchSceneUrdfBlobUrl,
  linkCollectionSceneGraph,
  unlinkCollectionSceneGraph,
  type CollectionSceneGraphLinkIO,
} from "~/composables/context/useCollectionSceneGraphLink";

const props = defineProps<{
  /** Collection's UUID v7 appId. */
  collectionAppId: string;
  /** Current link from the parent fetch — `null` when unset. */
  sceneGraphAppId: string | null;
  /** Whether the caller can mutate the link (writer on Collection). */
  canLink: boolean;
}>();

const emit = defineEmits<{
  /** Emitted after a successful link/unlink so the parent refetches Collection. */
  changed: [];
}>();

const linkInfo = ref<CollectionSceneGraphLinkIO | null>(null);
const urdfBlobUrl = ref<string | null>(null);
const loading = ref(false);
const notFound = ref(false);

// ── Link picker dialog state ────────────────────────────────────────────────

const showPicker = ref(false);
const pickerSceneId = ref<string>("");
const pickerOptions = ref<Array<{ value: string; title: string }>>([]);
const pickerLoading = ref(false);
const pickerSaving = ref(false);

interface SceneListItemLike {
  appId: string;
  name?: string | null;
}
interface SceneListPageLike {
  items?: SceneListItemLike[];
}

async function loadPickerOptions(): Promise<void> {
  pickerLoading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const config = useRuntimeConfig().public;
    const explicit = config.backendV2ApiUrl as string | undefined;
    const base = (
      explicit && explicit.length > 0
        ? explicit
        : (config.backendApiUrl as string)
            .replace(/\/shepard\/api\/?$/, "")
            .replace(/\/$/, "")
    ).replace(/\/$/, "");
    const resp = await fetch(`${base}/v2/scene-graphs?size=200`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!resp.ok) {
      pickerOptions.value = [];
      return;
    }
    const page = (await resp.json()) as SceneListPageLike;
    const items = page.items ?? [];
    pickerOptions.value = items.map(it => ({
      value: it.appId,
      title: it.name ? `${it.name} (${it.appId.slice(0, 8)}…)` : it.appId,
    }));
  } catch {
    pickerOptions.value = [];
  } finally {
    pickerLoading.value = false;
  }
}

async function openPicker(): Promise<void> {
  pickerSceneId.value = props.sceneGraphAppId ?? "";
  showPicker.value = true;
  await loadPickerOptions();
}

async function savePicker(): Promise<void> {
  if (!pickerSceneId.value) return;
  pickerSaving.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) return;
    const result = await linkCollectionSceneGraph(
      props.collectionAppId,
      pickerSceneId.value,
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

async function loadScene(): Promise<void> {
  // Revoke a prior blob URL before fetching the next one.
  if (urdfBlobUrl.value) {
    URL.revokeObjectURL(urdfBlobUrl.value);
    urdfBlobUrl.value = null;
  }
  linkInfo.value = null;
  notFound.value = false;
  if (!props.sceneGraphAppId) return;

  loading.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      notFound.value = true;
      return;
    }

    const info = await fetchCollectionSceneGraphLink(
      props.collectionAppId,
      accessToken,
    );
    if (!info) {
      // 404 / 403 — render EntityNotFound for the band.
      notFound.value = true;
      return;
    }
    linkInfo.value = info;

    const blobUrl = await fetchSceneUrdfBlobUrl(
      info.sceneGraphAppId,
      accessToken,
    );
    if (!blobUrl) {
      notFound.value = true;
      return;
    }
    urdfBlobUrl.value = blobUrl;
  } finally {
    loading.value = false;
  }
}

onMounted(loadScene);
watch(
  () => props.sceneGraphAppId,
  () => loadScene(),
);

onBeforeUnmount(() => {
  if (urdfBlobUrl.value) {
    URL.revokeObjectURL(urdfBlobUrl.value);
    urdfBlobUrl.value = null;
  }
});
</script>

<template>
  <div class="collection-scenegraph-header">
    <!-- Branch 1: scene linked + resolves cleanly → render the URDF viewer. -->
    <template v-if="sceneGraphAppId && !notFound">
      <div v-if="loading" class="scene-loading">
        <v-progress-circular indeterminate size="32" />
        <span class="ml-3 text-caption">Loading scene…</span>
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
          <strong v-if="linkInfo?.name">{{ linkInfo.name }}</strong>
          <span v-else class="text-medium-emphasis">{{
            sceneGraphAppId.slice(0, 8)
          }}…</span>
          <span class="text-caption text-medium-emphasis">
            {{ linkInfo?.frameCount ?? 0 }} frames ·
            {{ linkInfo?.jointCount ?? 0 }} joints
          </span>
          <v-spacer />
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
      <EntityNotFound
        entity-kind="Collection"
        :requested-id="sceneGraphAppId"
      />
    </template>

    <!-- Branch 3: no link + writer → CTA. -->
    <template v-else-if="canLink">
      <div class="scene-link-cta" data-testid="scene-link-empty">
        <span class="text-medium-emphasis text-caption">
          No scene-graph linked yet.
        </span>
        <v-btn
          variant="tonal"
          size="small"
          prepend-icon="mdi-link-variant"
          data-testid="scene-link-btn"
          @click="openPicker"
          >Link scene-graph</v-btn
        >
      </div>
    </template>

    <!-- Branch 4: no link + no permission → render nothing. -->

    <!-- Picker dialog (shared by link + replace). -->
    <v-dialog v-model="showPicker" max-width="640">
      <v-card>
        <v-card-title>Link a scene-graph</v-card-title>
        <v-card-text>
          <v-select
            v-model="pickerSceneId"
            :items="pickerOptions"
            :loading="pickerLoading"
            item-title="title"
            item-value="value"
            label="Scene-graph"
            data-testid="scene-picker-select"
          />
          <p class="text-caption text-medium-emphasis">
            Pick from scenes you can read. The link only surfaces a render
            affordance on this Collection; the scene's own permissions stay
            the source-of-truth for who can edit it.
          </p>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showPicker = false">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            :loading="pickerSaving"
            :disabled="!pickerSceneId"
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
