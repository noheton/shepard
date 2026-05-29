<script setup lang="ts">
/**
 * /scene-graphs/[appId] — placeholder for SCENEGRAPH-REST-1.
 *
 * Backend shipped 2026-05-29: 9 REST endpoints under /v2/scene-graphs +
 * 7 MCP tools (scene_graph_*) + URDF export. Real browser UI for the
 * frame tree + joint editor + URDF preview is queued as
 * SCENEGRAPH-REST-1-UI (see aidocs/16).
 *
 * Backlog: SCENEGRAPH-REST-1-UI (blocked-on-backend-merge)
 * REST:    GET /v2/scene-graphs/{appId}
 * Design:  aidocs/data/85-coordinate-frame-tree.md
 */
import PlaceholderPageHeader from "~/components/common/placeholder/PlaceholderPageHeader.vue";
import PlaceholderRestDump from "~/components/common/placeholder/PlaceholderRestDump.vue";
import PlaceholderImplStatus from "~/components/common/placeholder/PlaceholderImplStatus.vue";

useHead({ title: "Scene graph | shepard" });

const route = useRoute();
const appId = computed(() => String(route.params.appId ?? ""));
const restPath = computed(() => `/v2/scene-graphs/${appId.value}`);
const urdfPath = computed(() => `/v2/scene-graphs/${appId.value}/export.urdf`);
</script>

<template>
  <div class="pa-6 d-flex flex-column ga-4">
    <PlaceholderPageHeader
      title="Scene graph"
      :subtitle="`Digital-twin scene ${appId} — frame tree + joints + URDF export.`"
    />
    <PlaceholderImplStatus
      backend="shipped"
      backlog-row="SCENEGRAPH-REST-1-UI"
      design-doc="aidocs/data/85-coordinate-frame-tree.md"
      :endpoint="restPath"
      notes="Backend live (9 REST endpoints + 7 MCP tools + URDF export). Real
        browser UI for the frame tree + joint editor + URDF preview queued
        as SCENEGRAPH-REST-1-UI. Use the URDF export below to drop into
        Foxglove / RViz today; full in-browser visualisation lands with
        URDF-WEBVIEW-1 phase 2 wiring against this REST surface."
    />
    <PlaceholderRestDump
      :endpoint="restPath"
      :hint="`Scene graph JSON — GET ${restPath} returns frames + joints. URDF export at ${urdfPath}.`"
    />
  </div>
</template>
