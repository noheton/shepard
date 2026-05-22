/**
 * XHR-based file upload helpers with progress + cancel support.
 *
 * Why XHR and not fetch:
 * `fetch()` in shipping browsers cannot observe request-body upload progress —
 * there is no `ReadableStream` for the request body that survives CORS, and
 * the proposed `ProgressEvent` hook on a `fetch` Request body never landed.
 * `XMLHttpRequest.upload.onprogress` is the only portable signal available,
 * so both the legacy multipart and the presigned-PUT paths use XHR here.
 *
 * Task #135 — wires `bytesUploaded`, `bytesTotal`, ETA and Cancel through the
 * UI. The dialog binds to the progress callback; cancel propagates via an
 * `AbortSignal` (`AbortController.abort()`).
 *
 * Notes on robustness:
 *  - `xhr.upload.onprogress` may legitimately never fire (transparent proxies
 *    that buffer the whole request body before forwarding strip progress).
 *    Callers are expected to render an indeterminate spinner + elapsed-time
 *    fallback if no progress event arrives within ~2 s.
 *  - If `event.lengthComputable === false`, `bytesTotal` is reported as
 *    `null`; callers fall back to the indeterminate UI.
 *  - The `xhr` constructor is injectable for unit-testing without happy-dom.
 */

export interface UploadProgressEvent {
  bytesUploaded: number;
  /** `null` when the server / browser cannot report total size. */
  bytesTotal: number | null;
}

export interface XhrUploadOptions {
  /** Fires repeatedly as upload bytes are flushed. */
  onProgress?: (ev: UploadProgressEvent) => void;
  /** Abort signal — `abort()` calls `XMLHttpRequest.abort()`. */
  signal?: AbortSignal;
  /** Test seam — defaults to `new XMLHttpRequest()`. */
  xhrFactory?: () => XMLHttpRequest;
}

export class UploadAbortError extends Error {
  constructor() {
    super("Upload aborted by user");
    this.name = "UploadAbortError";
  }
}

export class UploadHttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly responseText: string,
  ) {
    super(`HTTP ${status}: ${responseText.slice(0, 200)}`);
    this.name = "UploadHttpError";
  }
}

interface XhrRequestOptions extends XhrUploadOptions {
  method: "POST" | "PUT";
  url: string;
  body: FormData | Blob | File;
  headers?: Record<string, string>;
  /** When set, parse a JSON body on success and resolve to it. */
  responseType?: "json" | "void";
}

function runXhr<T>(opts: XhrRequestOptions): Promise<T> {
  const xhr = (opts.xhrFactory ?? (() => new XMLHttpRequest()))();
  return new Promise<T>((resolve, reject) => {
    let aborted = false;

    const onAbort = () => {
      aborted = true;
      try {
        xhr.abort();
      } catch {
        /* ignore */
      }
      reject(new UploadAbortError());
    };

    if (opts.signal) {
      if (opts.signal.aborted) {
        onAbort();
        return;
      }
      opts.signal.addEventListener("abort", onAbort, { once: true });
    }

    xhr.open(opts.method, opts.url, true);

    if (opts.headers) {
      for (const [k, v] of Object.entries(opts.headers)) {
        xhr.setRequestHeader(k, v);
      }
    }

    if (opts.onProgress) {
      xhr.upload.onprogress = (ev: ProgressEvent) => {
        opts.onProgress!({
          bytesUploaded: ev.loaded,
          bytesTotal: ev.lengthComputable ? ev.total : null,
        });
      };
    }

    xhr.onload = () => {
      if (aborted) return;
      if (opts.signal) opts.signal.removeEventListener("abort", onAbort);
      if (xhr.status >= 200 && xhr.status < 300) {
        if (opts.responseType === "json") {
          try {
            const parsed = xhr.responseText
              ? (JSON.parse(xhr.responseText) as T)
              : (undefined as unknown as T);
            resolve(parsed);
          } catch (e) {
            reject(
              new Error(
                `Failed to parse JSON response: ${(e as Error).message}`,
              ),
            );
          }
        } else {
          resolve(undefined as unknown as T);
        }
      } else {
        reject(new UploadHttpError(xhr.status, xhr.responseText ?? ""));
      }
    };
    xhr.onerror = () => {
      if (aborted) return;
      if (opts.signal) opts.signal.removeEventListener("abort", onAbort);
      reject(new Error("Network error during upload"));
    };
    xhr.ontimeout = () => {
      if (aborted) return;
      if (opts.signal) opts.signal.removeEventListener("abort", onAbort);
      reject(new Error("Upload timed out"));
    };

    xhr.send(opts.body);
  });
}

/**
 * Legacy multipart upload — mirrors `FileContainerApi.createFileRaw`:
 *   POST <basePath>/fileContainers/{id}/payload
 *   Authorization: Bearer <token>
 *   FormData with single `file` field; browser sets Content-Type with boundary.
 */
export async function xhrUploadMultipart<T = unknown>(args: {
  url: string;
  fieldName: string;
  file: File;
  authorization: string;
  options?: XhrUploadOptions;
}): Promise<T> {
  const form = new FormData();
  form.append(args.fieldName, args.file);
  return runXhr<T>({
    method: "POST",
    url: args.url,
    body: form,
    headers: { Authorization: args.authorization },
    responseType: "json",
    ...args.options,
  });
}

/**
 * Presigned PUT — raw file bytes, no auth header (signature in URL).
 */
export async function xhrUploadPresignedPut(args: {
  url: string;
  file: File;
  contentType: string;
  options?: XhrUploadOptions;
}): Promise<void> {
  return runXhr<void>({
    method: "PUT",
    url: args.url,
    body: args.file,
    headers: { "Content-Type": args.contentType },
    responseType: "void",
    ...args.options,
  });
}
