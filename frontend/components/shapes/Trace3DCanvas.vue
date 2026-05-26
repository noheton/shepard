<script setup lang="ts">
/**
 * Trace3DCanvas — Three.js client-only 3D path renderer for VIEW_RECIPE outputs.
 *
 * Renders time-aligned X/Y/Z channels as a color-mapped line in a WebGL canvas.
 * Must be wrapped in <ClientOnly> by the parent.
 *
 * Beta (TPL2b): channel arrays are supplied by the parent after it fetches TS data
 * using the binding's channelSelector. Live resolution (TPL2c) is deferred.
 */
import { computed, onMounted, onUnmounted, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { colormapRgb, normalizeValues, type ColormapName } from "~/utils/colormap";

interface TracePoint {
  x: number;
  y: number;
  z: number;
  value: number;   // NaN when no value channel
  t: number;       // timestamp, nanoseconds — stored for brush time display
  eulerA?: number; // KUKA A: extrinsic rotation around world Z, degrees
  eulerB?: number; // KUKA B: extrinsic rotation around world Y, degrees
  eulerC?: number; // KUKA C: extrinsic rotation around world X, degrees
}

const props = defineProps<{
  points: TracePoint[];
  colormap?: ColormapName;
  label?: string;
  /** Normalized 0–1 time range for the brush highlight. */
  brushRange?: { from: number; to: number };
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let animId: number | null = null;

const hasOrientationArrows = computed(() =>
  props.points.some(p => p.eulerA !== undefined || p.eulerB !== undefined || p.eulerC !== undefined),
);

function buildScene(canvas: HTMLCanvasElement) {
  const w = canvas.clientWidth || 800;
  const h = canvas.clientHeight || 500;

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setSize(w, h, false);
  renderer.setClearColor(0x0d0d0d, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, w / h, 0.0001, 10000);
  camera.position.set(1, 1, 2);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;

  // ── axis lines ─────────────────────────────────────────────────────────────
  const axisLen = 1;
  const axisGeo = (dir: THREE.Vector3, color: number) => {
    const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), dir.clone().multiplyScalar(axisLen)]);
    return new THREE.Line(g, new THREE.LineBasicMaterial({ color }));
  };
  scene.add(axisGeo(new THREE.Vector3(1, 0, 0), 0xff4444));
  scene.add(axisGeo(new THREE.Vector3(0, 1, 0), 0x44ff44));
  scene.add(axisGeo(new THREE.Vector3(0, 0, 1), 0x4444ff));

  // ── trace geometry ─────────────────────────────────────────────────────────
  const pts = props.points;
  const n = pts.length;
  if (n >= 2) {
    // Normalize positions to unit cube
    const xs = pts.map(p => p.x), ys = pts.map(p => p.y), zs = pts.map(p => p.z);
    const [xMin, xMax] = [Math.min(...xs), Math.max(...xs)];
    const [yMin, yMax] = [Math.min(...ys), Math.max(...ys)];
    const [zMin, zMax] = [Math.min(...zs), Math.max(...zs)];
    const range = Math.max(xMax - xMin, yMax - yMin, zMax - zMin, 1e-9);
    const cx = (xMin + xMax) / 2, cy = (yMin + yMax) / 2, cz = (zMin + zMax) / 2;

    const normPos: THREE.Vector3[] = pts.map(p => new THREE.Vector3(
      (p.x - cx) / range,
      (p.y - cy) / range,
      (p.z - cz) / range,
    ));

    const vals = pts.map(p => (isNaN(p.value) ? 0 : p.value));
    const normVals = normalizeValues(vals);
    const allNaN = pts.every(p => isNaN(p.value));
    const getColor = (i: number): [number, number, number] => {
      const tv = allNaN ? i / (n - 1) : (normVals[i] ?? 0);
      return colormapRgb(tv, props.colormap ?? "inferno");
    };

    const brushFrom  = props.brushRange?.from ?? 0;
    const brushTo    = props.brushRange?.to   ?? 1;
    const isFullRange = brushFrom <= 0 && brushTo >= 1;
    const brushStart  = Math.round(brushFrom * (n - 1));
    const brushEnd    = Math.min(n - 1, Math.round(brushTo * (n - 1)));

    const buildLine = (indices: number[], dimFactor: number) => {
      const count = indices.length;
      const positions = new Float32Array(count * 3);
      const colors    = new Float32Array(count * 3);
      indices.forEach((idx, i) => {
        const p = normPos[idx]!;
        positions[i * 3]     = p.x;
        positions[i * 3 + 1] = p.y;
        positions[i * 3 + 2] = p.z;
        const [r, g, b] = getColor(idx);
        colors[i * 3]     = r * dimFactor;
        colors[i * 3 + 1] = g * dimFactor;
        colors[i * 3 + 2] = b * dimFactor;
      });
      const geo = new THREE.BufferGeometry();
      geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
      geo.setAttribute("color",    new THREE.BufferAttribute(colors, 3));
      return new THREE.Line(geo, new THREE.LineBasicMaterial({ vertexColors: true, linewidth: 2 }));
    };

    const allIdx = Array.from({ length: n }, (_, i) => i);
    if (!isFullRange) {
      // Dim full path; overlay bright selected segment
      scene.add(buildLine(allIdx, 0.2));
      scene.add(buildLine(allIdx.slice(brushStart, brushEnd + 1), 1));
    } else {
      scene.add(buildLine(allIdx, 1));
    }

    // Start and end markers
    const dotGeo   = new THREE.SphereGeometry(0.018, 8, 8);
    const startDot = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0x44ff44 }));
    const endDot   = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: 0xff4444 }));
    startDot.position.copy(normPos[0]!);
    endDot.position.copy(normPos[n - 1]!);
    scene.add(startDot, endDot);

    // ── KUKA Euler orientation arrows (tool Z-axis) ────────────────────────
    // KUKA extrinsic ZYX: Rz(A)·Ry(B)·Rx(C) → Three.js Euler(C, B, A, 'ZYX')
    const ARROW_STRIDE = 20;
    const ARROW_LEN    = 0.06;
    const hasEuler = pts.some(p => p.eulerA !== undefined || p.eulerB !== undefined || p.eulerC !== undefined);
    if (hasEuler) {
      for (let i = 0; i < n; i += ARROW_STRIDE) {
        const p = pts[i];
        if (!p || (p.eulerA === undefined && p.eulerB === undefined && p.eulerC === undefined)) continue;
        const euler = new THREE.Euler(
          (p.eulerC ?? 0) * (Math.PI / 180),
          (p.eulerB ?? 0) * (Math.PI / 180),
          (p.eulerA ?? 0) * (Math.PI / 180),
          "ZYX",
        );
        const dir = new THREE.Vector3(0, 0, 1).applyEuler(euler).normalize();
        scene.add(new THREE.ArrowHelper(dir, normPos[i]!.clone(), ARROW_LEN, 0xffff00,
          ARROW_LEN * 0.35, ARROW_LEN * 0.25));
      }
    }
  }

  // ── render loop ────────────────────────────────────────────────────────────
  const animate = () => {
    animId = requestAnimationFrame(animate);
    controls!.update();
    renderer!.render(scene, camera);
  };
  animate();

  // ── resize observer ────────────────────────────────────────────────────────
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
  [() => props.points, () => props.brushRange?.from, () => props.brushRange?.to],
  () => {
    cleanup?.();
    if (canvasRef.value) cleanup = buildScene(canvasRef.value);
  },
);

onUnmounted(() => {
  cleanup?.();
});
</script>

<template>
  <div class="trace3d-wrap">
    <canvas ref="canvasRef" class="trace3d-canvas" />
    <div class="trace3d-legend text-caption text-medium-emphasis">
      <span class="dot" style="background:#44ff44" /> Start
      <span class="dot ml-2" style="background:#ff4444" /> End
      <span class="ml-3">
        Color: {{ label ?? "value channel" }} ({{ colormap ?? "inferno" }})
      </span>
      <span v-if="hasOrientationArrows" class="ml-3" style="color:#ffff00">
        ▲ Tool Z-axis (KUKA A/B/C)
      </span>
    </div>
  </div>
</template>

<style scoped>
.trace3d-wrap {
  position: relative;
  width: 100%;
  height: 500px;
  background: #0d0d0d;
  border-radius: 8px;
  overflow: hidden;
}
.trace3d-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.trace3d-legend {
  position: absolute;
  bottom: 8px;
  left: 12px;
  color: #aaa;
  display: flex;
  align-items: center;
  gap: 4px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>
