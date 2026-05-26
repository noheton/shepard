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
import { onMounted, onUnmounted, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { colormapRgb, normalizeValues, type ColormapName } from "~/utils/colormap";

interface TracePoint {
  x: number;
  y: number;
  z: number;
  value: number; // NaN when no value channel
}

const props = defineProps<{
  points: TracePoint[];
  colormap?: ColormapName;
  label?: string;
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let controls: OrbitControls | null = null;
let animId: number | null = null;

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

  // ── trace line ─────────────────────────────────────────────────────────────
  const pts = props.points;
  if (pts.length >= 2) {
    const positions = new Float32Array(pts.length * 3);
    const colors = new Float32Array(pts.length * 3);

    // Normalize positions to unit cube
    const xs = pts.map(p => p.x), ys = pts.map(p => p.y), zs = pts.map(p => p.z);
    const [xMin, xMax] = [Math.min(...xs), Math.max(...xs)];
    const [yMin, yMax] = [Math.min(...ys), Math.max(...ys)];
    const [zMin, zMax] = [Math.min(...zs), Math.max(...zs)];
    const range = Math.max(xMax - xMin, yMax - yMin, zMax - zMin, 1e-9);
    const cx = (xMin + xMax) / 2, cy = (yMin + yMax) / 2, cz = (zMin + zMax) / 2;

    // Normalized color values
    const vals = pts.map(p => (isNaN(p.value) ? 0 : p.value));
    const normVals = normalizeValues(vals);
    // If all values are NaN, fall back to time gradient
    const allNaN = pts.every(p => isNaN(p.value));

    pts.forEach((p, i) => {
      positions[i * 3]     = (p.x - cx) / range;
      positions[i * 3 + 1] = (p.y - cy) / range;
      positions[i * 3 + 2] = (p.z - cz) / range;

      const t = allNaN ? i / (pts.length - 1) : normVals[i];
      const [r, g, b] = colormapRgb(t, props.colormap ?? "inferno");
      colors[i * 3]     = r;
      colors[i * 3 + 1] = g;
      colors[i * 3 + 2] = b;
    });

    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    geo.setAttribute("color",    new THREE.BufferAttribute(colors, 3));
    const mat = new THREE.LineBasicMaterial({ vertexColors: true, linewidth: 2 });
    scene.add(new THREE.Line(geo, mat));

    // Start and end markers
    const dotGeo = new THREE.SphereGeometry(0.018, 8, 8);
    const startMat = new THREE.MeshBasicMaterial({ color: 0x44ff44 });
    const endMat   = new THREE.MeshBasicMaterial({ color: 0xff4444 });
    const startDot = new THREE.Mesh(dotGeo, startMat);
    const endDot   = new THREE.Mesh(dotGeo, endMat);
    startDot.position.set(positions[0], positions[1], positions[2]);
    const last = (pts.length - 1) * 3;
    endDot.position.set(positions[last], positions[last + 1], positions[last + 2]);
    scene.add(startDot, endDot);
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

watch(() => props.points, () => {
  cleanup?.();
  if (canvasRef.value) cleanup = buildScene(canvasRef.value);
});

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
