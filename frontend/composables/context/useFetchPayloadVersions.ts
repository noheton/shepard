/**
 * UI7 — context-level payload version composable.
 *
 * A thin reactive wrapper around the container-level
 * {@link useFetchPayloadVersions} composable.  Accepts `Ref` parameters so it
 * can be driven by reactive data from the FileReference detail page (e.g.
 * `fileReference.value.containerAppId` and a file name from the files list).
 *
 * When either input ref is undefined the composable is a no-op: `versions`
 * stays empty, `isLoading` stays false, no network call is made.
 *
 * Endpoint: GET /v2/file-containers/{containerAppId}/files/{fileName}/versions
 */

import {
  useFetchPayloadVersions as useContainerPayloadVersions,
  type PayloadVersionIO,
} from "~/composables/container/useFetchPayloadVersions";

export type { PayloadVersionIO };

export function useFetchPayloadVersions(
  containerAppId: Ref<string | undefined>,
  fileName: Ref<string | undefined>,
) {
  const versions = ref<PayloadVersionIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    const appId = containerAppId.value;
    const name = fileName.value;
    if (!appId || !name) return;

    const inner = useContainerPayloadVersions(appId, name);
    isLoading.value = true;
    error.value = null;

    await inner.load();

    versions.value = inner.versions.value;
    isLoading.value = inner.isLoading.value;
    error.value = inner.error.value;
  }

  return { versions, isLoading, error, load };
}
