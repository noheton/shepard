<script setup lang="ts">
/**
 * ThermographyCanvas — Three.js client-only stub for the
 * VIEW_RECIPE "thermography" renderer.
 *
 * Tier-1 reality: there is no IR frame data yet — frame extraction is
 * filed as OTVIS-PARSE-2. This canvas ships now (rather than in the
 * tier-2 commit) so the Three.js plumbing — scene, camera, OrbitControls,
 * ResizeObserver, captureDataUrl — is in place and tier-2 only has to
 * swap the placeholder plane texture for an IR sequence frame.
 *
 * Reactivity contract (tier-1):
 *   - props.label / props.backgroundColor changes → rebuild.
 *
 * Reactivity contract (tier-2, OTVIS-PARSE-2):
 *   - props.frameUrl change → swap plane texture via TextureLoader.
 *   - props.amplitudeUrl + props.phaseUrl → side-by-side dual plane.
 *
 * Must be wrapped in <ClientOnly> by the parent (WebGL is SSR-incompatible).
 * Mirrors {@link ~/components/shapes/UrdfCanvas.vue} for captureDataUrl +
 * ResizeObserver patterns.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16). Design: aidocs/integrations/114 §5.
 */
import { ref, onMounted, onUnmounted, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";

const props = withDefaults(
  defineProps<{
    /** Aspect ratio of the placeholder plane (width / height). */
    aspectRatio?: number;
    /** Canvas background colour (CSS). */
    backgroundColor?: string;
    /** Human-readable legend label. */
    label?: string;
  }>(),
  {
    aspectRatio: 1024 / 768,
    backgroundColor: "#0d0d0d",
    label: "Thermography (tier-1 stub)",
  },
);

const canvasRef = ref<HTMLCanvasElement | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let scene:    THREE.Scene | null = null;
let camera:   THREE.PerspectiveCamera | null = null;
let controls: OrbitControls | null = null;
let animId:   number | null = null;
let captureDataUrlFn: (() => string) | null = null;

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

  // Lighting — the placeholder plane uses MeshBasicMaterial so lighting
  // isn't strictly required, but the same scene infra will host a
  // MeshStandardMaterial in tier-2 once a real IR texture renders.
  scene.add(new THREE.AmbientLight(0xffffff, 0.7));

  // Placeholder plane — sized to the IR camera aspect ratio so tier-2's
  // texture swap drops in without re-sizing geometry. The chequer
  // pattern signals "no data yet" at a glance.
  const planeHeight = 1.0;
  const planeWidth = planeHeight * props.aspectRatio;
  const planeGeo = new THREE.PlaneGeometry(planeWidth, planeHeight);
  const placeholderTexture = buildPlaceholderTexture();
  const planeMat = new THREE.MeshBasicMaterial({ map: placeholderTexture, side: THREE.DoubleSide });
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

  return () => {
    ro.disconnect();
    if (animId !== null) cancelAnimationFrame(animId);
    controls?.dispose();
    placeholderTexture.dispose();
    planeGeo.dispose();
    planeMat.dispose();
    renderer?.dispose();
    renderer = null;
    scene = null;
    camera = null;
    controls = null;
  };
}

/**
 * Build a 64x64 chequer canvas texture so the placeholder plane reads as
 * "no data yet" rather than "blank because broken". Tier-2 swaps this
 * for an actual IR frame texture.
 */
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

function captureDataUrl(): string {
  return captureDataUrlFn?.() ?? "";
}

defineExpose({ captureDataUrl });

let cleanup: (() => void) | null = null;

onMounted(() => {
  if (canvasRef.value) cleanup = buildScene(canvasRef.value);
});

onUnmounted(() => {
  cleanup?.();
});

// Rebuild on background colour change.
watch(() => props.backgroundColor, () => {
  if (renderer) renderer.setClearColor(new THREE.Color(props.backgroundColor), 1);
});
</script>

<template>
  <div class="thermography-wrap" :style="{ background: backgroundColor }">
    <canvas ref="canvasRef" class="thermography-canvas" />
    <div class="thermography-overlay">
      <v-chip size="x-small" color="warning" variant="tonal">
        Tier-1 stub - no frame data yet
      </v-chip>
      <span class="text-caption text-medium-emphasis ml-2">{{ label }}</span>
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
