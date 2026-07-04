<script setup lang="ts">
/**
 * UrdfCanvas — Three.js client-only URDF renderer for VIEW_RECIPE outputs.
 *
 * Loads a URDF document via `urdf-loader` (gkjohnson, MIT), mounts the
 * resulting URDFRobot Object3D into a Three.js scene with OrbitControls,
 * a small triad, and ground-plane lighting. Exposes `captureDataUrl()`
 * for screenshot capture (mirrors Trace3DCanvas).
 *
 * Must be wrapped in <ClientOnly> by the parent (WebGL is SSR-incompatible).
 *
 * Reactivity contract:
 *   - props.urdfUrl change       → full scene rebuild (new robot).
 *   - props.jointValues change   → per-joint setJointValue() on the existing
 *                                  robot. The scene is NOT rebuilt; this is
 *                                  what makes 30+ FPS animation possible.
 *
 * URDF-WEBVIEW-1 (aidocs/16). Design: aidocs/integrations/113-urdf-viewer.md.
 */
import { ref, onMounted, onUnmounted, watch } from "vue";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
// urdf-loader is a Three.js Loader; named import URDFRobot for the result type.
 
import URDFLoader from "urdf-loader";

interface URDFJointLike {
  name: string;
  jointType: string;
  limit?: { lower?: number; upper?: number };
  setJointValue: (value: number) => boolean;
}
interface URDFRobotLike extends THREE.Object3D {
  joints: Record<string, URDFJointLike>;
  setJointValue: (name: string, value: number) => boolean;
}

const props = withDefaults(
  defineProps<{
    /** URL of the .urdf file. Signed Garage URL or static asset path. */
    urdfUrl: string;
    /** Root for mesh resolution. URDF `package://foo/bar.stl` becomes `${packagePath}/foo/bar.stl`. */
    packagePath?: string;
    /** Optional override for custom mesh URL resolution. */
    meshUrlResolver?: (url: string) => string;
    /** Current joint angles, name → radians. */
    jointValues?: Record<string, number>;
    /** Debug — show a small RGB triad at the base. */
    showAxes?: boolean;
    /** Canvas background color (CSS color string). */
    backgroundColor?: string;
  }>(),
  {
    packagePath: "",
    meshUrlResolver: undefined,
    jointValues: () => ({}),
    showAxes: true,
    backgroundColor: "#0d0d0d",
  },
);

const emit = defineEmits<{
  /** Fired once the URDFRobot has loaded; carries the joint list for parents. */
  "robot-loaded": [robot: URDFRobotLike];
  /** Fired on load failure. */
  "load-error": [err: Error];
}>();

const canvasRef = ref<HTMLCanvasElement | null>(null);
const isLoading = ref(true);
const loadError = ref<string | null>(null);

let renderer: THREE.WebGLRenderer | null = null;
let scene:    THREE.Scene | null = null;
let camera:   THREE.PerspectiveCamera | null = null;
let controls: OrbitControls | null = null;
let animId:   number | null = null;
let robot:    URDFRobotLike | null = null;
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
  camera.position.set(1.2, 1.0, 1.6);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.target.set(0, 0.25, 0);

  // Lighting — ambient + a single directional so STL/Collada visuals look correct.
  scene.add(new THREE.AmbientLight(0xffffff, 0.6));
  const dir = new THREE.DirectionalLight(0xffffff, 0.7);
  dir.position.set(2, 4, 3);
  scene.add(dir);

  // Optional debug triad — base coordinate system.
  if (props.showAxes) {
    const axisLen = 0.25;
    const axisGeo = (vec: THREE.Vector3, color: number) => {
      const g = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, 0), vec.clone().multiplyScalar(axisLen)]);
      return new THREE.Line(g, new THREE.LineBasicMaterial({ color }));
    };
    scene.add(axisGeo(new THREE.Vector3(1, 0, 0), 0xff4444));
    scene.add(axisGeo(new THREE.Vector3(0, 1, 0), 0x44ff44));
    scene.add(axisGeo(new THREE.Vector3(0, 0, 1), 0x4444ff));
  }

  captureDataUrlFn = () => {
    if (!renderer || !canvasRef.value || !scene || !camera) return "";
    renderer.render(scene, camera);
    return canvasRef.value.toDataURL("image/png");
  };

  // Render loop — runs forever; joint mutations re-render automatically via the
  // controls.update() tick. Three.js does not auto-rebuild on Object3D mutation,
  // but the loop pulls the current transform each frame so setJointValue suffices.
  const animate = () => {
    animId = requestAnimationFrame(animate);
    if (controls) controls.update();
    if (renderer && scene && camera) renderer.render(scene, camera);
  };
  animate();

  // Resize observer
  const ro = new ResizeObserver(() => {
    if (!canvas || !camera || !renderer) return;
    const cw = canvas.clientWidth, ch = canvas.clientHeight;
    if (cw === 0 || ch === 0) return;
    camera.aspect = cw / ch;
    camera.updateProjectionMatrix();
    renderer.setSize(cw, ch, false);
  });
  ro.observe(canvas);

  // Kick off URDF load.
  void loadUrdf();

  return () => {
    ro.disconnect();
    if (animId !== null) cancelAnimationFrame(animId);
    controls?.dispose();
    renderer?.dispose();
    renderer = null;
    scene = null;
    camera = null;
    controls = null;
    robot = null;
  };
}

