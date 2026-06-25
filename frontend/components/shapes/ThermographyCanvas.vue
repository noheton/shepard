<script setup lang="ts">
/**
 * ThermographyCanvas — Three.js client-only IR-frame renderer for the
 * VIEW_RECIPE "thermography" renderer.
 *
 * Tier-1 (OTVIS-VIEW-1): Three.js plumbing in place; checkerboard plane
 * shown when no frame URL is provided.
 *
 * Tier-2 (this commit, PLACEHOLDER-thermography-canvas): frameUrl prop
 * wired. When set, THREE.TextureLoader fetches the blob URL produced by
 * DataObjectOtvisViewer / ThermographyView → swaps the plane texture so
 * the canvas shows the actual IR heatmap. The dual-pane (amplitudeUrl +
 * phaseUrl side-by-side) is declared but not yet geometrically separated
 * — both URLs fold onto the same plane (PLACEHOLDER-thermo-dual-pane).
 *
 * Reactivity contract:
 *   - frameUrl change → reload plane texture via TextureLoader.
 *   - amplitudeUrl OR phaseUrl → same (primary channel wins; dual-pane
 *     geometry lands in PLACEHOLDER-thermo-dual-pane).
 *   - backgroundColor change → re-clear.
 *
 * Must be wrapped in <ClientOnly> by the parent (WebGL is SSR-incompatible).
 * Mirrors {@link ~/components/shapes/UrdfCanvas.vue} for captureDataUrl +
 * ResizeObserver patterns.
 *
 * Task: PLACEHOLDER-thermography-canvas (aidocs/16).
 * Design: aidocs/integrations/114-process-monitoring-parser-plugin.md §5.
 */
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";

const props = withDefaults(
  defineProps<{
    /** Aspect ratio of the plane (width / height). Defaults to OTvis standard 1024×768. */
    aspectRatio?: number;
    /** Canvas background colour (CSS). */
    backgroundColor?: string;
    /** Human-readable legend label. */
    label?: string;
    /**
     * Blob URL for the primary IR frame PNG.  When set, the checkerboard
     * placeholder is replaced by the real heatmap.  Produced by
     * DataObjectOtvisViewer / ThermographyView via POST /v2/shapes/render
     * (Accept: image/png).
     */
    frameUrl?: string | null;
    /**
     * Blob URL for the lock-in amplitude channel frame (left pane in the
     * future dual-pane layout — PLACEHOLDER-thermo-dual-pane). For now the
     * amplitude channel renders on the primary plane when frameUrl is absent.
     */
    amplitudeUrl?: string | null;
    /**
     * Blob URL for the lock-in phase channel frame (right pane in the future
     * dual-pane layout). For now the phase channel renders on the primary
     * plane when both frameUrl and amplitudeUrl are absent.
     */
    phaseUrl?: string | null;
  }>(),
  {
    aspectRatio: 1024 / 768,
    backgroundColor: "#0d0d0d",
    label: "Thermography",
    frameUrl: null,
    amplitudeUrl: null,
    phaseUrl: null,
  },
);

/** The active URL to display: frameUrl → amplitudeUrl → phaseUrl → null. */
const activeUrl = computed(() => props.frameUrl ?? props.amplitudeUrl ?? props.phaseUrl ?? null);
/** True when we have a real frame to show. */
const hasFrame = computed(() => activeUrl.value !== null);

const canvasRef = ref<HTMLCanvasElement | null>(null);
const isTextureLoading = ref(false);
const textureError = ref<string | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let scene:    THREE.Scene | null = null;
let camera:   THREE.PerspectiveCamera | null = null;
let controls: OrbitControls | null = null;
let animId:   number | null = null;

/** The plane material — kept so we can swap its map texture in place. */
let planeMat: THREE.MeshBasicMaterial | null = null;
/** The current live texture (either placeholder or loaded frame). */
let currentTexture: THREE.Texture | null = null;

let captureDataUrlFn: (() => string) | null = null;
let cleanupFn: (() => void) | null = null;

const loader = new THREE.TextureLoader();

function buildPlaceholderTexture(): THREE.CanvasTexture {
  const size = 64;
  const c = document.createElement("canvas");
  c.width = size; c.height = size;
  const ctx = c.getContext("2d");
  if (ctx) {
    const tile = 8;
    for (let y = 0; y < size; y += tile) {
      for (let x = 0; x < size; x += tile) {
        const dark = ((x / tile + y / tile) & 1) === 0;
        ctx.fillStyle = dark ? "#1a1a1a" : "#262626";
        ctx.fillRect(x, y, tile, tile);
      }
    }
  }
  const tex = new THREE.CanvasTexture(c);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  return tex;
}

function swapTexture(tex: THREE.Texture) {
  if (!planeMat) return;
  currentTexture?.dispose();
  currentTexture = tex;
  planeMat.map = tex;
  planeMat.needsUpdate = true;
}

