<script lang="ts" setup>
/**
 * MFFD-RENDER-MATERIAL-BATCH-TRACE (slice 3 UI)
 *
 * In-context pane mounted on mffd:material-batch DataObject detail pages.
 * Calls POST /v2/shapes/render with the MaterialBatchTraceViewRenderer's
 * shape IRI and the current batch DataObject's appId. Renders the list of
 * every process step (AFP course, weld step, NDT measurement, …) that
 * annotated itself as consuming this material batch.
 *
 * Mounting guard: the DataObject detail page mounts this pane only when
 * the DataObject carries a `urn:shepard:mffd:batch-id` annotation
 * (isMaterialBatchDo guard). This avoids a stray render call on every
 * non-batch DataObject.
 *
 * Navigation: consumers in the same collection are rendered as NuxtLinks;
 * consumers in other collections (name-lookup 404) fall back to plain
 * appId text with a clipboard copy button.
 *
 * Backend: POST /v2/shapes/render
 *   body: { shapeIri, focusShepardId }
 *   → ShapesRenderResponseIO { channelBindings[] }
 *     each binding: { role, status: "OK"|"MISSING", resolved: { channelRef: appId } }
 *
 * Cross-refs:
 *   aidocs/16  — MFFD-RENDER-MATERIAL-BATCH-TRACE slice 3
 *   MaterialBatchTraceViewRenderer.java (slice 2)
 *   MffdMaterialBatchKind.java          (slice 1 — TRACE_SHAPE_IRI constant)
 */
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const h: Record<string, string> = {
    Accept: "application/json",
    "Content-Type": "application/json",
  };
  if (accessToken) h["Authorization"] = `Bearer ${accessToken}`;
  return h;
}

const props = defineProps<{
  /** UUID v7 of the material-batch DataObject whose consumers we trace. */
  dataObjectAppId: string;
  /** UUID v7 of the current Collection — used to resolve consumer DataObject names. */
  collectionAppId: string;
}>();

/** Shape IRI dispatched to MaterialBatchTraceViewRenderer. */
const TRACE_SHAPE_IRI =
  "urn:shepard:shape:mffd-material-batch-trace#MaterialBatchTraceShape";

interface ConsumerRow {
  appId: string;
  name: string | null;
  /** true when GET /v2/collections/{collectionAppId}/data-objects/{appId} succeeded */
  inCurrentCollection: boolean;
}

const consumers = ref<ConsumerRow[]>([]);
const isLoading = ref(true);
const fetchError = ref<string | null>(null);
const isEmpty = ref(false);

async function fetchTrace(): Promise<void> {
  isLoading.value = true;
  fetchError.value = null;
  isEmpty.value = false;
  consumers.value = [];

  try {
    const res = await fetch(v2BaseUrl() + "/v2/shapes/render", {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({
        shapeIri: TRACE_SHAPE_IRI,
        focusShepardId: props.dataObjectAppId,
      }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      fetchError.value =
        (err as { error?: string }).error ?? `HTTP ${res.status}`;
      return;
    }

    const body = await res.json() as {
      channelBindings?: Array<{
        status: string;
        resolved?: { channelRef?: string } | null;
      }>;
    };

    const bindings = body.channelBindings ?? [];
    const okAppIds = bindings
      .filter(b => b.status === "OK" && b.resolved?.channelRef)
      .map(b => b.resolved!.channelRef!);

    if (okAppIds.length === 0) {
      isEmpty.value = true;
      return;
    }

    // Populate consumer rows; resolve names against the current collection.
    consumers.value = okAppIds.map(appId => ({
      appId,
      name: null,
      inCurrentCollection: false,
    }));

    await resolveNames(okAppIds);
  } catch (e) {
    fetchError.value = e instanceof Error ? e.message : String(e);
  } finally {
    isLoading.value = false;
  }
}

async function resolveNames(appIds: string[]): Promise<void> {
  await Promise.all(
    appIds.map(async appId => {
      try {
        const res = await fetch(
          `${v2BaseUrl()}/v2/collections/${encodeURIComponent(props.collectionAppId)}/data-objects/${encodeURIComponent(appId)}`,
          { headers: authHeaders() },
        );
        if (res.ok) {
          const doData = await res.json() as { name?: string };
          const idx = consumers.value.findIndex(c => c.appId === appId);
          if (idx >= 0) {
            consumers.value[idx] = {
              appId,
              name: doData.name ?? null,
              inCurrentCollection: true,
            };
          }
        }
      } catch { /* skip — show appId fallback */ }
    }),
  );
}

function copyToClipboard(text: string): void {
  navigator.clipboard.writeText(text).catch(() => {});
}

onMounted(fetchTrace);
</script>

<template>
  <div data-testid="material-batch-trace-pane">
    <!-- Loading -->
    <v-progress-linear
      v-if="isLoading"
      indeterminate
      aria-label="Loading material batch consumers"
    />

    <!-- Error -->
    <v-alert
      v-else-if="fetchError"
      type="warning"
      density="compact"
      variant="tonal"
      class="my-2"
      :text="`Could not load batch lineage: ${fetchError}`"
    />

    <!-- Empty state: batch exists but nothing has consumed it yet -->
    <div
      v-else-if="isEmpty"
      class="text-body-2 text-medium-emphasis py-2"
      data-testid="material-batch-trace-empty"
    >
      No process steps have referenced this material batch yet.
    </div>

    <!-- Consumer list -->
    <v-list
      v-else
      density="compact"
      class="pa-0"
      data-testid="material-batch-trace-list"
    >
      <v-list-item
        v-for="consumer in consumers"
        :key="consumer.appId"
        :prepend-icon="'mdi-arrow-right-circle-outline'"
        class="px-0"
      >
        <!-- In-collection consumer: navigable link -->
        <NuxtLink
          v-if="consumer.inCurrentCollection"
          :to="`${collectionsPath}${collectionAppId}${dataObjectsPathFragment}${consumer.appId}`"
          class="text-primary text-body-2"
        >
          {{ consumer.name ?? consumer.appId }}
        </NuxtLink>

        <!-- Cross-collection consumer: appId + copy button -->
        <span
          v-else
          class="text-body-2 text-medium-emphasis"
        >
          {{ consumer.name ?? consumer.appId }}
          <v-btn
            icon="mdi-content-copy"
            size="x-small"
            variant="text"
            :aria-label="`Copy appId ${consumer.appId}`"
            @click="copyToClipboard(consumer.appId)"
          />
        </span>
      </v-list-item>
    </v-list>
  </div>
</template>
