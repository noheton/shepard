<script setup lang="ts">
/**
 * SpatialPointsCanvas — Three.js renderer for SpatialDataContainer payloads.
 *
 * Two render modes:
 *
 *   "pointcloud"  — THREE.Points scatter with vertex colours. Decimates to
 *                   ``maxPoints`` via grid-voxel downsampling so the browser
 *                   stays responsive on the full ~4000-point Keyence laser
 *                   stripe and the larger TPS scans.
 *   "trajectory"  — THREE.Line strip with time-as-colour. Renders the TCP
 *                   path as a colour-mapped curve in the same unit cube,
 *                   matching the Trace3DCanvas axis convention.
 *
 * Shared:
 *   - Normalises XYZ to a unit cube around (0,0,0) so the OrbitControls
 *     pivot lands sensibly.
 *   - Renders RGB axes (X=red, Y=green, Z=blue) so the viewer aligns with
 *     the AFP-cell convention used in Trace3DCanvas.
 *
 * GAP-5 / W7 from aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md.
 * Companion: ``frontend/components/shapes/Trace3DCanvas.vue`` (the line-trace
 * sibling for VIEW_RECIPE Trace3D output).
 */
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { colormapRgb, normalizeValues, type ColormapName } from "~/utils/colormap";
import {
  voxelGridDownsample,
  type SpatialPoint,
} from "~/utils/spatialDownsample";

export type SpatialRenderMode = "pointcloud" | "trajectory";
export type { SpatialPoint };

const props = withDefaults(
  defineProps<{
    points: SpatialPoint[];
    mode?: SpatialRenderMode;
    colormap?: ColormapName;
    label?: string;
    /** Decimation cap. Voxel-grid downsampling kicks in above this. */
    maxPoints?: number;
    /** Pointcloud-only: visual size of each point in world units. */
    pointSize?: number;
  }>(),
  {
    mode: "pointcloud",
    colormap: "viridis",
    label: "Value",
    maxPoints: 500_000,
    pointSize: 0.005,
  },
);

const canvasRef = ref<HTMLCanvasElement | null>(null);
const decimatedCount = ref<number>(0);

let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let animId: number | null = null;

