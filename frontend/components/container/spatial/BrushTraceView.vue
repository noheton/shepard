<script setup lang="ts">
/**
 * BrushTraceView — fetches SpatialDataPoints from the v1 payload endpoint
 * and renders them as a 3D line trace using Three.js.
 *
 * SPATIAL-V6-004: replaces the "in development" banner on the spatial
 * container page with a real 3D viewer.
 *
 * Must be wrapped in <ClientOnly> by the parent (Three.js is client-only).
 *
 * Accepted prop:
 *   containerId — the Neo4j long id of the SpatialDataContainer (available
 *                 on the spatialData.value.id field from the accessor).
 */
import { onMounted, onUnmounted, ref, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";

interface SpatialDataPoint {
  timestamp?: number;
  x: number;
  y: number;
  z: number;
  measurements: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

const props = defineProps<{
  containerId: number;
}>();

// ── state ──────────────────────────────────────────────────────────────────────

const canvasRef = ref<HTMLCanvasElement | null>(null);
const isLoading = ref(true);
const isError = ref(false);
const isEmpty = ref(false);
const errorMessage = ref("");

let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let animId: number | null = null;
let cleanup: (() => void) | null = null;

// ── data fetching ──────────────────────────────────────────────────────────────

async function fetchPoints(): Promise<SpatialDataPoint[]> {
  const url = `/shepard/api/spatial-data-containers/${props.containerId}/payload?limit=5000`;
  const res = await fetch(url, { credentials: "include" });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} — ${res.statusText}`);
  }
  return res.json() as Promise<SpatialDataPoint[]>;
}

// ── Three.js scene ─────────────────────────────────────────────────────────────

function buildScene(canvas: HTMLCanvasElement, points: SpatialDataPoint[]) {
  const w = canvas.clientWidth || 800;
  const h = canvas.clientHeight || 500;

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setSize(w, h, false);
  renderer.setClearColor(0x0d0d0d, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, w / h, 0.0001, 100000);
  camera.position.set(1, 1, 2);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;

  // ── axis lines ───────────────────────────────────────────────────────────────
  const axisLen = 1;
  const axisLine = (dir: THREE.Vector3, color: number) => {
    const g = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0, 0, 0),
      dir.clone().multiplyScalar(axisLen),
    ]);
    return new THREE.Line(g, new THREE.LineBasicMaterial({ color }));
  };
  scene.add(axisLine(new THREE.Vector3(1, 0, 0), 0xff4444));
  scene.add(axisLine(new THREE.Vector3(0, 1, 0), 0x44ff44));
  scene.add(axisLine(new THREE.Vector3(0, 0, 1), 0x4444ff));

  // ── trace line ───────────────────────────────────────────────────────────────
  const n = points.length;
  if (n >= 2) {
    // Sort by timestamp so the line follows the trajectory in time order.
    const sorted = [...points].sort((a, b) => (a.timestamp ?? 0) - (b.timestamp ?? 0));

    // Normalise positions into a unit cube centred at origin.
    const xs = sorted.map(p => p.x);
    const ys = sorted.map(p => p.y);
    const zs = sorted.map(p => p.z);
    const xMin = Math.min(...xs), xMax = Math.max(...xs);
    const yMin = Math.min(...ys), yMax = Math.max(...ys);
    const zMin = Math.min(...zs), zMax = Math.max(...zs);
    const range = Math.max(xMax - xMin, yMax - yMin, zMax - zMin, 1e-9);
    const cx = (xMin + xMax) / 2;
    const cy = (yMin + yMax) / 2;
    const cz = (zMin + zMax) / 2;

    const positions = new Float32Array(n * 3);
    sorted.forEach((p, i) => {
      positions[i * 3]     = (p.x - cx) / range;
      positions[i * 3 + 1] = (p.y - cy) / range;
      positions[i * 3 + 2] = (p.z - cz) / range;
    });

    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    const mat = new THREE.LineBasicMaterial({ color: 0xffffff, linewidth: 1 });
    scene.add(new THREE.Line(geo, mat));

    // Start (green) and end (red) dot markers.
    const dotGeo = new THREE.SphereGeometry(0.018, 8, 8);
    const startDot = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0x44ff44 }));
    const endDot   = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0xff4444 }));
    startDot.position.set(positions[0]!, positions[1]!, positions[2]!);
    const last = (n - 1) * 3;
    endDot.position.set(positions[last]!, positions[last + 1]!, positions[last + 2]!);
    scene.add(startDot, endDot);

    // Fit camera to bounding box.
    camera.position.set(
      positions[0]! + 1.5,
      positions[1]! + 1.5,
      positions[2]! + 1.5,
    );
    controls.target.set(0, 0, 0);
    controls.update();
  }

  // ── render loop ───────────────────────────────────────────────────────────────
  const animate = () => {
    animId = requestAnimationFrame(animate);
    controls!.update();
    renderer!.render(scene, camera);
  };
  animate();

  // ── resize observer ───────────────────────────────────────────────────────────
  const ro = new ResizeObserver(() => {
    const cw = canvas.clientWidth;
    const ch = canvas.clientHeight;
    if (cw === 0 || ch === 0) return;
    camera.aspect = cw / ch;
    camera.updateProjectionMatrix();
    renderer!.setSize(cw, ch, false);
  });
  ro.observe(canvas);

  return () => {
    ro.disconnect();
    if (animId !== null) cancelAnimationFrame(animId);
    controls?.dispose();
    renderer?.dispose();
    renderer = null;
    controls = null;
    animId = null;
  };
}

// ── lifecycle ─────────────────────────────────────────────────────────────────

async function initialise() {
  isLoading.value = true;
  isError.value = false;
  isEmpty.value = false;
  cleanup?.();
  cleanup = null;

  try {
    const points = await fetchPoints();
    isLoading.value = false;
    if (!points || points.length === 0) {
      isEmpty.value = true;
      return;
    }
    // Wait for canvas to be in the DOM before building the scene.
    await nextTick();
    if (canvasRef.value) {
      cleanup = buildScene(canvasRef.value, points);
    }
  } catch (err) {
    isLoading.value = false;
    isError.value = true;
    errorMessage.value = err instanceof Error ? err.message : String(err);
  }
}

onMounted(() => {
  initialise();
});

watch(
  () => props.containerId,
  () => {
    initialise();
  },
);

onUnmounted(() => {
  cleanup?.();
});
</script>

<template>
  <div class="brush-trace-wrap">
    <!-- Loading state -->
    <div v-if="isLoading" class="brush-trace-overlay">
      <v-progress-circular indeterminate color="primary" size="48" />
      <span class="ml-3 text-body-2">Loading spatial data…</span>
    </div>

    <!-- Error state -->
    <div v-else-if="isError" class="brush-trace-overlay">
      <v-alert
        type="error"
        variant="tonal"
        prepend-icon="mdi-alert-circle-outline"
        title="Failed to load spatial data"
        :text="errorMessage || 'An unexpected error occurred while fetching the spatial data points.'"
        class="ma-4"
      />
    </div>

    <!-- Empty state -->
    <div v-else-if="isEmpty" class="brush-trace-overlay">
      <v-alert
        type="info"
        variant="tonal"
        prepend-icon="mdi-map-marker-off-outline"
        title="No spatial data"
        text="This container has no spatial data points yet. Upload data using the API or the shepard-admin CLI."
        class="ma-4"
      />
    </div>

    <!-- Canvas (shown when data is loaded) -->
    <canvas v-else ref="canvasRef" class="brush-trace-canvas" />

    <!-- Legend bar (shown only when canvas is active) -->
    <div v-if="!isLoading && !isError && !isEmpty" class="brush-trace-legend text-caption text-medium-emphasis">
      <span class="dot" style="background:#44ff44" />
      Start
      <span class="dot ml-2" style="background:#ff4444" />
      End
      <span class="ml-3">Drag to orbit · Scroll to zoom</span>
    </div>
  </div>
</template>

<style scoped>
.brush-trace-wrap {
  position: relative;
  width: 100%;
  height: 500px;
  background: #0d0d0d;
  border-radius: 8px;
  overflow: hidden;
}

.brush-trace-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.brush-trace-overlay {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #eee;
  flex-direction: column;
}

.brush-trace-legend {
  position: absolute;
  bottom: 8px;
  left: 12px;
  color: #aaa;
  display: flex;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}

.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>
