/**
 * Shared per-batch upload progress state, consumed by both
 * `DataObjectFileUploadDialog.vue` and `FileUploadDialog.vue`.
 *
 * Task #135 — surface `bytesUploaded / bytesTotal`, percent, ETA, current
 * filename, and a Cancel button while a batch of files is being uploaded.
 *
 * Design points:
 *  - Per-file state + aggregate state.  Aggregate percent = sum(bytesUploaded)
 *    / sum(bytesTotal) over files where total is known; if any file has
 *    unknown total (`bytesTotal == null`), aggregate degrades to indeterminate.
 *  - Per-file ETA is computed from a smoothed rate (EWMA) over the file's
 *    own progress events; aggregate ETA is computed from the aggregate rate.
 *  - Cancel = call `abort()` on the shared `AbortController`; every in-flight
 *    XHR is wired to its signal and bails out.
 *  - "Stalled" detection — if no progress event arrives within 2 s of the
 *    upload starting, the file is marked `indeterminate` so the UI can switch
 *    to a spinner with elapsed time.  Subsequent real events flip it back.
 */
import { computed, reactive, ref, watch } from "vue";

export type FileUploadStatus =
  | "pending"
  | "uploading"
  | "indeterminate"
  | "done"
  | "error"
  | "cancelled";

export interface FileUploadState {
  index: number;
  name: string;
  size: number;
  bytesUploaded: number;
  /** `null` if server can't report total. */
  bytesTotal: number | null;
  percent: number;
  rateBytesPerSec: number;
  etaSeconds: number | null;
  status: FileUploadStatus;
  error: string | null;
  startedAt: number | null;
  finishedAt: number | null;
}

export interface AggregateProgress {
  totalBytes: number;
  uploadedBytes: number;
  percent: number;
  rateBytesPerSec: number;
  etaSeconds: number | null;
  determinate: boolean;
  elapsedSeconds: number;
  currentFilename: string | null;
  filesDone: number;
  filesTotal: number;
}

const EWMA_ALPHA = 0.3; // smoothing for rate
const STALL_THRESHOLD_MS = 2000;

