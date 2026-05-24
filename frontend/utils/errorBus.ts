import type { ResponseError } from "@dlr-shepard/backend-client";
import { useEventBus, type EventBusKey } from "@vueuse/core";
import log from "loglevel";

const errorKey: EventBusKey<{ error: ErrorType | string; situation: string }> =
  Symbol("error-key");

const errorBus = useEventBus(errorKey);

export type ErrorType = {
  status: number;
  exception: string;
  message: string;
};

export function isString(error: unknown): error is string {
  return typeof error === "string";
}

export function isErrorType(error: object): error is ErrorType {
  return "status" in error && "exception" in error && "message" in error;
}

function isJsonString(str: string) {
  try {
    JSON.parse(str);

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
  } catch (e) {
    return false;
  }
  return true;
}

function isResponseError(error: unknown): error is ResponseError {
  return error !== null && typeof error === "object" && "response" in error;
}

async function parseResponseError(error: ResponseError): Promise<ErrorType> {
  let errorObject: ErrorType = {
    status: error.response.status,
    exception: error.response.statusText,
    message: "",
  };

  const result = await error.response.body?.getReader().read();
  if (result?.value) {
    const errorString = new TextDecoder().decode(result.value).trim();
    if (isJsonString(errorString)) {
      const parsed = JSON.parse(errorString);
      if (isErrorType(parsed)) {
        errorObject = parsed;
      } else if (typeof parsed?.message === "string") {
        errorObject.message = parsed.message;
      } else if (typeof parsed?.error === "string") {
        errorObject.message = parsed.error;
      } else if (typeof parsed?.detail === "string") {
        errorObject.message = parsed.detail;
      } else if (Array.isArray(parsed?.violations) && parsed.violations.length > 0) {
        // Quarkus Bean Validation — {title, status, violations: [{field, message}]}.
        // Surface each violation; this is what previously rendered as an empty
        // toast for createFileReference and similar constraint-rejection paths.
        const lines = parsed.violations
          .filter(
            (v: unknown): v is { field?: string; message?: string } =>
              v !== null && typeof v === "object",
          )
          .map((v: { field?: string; message?: string }) => {
            const field = v.field
              ? v.field.split(".").pop() ?? v.field
              : "field";
            return `${field}: ${v.message ?? "invalid"}`;
          })
          .join("; ");
        errorObject.message =
          (typeof parsed?.title === "string" ? `${parsed.title} — ` : "") + lines;
      } else if (typeof parsed?.title === "string") {
        // RFC 9457 Problem Details fallback — title is the human summary.
        errorObject.message = parsed.title;
      }
    } else if (errorString.length > 0 && errorString.length < 2000) {
      // Plain-text error body (e.g. "repoUrl is required and must be non-blank")
      errorObject.message = errorString.replace(/^"|"$/g, "");
    }
  }
  return errorObject;
}

/**
 * UX Pattern A (2026-05-24): suppress two classes of "false-alarm" errors
 * that were lighting up the red toast on every cold-load workflow:
 *
 * 1. **AbortError / cancellation** — when a route changes during an
 *    in-flight fetch, Nuxt aborts the previous request. The original
 *    promise rejects with a DOMException("AbortError") or a TypeError
 *    "Failed to fetch" that the user has no way to act on.
 *
 * 2. **401 Unauthorized** — `useAuthRefreshMiddleware` already handles
 *    401s by silently refreshing the token (and on second 401, redirecting
 *    to sign-in). Showing a red toast in addition is duplicate noise and
 *    confuses users into thinking data is missing when it's just an auth
 *    blip during page bootstrap.
 *
 * Both are still logged to the browser console at WARN so a developer
 * debugging a real silent-401 can still see them; only the visible toast
 * is suppressed.
 */
function isAbortLike(e: unknown): boolean {
  if (e instanceof DOMException && e.name === "AbortError") return true;
  if (e instanceof Error) {
    if (e.name === "AbortError") return true;
    // Browsers throw "TypeError: Failed to fetch" when the request is
    // aborted mid-flight (route change, page unload, etc).
    if (e.name === "TypeError" && /Failed to fetch|NetworkError/i.test(e.message)) {
      return true;
    }
  }
  return false;
}

function isUnauthorized(e: unknown): boolean {
  return isResponseError(e) && e.response.status === 401;
}

export function handleError(e: unknown, situation: string) {
  if (isAbortLike(e)) {
    log.warn(`Suppressed abort while ${situation}`);
    return;
  }
  if (isUnauthorized(e)) {
    // The auth-refresh middleware already handles 401s (refresh + retry,
    // then signIn on second 401). A red toast on top of that is noise.
    log.warn(`Suppressed 401 toast while ${situation} (auth middleware handles it)`);
    return;
  }
  if (isString(e)) {
    errorBus.emit({ error: e, situation });
  } else if (isResponseError(e)) {
    parseResponseError(e).then(parsedError => {
      log.error(
        "Error while " + situation + ": " + JSON.stringify(parsedError),
      );
      errorBus.emit({
        situation: situation,
        error: parsedError,
      });
    });
  } else if (e instanceof Error) {
    // Plain Error instance — preserve the message instead of dropping
    // everything to "Unknown error". Callers that throw `new Error("...")`
    // expect their message to surface to the user.
    errorBus.emit({
      situation,
      error: e.message || e.toString() || "Unknown error",
    });
  } else {
    errorBus.emit({
      error: "Unknown error",
      situation: situation,
    });
  }
}

export const onError = (
  listener: (e: { error: ErrorType | string; situation: string }) => void,
) => {
  errorBus.on(listener);
};