async function loadFrameTexture(url: string) {
  isTextureLoading.value = true;
  textureError.value = null;
  try {
    const tex = await loader.loadAsync(url);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.magFilter = THREE.LinearFilter;
    tex.minFilter = THREE.LinearMipmapLinearFilter;
    tex.generateMipmaps = true;
    swapTexture(tex);
  } catch (e) {
    textureError.value = `Failed to load frame texture — ${String((e as Error).message ?? e)}`;
  } finally {
    isTextureLoading.value = false;
  }
}

function buildScene(canvas: HTMLCanvasElement) {
  const w = canvas.clientWidth || 800;
  const h = canvas.clientHeight || 500;

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setSize(w, h, false);
  renderer.setClearColor(new THREE.Color(props.backgroundColor), 1);

  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(50, w / h, 0.001, 100);
  camera.position.set(0, 0, 2.0);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.target.set(0, 0, 0);

  scene.add(new THREE.AmbientLight(0xffffff, 0.7));

  const planeHeight = 1.0;
  const planeWidth  = planeHeight * props.aspectRatio;
  const planeGeo = new THREE.PlaneGeometry(planeWidth, planeHeight);
  currentTexture = buildPlaceholderTexture();
  planeMat = new THREE.MeshBasicMaterial({ map: currentTexture, side: THREE.DoubleSide });
  const plane = new THREE.Mesh(planeGeo, planeMat);
  scene.add(plane);

  captureDataUrlFn = () => {
    if (!renderer || !canvasRef.value || !scene || !camera) return "";
    renderer.render(scene, camera);
    return canvasRef.value.toDataURL("image/png");
  };

  const animate = () => {
    animId = requestAnimationFrame(animate);
    if (controls) controls.update();
    if (renderer && scene && camera) renderer.render(scene, camera);
  };
  animate();

  const ro = new ResizeObserver(() => {
    if (!canvas || !camera || !renderer) return;
    const cw = canvas.clientWidth, ch = canvas.clientHeight;
    if (cw === 0 || ch === 0) return;
    camera.aspect = cw / ch;
    camera.updateProjectionMatrix();
    renderer.setSize(cw, ch, false);
  });
  ro.observe(canvas);

  // Load initial frame if already set when the scene is built.
  if (activeUrl.value) void loadFrameTexture(activeUrl.value);

  return () => {
    ro.disconnect();
    if (animId !== null) cancelAnimationFrame(animId);
    controls?.dispose();
    currentTexture?.dispose();
    planeMat?.dispose();
    planeGeo.dispose();
    renderer?.dispose();
    renderer = null; scene = null; camera = null; controls = null; planeMat = null; currentTexture = null;
  };
}

function captureDataUrl(): string {
  return captureDataUrlFn?.() ?? "";
}

defineExpose({ captureDataUrl });

onMounted(() => {
  if (canvasRef.value) cleanupFn = buildScene(canvasRef.value);
});

onUnmounted(() => {
  cleanupFn?.();
  cleanupFn = null;
});

watch(() => props.backgroundColor, () => {
  if (renderer) renderer.setClearColor(new THREE.Color(props.backgroundColor), 1);
});

watch(activeUrl, (url) => {
  if (!renderer) return; // scene not built yet — onMounted handles it
  if (url) {
    void loadFrameTexture(url);
  } else {
    swapTexture(buildPlaceholderTexture());
  }
});
</script>

<template>
  <div class="thermography-wrap" :style="{ background: backgroundColor }">
    <canvas ref="canvasRef" class="thermography-canvas" />

    <!-- status overlay -->
    <div class="thermography-overlay">
      <template v-if="isTextureLoading">
        <v-progress-circular size="16" width="2" indeterminate color="primary" />
        <span class="text-caption text-medium-emphasis ml-2">Loading frame…</span>
      </template>
      <template v-else-if="textureError">
        <v-chip size="x-small" color="error" variant="tonal">Frame load error</v-chip>
        <span class="text-caption text-error ml-2">{{ textureError }}</span>
      </template>
      <template v-else-if="hasFrame">
        <v-chip size="x-small" color="success" variant="tonal">Live frame</v-chip>
        <span class="text-caption text-medium-emphasis ml-2">{{ label }}</span>
      </template>
      <template v-else>
        <v-chip size="x-small" color="warning" variant="tonal">No frame data</v-chip>
        <span class="text-caption text-medium-emphasis ml-2">{{ label }}</span>
      </template>
    </div>
  </div>
</template>

<style scoped>
.thermography-wrap {
  position: relative;
  width: 100%;
  height: 500px;
  border-radius: 8px;
  overflow: hidden;
}
.thermography-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.thermography-overlay {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  align-items: center;
  pointer-events: none;
}
</style>