function buildScene(canvas: HTMLCanvasElement): () => void {
  const w = canvas.clientWidth || 800;
  const h = canvas.clientHeight || 500;

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setSize(w, h, false);
  renderer.setClearColor(0x0d0d0d, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, w / h, 0.0001, 10000);
  camera.position.set(1.2, 1.2, 1.8);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;

  // ── axes (red/green/blue for X/Y/Z) ─────────────────────────────────────
  const axisLen = 1;
  const axisGeo = (dir: THREE.Vector3, color: number) => {
    const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), dir.clone().multiplyScalar(axisLen)]);
    return new THREE.Line(g, new THREE.LineBasicMaterial({ color }));
  };
  scene.add(axisGeo(new THREE.Vector3(1, 0, 0), 0xff4444));
  scene.add(axisGeo(new THREE.Vector3(0, 1, 0), 0x44ff44));
  scene.add(axisGeo(new THREE.Vector3(0, 0, 1), 0x4444ff));

  // ── source pts → decimated normalised pts ───────────────────────────────
  const sourcePts = props.points;
  decimatedCount.value = 0;

  if (sourcePts.length >= 1) {
    const decimated =
      props.mode === "pointcloud"
        ? voxelGridDownsample(sourcePts, props.maxPoints)
        : sourcePts;
    decimatedCount.value = decimated.length;

    const xs = decimated.map(p => p.x);
    const ys = decimated.map(p => p.y);
    const zs = decimated.map(p => p.z);
    const xMin = Math.min(...xs), xMax = Math.max(...xs);
    const yMin = Math.min(...ys), yMax = Math.max(...ys);
    const zMin = Math.min(...zs), zMax = Math.max(...zs);
    const range = Math.max(xMax - xMin, yMax - yMin, zMax - zMin, 1e-9);
    const cx = (xMin + xMax) / 2;
    const cy = (yMin + yMax) / 2;
    const cz = (zMin + zMax) / 2;

    const n = decimated.length;
    const positions = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const p = decimated[i]!;
      positions[i * 3]     = (p.x - cx) / range;
      positions[i * 3 + 1] = (p.y - cy) / range;
      positions[i * 3 + 2] = (p.z - cz) / range;
    }

    // Value-vector chosen per mode:
    //   pointcloud: use ``value`` if any present, else fall back to z (height).
    //   trajectory: use ``t`` if any present, else fall back to point-index.
    const haveValues = decimated.some(p => p.value !== undefined && isFinite(p.value));
    const haveTimes = decimated.some(p => p.t !== undefined);
    const vals = decimated.map((p, i) => {
      if (props.mode === "trajectory") {
        return haveTimes ? Number(p.t ?? 0) : i;
      }
      return haveValues ? Number(p.value ?? p.z) : p.z;
    });
    const normVals = normalizeValues(vals);

    const colors = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const [r, g, b] = colormapRgb(normVals[i] ?? 0, props.colormap);
      colors[i * 3] = r;
      colors[i * 3 + 1] = g;
      colors[i * 3 + 2] = b;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));

    if (props.mode === "pointcloud") {
      const material = new THREE.PointsMaterial({
        size: props.pointSize,
        vertexColors: true,
        sizeAttenuation: true,
      });
      scene.add(new THREE.Points(geometry, material));
    } else {
      // trajectory: LineStrip with vertex colours (time-as-colour by default)
      const material = new THREE.LineBasicMaterial({ vertexColors: true });
      scene.add(new THREE.Line(geometry, material));
      // start + end markers for orientation
      const dotGeo = new THREE.SphereGeometry(0.018, 8, 8);
      const startDot = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0x44ff44 }));
      const endDot = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0xff4444 }));
      startDot.position.set(positions[0]!, positions[1]!, positions[2]!);
      endDot.position.set(positions[(n - 1) * 3]!, positions[(n - 1) * 3 + 1]!, positions[(n - 1) * 3 + 2]!);
      scene.add(startDot, endDot);
    }
  }

  // ── render loop ─────────────────────────────────────────────────────────
  const animate = () => {
    animId = requestAnimationFrame(animate);
    controls!.update();
    renderer!.render(scene, camera);
  };
  animate();

  const ro = new ResizeObserver(() => {
    const cw = canvas.clientWidth, ch = canvas.clientHeight;
    camera.aspect = cw / ch;
    camera.updateProjectionMatrix();
    renderer!.setSize(cw, ch, false);
  });
  ro.observe(canvas);

  return () => {
    ro.disconnect();
    if (animId !== null) cancelAnimationFrame(animId);
    controls!.dispose();
    renderer!.dispose();
  };
}

let cleanup: (() => void) | null = null;

onMounted(() => {
  if (canvasRef.value) cleanup = buildScene(canvasRef.value);
});

watch(
  [() => props.points, () => props.mode, () => props.colormap, () => props.maxPoints],
  () => {
    cleanup?.();
    if (canvasRef.value) cleanup = buildScene(canvasRef.value);
  },
);

onUnmounted(() => {
  cleanup?.();
});

const renderedCount = computed(() => decimatedCount.value);
const sourceCount = computed(() => props.points.length);
const isDecimated = computed(() => renderedCount.value < sourceCount.value);
</script>

<template>
  <div class="spatial-points-wrap">
    <canvas ref="canvasRef" class="spatial-points-canvas" />
    <div class="spatial-points-legend text-caption text-medium-emphasis">
      <span class="ml-1">
        <strong>{{ mode === "pointcloud" ? "Pointcloud" : "Trajectory" }}</strong>
      </span>
      <span class="ml-3">Colour: {{ label }} ({{ colormap }})</span>
      <span v-if="mode === 'trajectory'" class="ml-3">
        <span class="dot" style="background:#44ff44" /> Start
        <span class="dot ml-2" style="background:#ff4444" /> End
      </span>
      <span class="ml-3" data-testid="point-count">
        {{ renderedCount.toLocaleString() }} / {{ sourceCount.toLocaleString() }} pts
        <span v-if="isDecimated"> (decimated)</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.spatial-points-wrap {
  position: relative;
  width: 100%;
  height: 500px;
  background: #0d0d0d;
  border-radius: 8px;
  overflow: hidden;
}
.spatial-points-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.spatial-points-legend {
  position: absolute;
  bottom: 8px;
  left: 12px;
  color: #cccccc;
  background: rgba(0, 0, 0, 0.4);
  padding: 4px 8px;
  border-radius: 4px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  vertical-align: middle;
}
</style>
