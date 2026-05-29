<script lang="ts" setup>
definePageMeta({ layout: 'default' })
const route = useRoute()
const sceneId = route.params.sceneId as string
</script>
<template>
  <PageShell>
    <PlaceholderPageHeader title="Digital Twin Scene" :entity-id="sceneId" />
    <PlaceholderImplStatus task-id="SCENEGRAPH-REST-1" status="backend-only" />
    <PlaceholderRestDump :endpoint="`/v2/scene-graphs/${sceneId}`" />
  </PageShell>
</template>