export function useFileUploadProgress() {
  const items = ref<FileUploadState[]>([]);
  const controller = ref<AbortController | null>(null);
  const batchStartedAt = ref<number | null>(null);
  const tick = ref(0);
  let tickInterval: ReturnType<typeof setInterval> | null = null;

  const reset = () => {
    items.value = [];
    batchStartedAt.value = null;
    if (controller.value) {
      try {
        controller.value.abort();
      } catch {
        /* ignore */
      }
    }
    controller.value = new AbortController();
    if (tickInterval) {
      clearInterval(tickInterval);
      tickInterval = null;
    }
  };

  const startBatch = (files: File[]): AbortSignal => {
    reset();
    batchStartedAt.value = Date.now();
    items.value = files.map((f, i) => ({
      index: i,
      name: f.name,
      size: f.size,
      bytesUploaded: 0,
      bytesTotal: f.size > 0 ? f.size : null,
      percent: 0,
      rateBytesPerSec: 0,
      etaSeconds: null,
      status: "pending",
      error: null,
      startedAt: null,
      finishedAt: null,
    }));
    tickInterval = setInterval(() => {
      tick.value = Date.now();
      // Stall detection per file
      for (const it of items.value) {
        if (
          it.status === "uploading" &&
          it.startedAt !== null &&
          it.bytesUploaded === 0 &&
          Date.now() - it.startedAt > STALL_THRESHOLD_MS
        ) {
          it.status = "indeterminate";
        }
      }
    }, 500);
    return controller.value!.signal;
  };

  const cancel = () => {
    if (controller.value) {
      controller.value.abort();
    }
    for (const it of items.value) {
      if (
        it.status === "pending" ||
        it.status === "uploading" ||
        it.status === "indeterminate"
      ) {
        it.status = "cancelled";
      }
    }
  };

  const finishBatch = () => {
    if (tickInterval) {
      clearInterval(tickInterval);
      tickInterval = null;
    }
  };

  const reportProgress = (
    index: number,
    bytesUploaded: number,
    bytesTotal: number | null,
  ) => {
    const it = items.value[index];
    if (!it) return;
    const now = Date.now();
    if (it.startedAt === null) it.startedAt = now;
    if (it.status === "pending" || it.status === "indeterminate") {
      it.status = "uploading";
    }
    const dtMs = now - (it.startedAt ?? now);
    const overallRate = dtMs > 0 ? (bytesUploaded * 1000) / dtMs : 0;
    // EWMA smoothing
    it.rateBytesPerSec =
      it.rateBytesPerSec === 0
        ? overallRate
        : EWMA_ALPHA * overallRate + (1 - EWMA_ALPHA) * it.rateBytesPerSec;
    it.bytesUploaded = bytesUploaded;
    it.bytesTotal = bytesTotal;
    if (bytesTotal && bytesTotal > 0) {
      it.percent = Math.min(100, (bytesUploaded / bytesTotal) * 100);
      it.etaSeconds =
        it.rateBytesPerSec > 0
          ? Math.max(0, (bytesTotal - bytesUploaded) / it.rateBytesPerSec)
          : null;
    } else {
      it.percent = 0;
      it.etaSeconds = null;
    }
  };

  const markStarted = (index: number) => {
    const it = items.value[index];
    if (it && it.status === "pending") {
      it.status = "uploading";
      it.startedAt = Date.now();
    }
  };

  const markDone = (index: number) => {
    const it = items.value[index];
    if (!it) return;
    it.status = "done";
    it.finishedAt = Date.now();
    if (it.bytesTotal) {
      it.bytesUploaded = it.bytesTotal;
      it.percent = 100;
    }
    it.etaSeconds = 0;
  };

  const markError = (index: number, message: string) => {
    const it = items.value[index];
    if (!it) return;
    it.status = "error";
    it.error = message;
    it.finishedAt = Date.now();
  };

  const markCancelled = (index: number) => {
    const it = items.value[index];
    if (!it) return;
    if (it.status !== "done" && it.status !== "error") {
      it.status = "cancelled";
      it.finishedAt = Date.now();
    }
  };

  const aggregate = computed<AggregateProgress>(() => {
    void tick.value; // ensure recompute on tick
    const list = items.value;
    const totalBytes = list.reduce((s, it) => s + (it.size || 0), 0);
    const uploadedBytes = list.reduce((s, it) => s + it.bytesUploaded, 0);
    const determinate = list.every(it => it.bytesTotal !== null);
    const percent =
      determinate && totalBytes > 0
        ? Math.min(100, (uploadedBytes / totalBytes) * 100)
        : 0;
    const filesDone = list.filter(it => it.status === "done").length;
    const filesTotal = list.length;
    const current = list.find(
      it => it.status === "uploading" || it.status === "indeterminate",
    );
    const elapsedSeconds = batchStartedAt.value
      ? (Date.now() - batchStartedAt.value) / 1000
      : 0;
    const rateBytesPerSec =
      elapsedSeconds > 0 ? uploadedBytes / elapsedSeconds : 0;
    const etaSeconds =
      determinate && rateBytesPerSec > 0
        ? Math.max(0, (totalBytes - uploadedBytes) / rateBytesPerSec)
        : null;
    return {
      totalBytes,
      uploadedBytes,
      percent,
      rateBytesPerSec,
      etaSeconds,
      determinate,
      elapsedSeconds,
      currentFilename: current?.name ?? null,
      filesDone,
      filesTotal,
    };
  });

  // Clean up timer on items emptied without explicit finish (defensive).
  watch(items, list => {
    if (list.length === 0 && tickInterval) {
      clearInterval(tickInterval);
      tickInterval = null;
    }
  });

  return {
    items,
    aggregate,
    startBatch,
    cancel,
    finishBatch,
    reportProgress,
    markStarted,
    markDone,
    markError,
    markCancelled,
    controller,
  };
}

/** Convert a byte count into a short human-readable form. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return "?";
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let v = Math.abs(bytes);
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i += 1;
  }
  const fixed = v >= 100 || i === 0 ? v.toFixed(0) : v.toFixed(1);
  return `${fixed} ${units[i]}`;
}

/** Convert a duration in seconds to a short human-readable ETA. */
export function formatEta(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !isFinite(seconds))
    return "—";
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const rem = s % 60;
  if (m < 60) return `${m}m ${rem.toString().padStart(2, "0")}s`;
  const h = Math.floor(m / 60);
  const remM = m % 60;
  return `${h}h ${remM.toString().padStart(2, "0")}m`;
}
