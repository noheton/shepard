/**
 * Task #135 — unit coverage for the XHR-based upload helper.
 *
 * Why a custom mock instead of jsdom/happy-dom: the vitest config runs in
 * `node` environment to keep the suite fast.  We inject a minimal XHR mock
 * through the `xhrFactory` seam in `runXhr`, which exercises every code path
 * (open / setRequestHeader / send / onload / progress / abort) without
 * dragging in a DOM shim.
 */
import { describe, it, expect, vi } from "vitest";
import {
  xhrUploadMultipart,
  xhrUploadPresignedPut,
  UploadAbortError,
  UploadHttpError,
} from "~/composables/container/xhrUpload";

interface FakeXhrSpec {
  /** Provide a list of progress events to fire before `onload`. */
  progressEvents?: { loaded: number; total: number; lengthComputable: boolean }[];
  /** Status to set on onload. */
  status?: number;
  responseText?: string;
  /** If true, fire `onerror` instead of `onload`. */
  fireError?: boolean;
  /** If true, never fire anything (test stalls). */
  hang?: boolean;
}

interface FakeXhrCapture {
  method?: string;
  url?: string;
  headers: Record<string, string>;
  body?: unknown;
  aborted: boolean;
}

class FakeXhr {
  upload: { onprogress: ((e: ProgressEvent) => void) | null } = {
    onprogress: null,
  };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  ontimeout: (() => void) | null = null;
  status = 0;
  responseText = "";
  // captured state
  private spec: FakeXhrSpec;
  private capture: FakeXhrCapture;

  constructor(spec: FakeXhrSpec, capture: FakeXhrCapture) {
    this.spec = spec;
    this.capture = capture;
  }

  open(method: string, url: string) {
    this.capture.method = method;
    this.capture.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.capture.headers[name] = value;
  }

  send(body: unknown) {
    this.capture.body = body;
    if (this.spec.hang) return;
    // Fire progress events synchronously, then onload/onerror, in microtasks.
    queueMicrotask(() => {
      for (const ev of this.spec.progressEvents ?? []) {
        if (this.capture.aborted) return;
        this.upload.onprogress?.({
          loaded: ev.loaded,
          total: ev.total,
          lengthComputable: ev.lengthComputable,
        } as unknown as ProgressEvent);
      }
      if (this.capture.aborted) return;
      if (this.spec.fireError) {
        this.onerror?.();
      } else {
        this.status = this.spec.status ?? 200;
        this.responseText = this.spec.responseText ?? "";
        this.onload?.();
      }
    });
  }

  abort() {
    this.capture.aborted = true;
    // Real XHRs fire `onabort`; our promise resolves via signal handler instead.
  }
}

function makeFactory(spec: FakeXhrSpec) {
  const capture: FakeXhrCapture = {
    headers: {},
    aborted: false,
  };
  const factory = () => new FakeXhr(spec, capture) as unknown as XMLHttpRequest;
  return { capture, factory };
}

function fakeFile(name: string, size: number): File {
  return new File([new Uint8Array(size)], name, { type: "application/octet-stream" });
}

