/**
 * Task #135 — unit coverage for the shared progress composable.
 *
 * Exercises:
 *  - per-file progress tracking
 *  - aggregate progress + ETA
 *  - indeterminate fallback when bytesTotal is null
 *  - cancel() flips in-flight items to "cancelled"
 *  - markDone snaps percent to 100 even when no final progress event arrived
 *  - formatBytes / formatEta edge cases
 */
import { describe, it, expect } from "vitest";
import {
  useFileUploadProgress,
  formatBytes,
  formatEta,
} from "~/composables/container/useFileUploadProgress";

function makeFile(name: string, size: number): File {
  return new File([new Uint8Array(size)], name);
}

describe("useFileUploadProgress — per-file + aggregate", () => {
  it("startBatch initialises per-file rows in 'pending'", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 100), makeFile("b", 200)]);
    expect(p.items.value).toHaveLength(2);
    expect(p.items.value[0]?.status).toBe("pending");
    expect(p.items.value[1]?.size).toBe(200);
    expect(p.aggregate.value.totalBytes).toBe(300);
    expect(p.aggregate.value.filesTotal).toBe(2);
    p.finishBatch();
  });

  it("reportProgress moves status to 'uploading' and updates percent", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000)]);
    p.reportProgress(0, 250, 1000);
    expect(p.items.value[0]?.status).toBe("uploading");
    expect(p.items.value[0]?.percent).toBe(25);
    expect(p.items.value[0]?.bytesUploaded).toBe(250);
    p.finishBatch();
  });

  it("aggregate.percent sums bytesUploaded / totalBytes", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000), makeFile("b", 1000)]);
    p.reportProgress(0, 500, 1000);
    p.reportProgress(1, 250, 1000);
    expect(p.aggregate.value.uploadedBytes).toBe(750);
    expect(p.aggregate.value.percent).toBeCloseTo(37.5);
    expect(p.aggregate.value.determinate).toBe(true);
    p.finishBatch();
  });

  it("aggregate becomes indeterminate when any file has unknown total", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000)]);
    p.reportProgress(0, 200, null);
    expect(p.aggregate.value.determinate).toBe(false);
    expect(p.aggregate.value.percent).toBe(0);
    p.finishBatch();
  });

  it("markDone snaps an item to 100% / done", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000)]);
    p.markDone(0);
    expect(p.items.value[0]?.status).toBe("done");
    expect(p.items.value[0]?.percent).toBe(100);
    expect(p.items.value[0]?.bytesUploaded).toBe(1000);
    p.finishBatch();
  });

  it("markError records the message and status", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000)]);
    p.markError(0, "boom");
    expect(p.items.value[0]?.status).toBe("error");
    expect(p.items.value[0]?.error).toBe("boom");
    p.finishBatch();
  });
});

describe("useFileUploadProgress — cancel", () => {
  it("cancel() flips pending/uploading items to 'cancelled' and aborts the controller", () => {
    const p = useFileUploadProgress();
    const signal = p.startBatch([makeFile("a", 1000), makeFile("b", 500)]);
    p.reportProgress(0, 100, 1000);
    p.cancel();
    expect(signal.aborted).toBe(true);
    expect(p.items.value[0]?.status).toBe("cancelled");
    expect(p.items.value[1]?.status).toBe("cancelled");
    p.finishBatch();
  });

  it("cancel() leaves already-done items alone", () => {
    const p = useFileUploadProgress();
    p.startBatch([makeFile("a", 1000), makeFile("b", 500)]);
    p.markDone(0);
    p.cancel();
    expect(p.items.value[0]?.status).toBe("done");
    expect(p.items.value[1]?.status).toBe("cancelled");
    p.finishBatch();
  });
});

describe("formatBytes", () => {
  it("scales through KB / MB / GB", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1024)).toBe("1.0 KB");
    expect(formatBytes(1024 * 1024)).toBe("1.0 MB");
    expect(formatBytes(1024 * 1024 * 1024)).toBe("1.0 GB");
  });

  it("handles null / undefined", () => {
    expect(formatBytes(null)).toBe("?");
    expect(formatBytes(undefined)).toBe("?");
  });
});

describe("formatEta", () => {
  it("renders seconds, minutes, and hours", () => {
    expect(formatEta(0)).toBe("0s");
    expect(formatEta(45)).toBe("45s");
    expect(formatEta(125)).toBe("2m 05s");
    expect(formatEta(3700)).toBe("1h 01m");
  });

  it("renders em dash on null / NaN", () => {
    expect(formatEta(null)).toBe("—");
    expect(formatEta(Number.NaN)).toBe("—");
  });
});