async function loadUrdf() {
  if (!scene) return;
  isLoading.value = true;
  loadError.value = null;

  // Drop any previously-mounted robot before loading a new one.
  if (robot) {
    scene.remove(robot);
    robot = null;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const Loader = URDFLoader as any;
    const loader = new Loader();
    if (props.packagePath) loader.packages = props.packagePath;
    if (props.meshUrlResolver) {
      // urdf-loader's hook: `loadMeshCb` — but the simpler `urlModifierFunc`
      // works for the resolver case. Cast to any: typings are loose.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (loader as any).manager = new THREE.LoadingManager();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (loader as any).manager.setURLModifier(props.meshUrlResolver);
    }

    const loaded = await new Promise<URDFRobotLike>((resolve, reject) => {
      // urdf-loader's load() takes (url, onLoad, onProgress, onError) per Three.js convention.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      loader.load(props.urdfUrl, (r: any) => resolve(r as URDFRobotLike), undefined, (e: unknown) => reject(e instanceof Error ? e : new Error(String(e))));
    });

    // URDFs are conventionally Z-up. Three.js cameras are Y-up. Rotate the
    // robot so its Z becomes scene-Y for a more natural default view.
    loaded.rotation.x = -Math.PI / 2;

    robot = loaded;
    if (scene) scene.add(robot);

    // Apply any initial jointValues that arrived before the load completed.
    applyJointValues(props.jointValues);

    // URDF-FIT-VIEW: frame the loaded robot. urdf-loader resolves with the
    // kinematic tree before its STL meshes finish loading asynchronously
    // through THREE.LoadingManager, so the bbox is empty/tiny at this
    // moment. Re-fit on each animation frame while the bbox is still
    // growing (max 60 frames ≈ 1 s of headroom for meshes to arrive). After
    // the bbox stops growing for 3 consecutive frames we stop re-fitting,
    // leaving the user in full control via OrbitControls.
    let stableFrames = 0;
    let lastDiameter = 0;
    let framesFitted = 0;
    const refit = () => {
      if (!robot || framesFitted > 60) return;
      framesFitted += 1;
      robot.updateMatrixWorld(true);
      const box = new THREE.Box3().setFromObject(robot);
      if (box.isEmpty()) {
        requestAnimationFrame(refit);
        return;
      }
      const size = box.getSize(new THREE.Vector3());
      const d = Math.max(size.x, size.y, size.z);
      if (d > lastDiameter + 1e-4) {
        lastDiameter = d;
        stableFrames = 0;
        fitCameraToObject(robot);
      } else {
        stableFrames += 1;
        if (stableFrames < 3) {
          requestAnimationFrame(refit);
          return;
        }
        return;
      }
      requestAnimationFrame(refit);
    };
    requestAnimationFrame(refit);

    isLoading.value = false;
    emit("robot-loaded", robot);
  } catch (e) {
    const err = e instanceof Error ? e : new Error(String(e));
    loadError.value = err.message;
    isLoading.value = false;
    emit("load-error", err);
  }
}

function fitCameraToObject(obj: THREE.Object3D, margin: number = 1.4): void {
  if (!camera || !controls) return;
  // Bounding box in world space. Robust to the URDF's Z-up→Y-up rotation we
  // just applied because Box3.setFromObject walks the world matrices.
  obj.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(obj);
  if (box.isEmpty()) return;
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxDim = Math.max(size.x, size.y, size.z);
  if (!isFinite(maxDim) || maxDim <= 0) return;

  const fovRad = (camera.fov * Math.PI) / 180;
  const distance = (maxDim / 2) / Math.tan(fovRad / 2) * margin;

  // Keep the original viewing angle (front-right-above) — just scale the
  // distance and re-anchor on the object center.
  const dir = new THREE.Vector3(1.2, 1.0, 1.6).normalize();
  camera.position.copy(center).addScaledVector(dir, distance);
  camera.near = Math.max(distance / 100, 0.001);
  camera.far = distance * 10;
  camera.updateProjectionMatrix();
  controls.target.copy(center);
  controls.update();
}

function applyJointValues(values: Record<string, number> | undefined) {
  if (!robot || !values) return;
  for (const [name, val] of Object.entries(values)) {
    if (typeof val !== "number" || !isFinite(val)) continue;
    try {
      robot.setJointValue(name, val);
    } catch {
      // unknown joint — ignore. Caller may pass a superset.
    }
  }
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

// Reload the robot when the source URL or packagePath changes — full rebuild.
watch(
  () => [props.urdfUrl, props.packagePath],
  () => { void loadUrdf(); },
);

// Cheap path: per-frame joint reactivity. Do NOT rebuild the scene; just
// poke the existing robot's setJointValue. This is what makes 30+ FPS
// animation possible — UrdfAnimator updates jointValues every rAF tick.
watch(
  () => props.jointValues,
  (vals) => { applyJointValues(vals); },
  { deep: true },
);
</script>

<template>
  <div class="urdf-wrap" :style="{ background: backgroundColor }">
    <canvas ref="canvasRef" class="urdf-canvas" />

    <div v-if="isLoading" class="urdf-overlay">
      <v-progress-circular indeterminate color="primary" />
      <span class="text-caption text-medium-emphasis ml-3">Loading URDF…</span>
    </div>

    <v-alert
      v-if="loadError"
      type="error"
      variant="tonal"
      density="compact"
      class="urdf-error"
    >
      Failed to load URDF: {{ loadError }}
    </v-alert>
  </div>
</template>

<style scoped>
.urdf-wrap {
  position: relative;
  width: 100%;
  height: 500px;
  border-radius: 8px;
  overflow: hidden;
}
.urdf-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.urdf-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(13, 13, 13, 0.4);
  pointer-events: none;
}
.urdf-error {
  position: absolute;
  bottom: 8px;
  left: 8px;
  right: 8px;
}
</style>