describe("xhrUpload — multipart legacy path", () => {
  it("sends Authorization, file form-field, and a POST", async () => {
    const { capture, factory } = makeFactory({
      status: 200,
      responseText: '{"oid":"abc","filename":"x.bin"}',
    });
    const file = fakeFile("x.bin", 100);
    const result = await xhrUploadMultipart<{ oid: string; filename: string }>({
      url: "http://api.test/fileContainers/42/payload",
      fieldName: "file",
      file,
      authorization: "Bearer test-token",
      options: { xhrFactory: factory },
    });
    expect(capture.method).toBe("POST");
    expect(capture.url).toBe("http://api.test/fileContainers/42/payload");
    expect(capture.headers.Authorization).toBe("Bearer test-token");
    // Don't manually set Content-Type — the browser sets it on FormData.
    expect(capture.headers["Content-Type"]).toBeUndefined();
    expect(result.oid).toBe("abc");
    expect(result.filename).toBe("x.bin");
  });

  it("reports progress events back through onProgress", async () => {
    const { factory } = makeFactory({
      status: 200,
      responseText: "{}",
      progressEvents: [
        { loaded: 100, total: 1000, lengthComputable: true },
        { loaded: 500, total: 1000, lengthComputable: true },
        { loaded: 1000, total: 1000, lengthComputable: true },
      ],
    });
    const seen: { bytesUploaded: number; bytesTotal: number | null }[] = [];
    await xhrUploadMultipart({
      url: "http://api.test/fileContainers/42/payload",
      fieldName: "file",
      file: fakeFile("x.bin", 1000),
      authorization: "Bearer t",
      options: {
        xhrFactory: factory,
        onProgress: ev => seen.push(ev),
      },
    });
    expect(seen).toEqual([
      { bytesUploaded: 100, bytesTotal: 1000 },
      { bytesUploaded: 500, bytesTotal: 1000 },
      { bytesUploaded: 1000, bytesTotal: 1000 },
    ]);
  });

  it("reports null bytesTotal when length not computable", async () => {
    const { factory } = makeFactory({
      status: 200,
      responseText: "{}",
      progressEvents: [{ loaded: 50, total: 0, lengthComputable: false }],
    });
    const seen: { bytesUploaded: number; bytesTotal: number | null }[] = [];
    await xhrUploadMultipart({
      url: "http://api.test/x",
      fieldName: "file",
      file: fakeFile("a", 10),
      authorization: "Bearer t",
      options: { xhrFactory: factory, onProgress: ev => seen.push(ev) },
    });
    expect(seen[0]).toEqual({ bytesUploaded: 50, bytesTotal: null });
  });

  it("rejects with UploadHttpError on non-2xx status", async () => {
    const { factory } = makeFactory({ status: 500, responseText: "boom" });
    await expect(
      xhrUploadMultipart({
        url: "http://api.test/x",
        fieldName: "file",
        file: fakeFile("a", 1),
        authorization: "Bearer t",
        options: { xhrFactory: factory },
      }),
    ).rejects.toBeInstanceOf(UploadHttpError);
  });

  it("rejects with UploadAbortError when signal aborts mid-upload", async () => {
    const { capture, factory } = makeFactory({ hang: true });
    const ctrl = new AbortController();
    const p = xhrUploadMultipart({
      url: "http://api.test/x",
      fieldName: "file",
      file: fakeFile("a", 1),
      authorization: "Bearer t",
      options: { xhrFactory: factory, signal: ctrl.signal },
    });
    ctrl.abort();
    await expect(p).rejects.toBeInstanceOf(UploadAbortError);
    expect(capture.aborted).toBe(true);
  });

  it("rejects immediately if signal is already aborted at start", async () => {
    const { factory } = makeFactory({});
    const ctrl = new AbortController();
    ctrl.abort();
    await expect(
      xhrUploadMultipart({
        url: "http://api.test/x",
        fieldName: "file",
        file: fakeFile("a", 1),
        authorization: "Bearer t",
        options: { xhrFactory: factory, signal: ctrl.signal },
      }),
    ).rejects.toBeInstanceOf(UploadAbortError);
  });

  it("rejects with network error when xhr.onerror fires", async () => {
    const { factory } = makeFactory({ fireError: true });
    await expect(
      xhrUploadMultipart({
        url: "http://api.test/x",
        fieldName: "file",
        file: fakeFile("a", 1),
        authorization: "Bearer t",
        options: { xhrFactory: factory },
      }),
    ).rejects.toThrow(/network/i);
  });
});

describe("xhrUpload — presigned PUT path", () => {
  it("sends a PUT with provided Content-Type and no Authorization header", async () => {
    const { capture, factory } = makeFactory({ status: 200, responseText: "" });
    await xhrUploadPresignedPut({
      url: "http://s3.test/signed?sig=abc",
      file: fakeFile("x.bin", 50),
      contentType: "image/png",
      options: { xhrFactory: factory },
    });
    expect(capture.method).toBe("PUT");
    expect(capture.url).toBe("http://s3.test/signed?sig=abc");
    expect(capture.headers["Content-Type"]).toBe("image/png");
    expect(capture.headers.Authorization).toBeUndefined();
  });

  it("forwards onProgress on the presigned path too", async () => {
    const { factory } = makeFactory({
      status: 200,
      responseText: "",
      progressEvents: [{ loaded: 25, total: 50, lengthComputable: true }],
    });
    const seen: { bytesUploaded: number; bytesTotal: number | null }[] = [];
    await xhrUploadPresignedPut({
      url: "http://s3.test/x",
      file: fakeFile("a", 50),
      contentType: "application/octet-stream",
      options: { xhrFactory: factory, onProgress: ev => seen.push(ev) },
    });
    expect(seen).toEqual([{ bytesUploaded: 25, bytesTotal: 50 }]);
  });
});

describe("xhrUpload — error class metadata", () => {
  it("UploadHttpError captures status + truncated response body", async () => {
    const { factory } = makeFactory({
      status: 413,
      responseText: "x".repeat(500),
    });
    const err = await xhrUploadMultipart({
      url: "http://api.test/x",
      fieldName: "file",
      file: fakeFile("a", 1),
      authorization: "Bearer t",
      options: { xhrFactory: factory },
    }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(UploadHttpError);
    expect((err as UploadHttpError).status).toBe(413);
    expect((err as UploadHttpError).responseText.length).toBe(500);
  });
});
