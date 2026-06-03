<script setup lang="ts">
/**
 * MFFD-IMAGEBUNDLE-PANE-MOUNT-1 — image bundle frame-scrubber pane.
 *
 * Mounts on the DataObject detail page when the DO carries at least one
 * FileBundleReference whose first group contains image files
 * (.png, .jpg, .jpeg, .tif, .tiff).
 *
 * Detection: iterates over `candidateBundleAppIds` (FileBundleReference
 * appIds already gathered by the parent from the v1 dataReferences list),
 * calls `GET /v2/bundles/{appId}` for each, and picks the first bundle
 * whose first group's first file has an image-extension filename.
 *
 * Rendering: once resolved, delegates to `ImageBundleViewer` which owns
 * the pagination + scrubber + thumbnail strip affordances.
 *
 * Follows `DataObjectThermographyPane.vue` for component structure; differs
 * in that detection is self-contained so index.vue only needs to pass the
 * list of candidate bundle appIds rather than doing the v2 API lookup itself.
 */

import ImageBundleViewer from "~/components/common/ImageBundleViewer.vue";
import { isImageFilename } from "~/composables/container/useFetchFileThumbnail";

const props = defineProps<{
  /** appId of the parent DataObject (informational; not used for fetching). */
  dataObjectAppId: string;
  /**
   * appIds of all FileBundleReferences attached to the DataObject, as detected
   * from the v1 dataReferences array on the parent page. The pane checks each
   * via `GET /v2/bundles/{appId}` and picks the first whose first group carries
   * at least one image-extension file.
   */
  candidateBundleAppIds: string[];
}>();

interface ShepardFile {
  oid?: string | null;
  filename?: string | null;
}

interface FileGroupIO {
  appId?: string | null;
  name?: string | null;
  files?: ShepardFile[];
}

interface FileBundleIO {
  appId?: string | null;
  containerMongoId?: string | null;
  groups?: FileGroupIO[];
}

interface ResolvedBundle {
  bundleAppId: string;
  groupAppId: string;
  containerMongoId: string | null;
  groupName: string | null;
}

const isLoading = ref(false);
const resolvedBundle = ref<ResolvedBundle | null>(null);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchBundle(bundleAppId: string): Promise<FileBundleIO | null> {
  const { data: session } = useAuth();
  const token = (session.value as { accessToken?: string } | null)?.accessToken;
  const url = `${v2BaseUrl()}/v2/bundles/${encodeURIComponent(bundleAppId)}`;
  try {
    const r = await fetch(url, {
      headers: {
        Authorization: token ? `Bearer ${token}` : "",
        Accept: "application/json",
      },
    });
    if (!r.ok) return null;
    return (await r.json()) as FileBundleIO;
  } catch {
    return null;
  }
}

function firstGroupHasImageFile(bundle: FileBundleIO): boolean {
  const firstGroup = bundle.groups?.[0];
  if (!firstGroup?.files?.length) return false;
  return firstGroup.files.some(f => isImageFilename(f.filename ?? null));
}

async function detectImageBundle(): Promise<void> {
  resolvedBundle.value = null;
  if (!props.candidateBundleAppIds.length) return;
  isLoading.value = true;
  try {
    for (const bundleAppId of props.candidateBundleAppIds) {
      const bundle = await fetchBundle(bundleAppId);
      if (!bundle || !firstGroupHasImageFile(bundle)) continue;
      const firstGroup = bundle.groups![0]!;
      if (!firstGroup.appId) continue;
      resolvedBundle.value = {
        bundleAppId,
        groupAppId: firstGroup.appId,
        containerMongoId: bundle.containerMongoId ?? null,
        groupName: firstGroup.name ?? null,
      };
      return;
    }
  } finally {
    isLoading.value = false;
  }
}

watch(
  () => props.candidateBundleAppIds,
  () => detectImageBundle(),
  { immediate: true, deep: true },
);
</script>

<template>
  <div
    v-if="resolvedBundle"
    class="pa-4"
    data-testid="image-bundle-pane"
  >
    <ImageBundleViewer
      :bundle-app-id="resolvedBundle.bundleAppId"
      :group-app-id="resolvedBundle.groupAppId"
      :container-mongo-id="resolvedBundle.containerMongoId"
      :group-name="resolvedBundle.groupName"
    />
  </div>
  <CenteredLoadingSpinner v-else-if="isLoading" />
</template>
