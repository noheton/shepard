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
      }
    } else if (errorString.length > 0 && errorString.length < 2000) {
      // Plain-text error body (e.g. "repoUrl is required and must be non-blank")
      errorObject.message = errorString.replace(/^"|"$/g, "");
    }
  }
  return errorObject;
}

export function handleError(e: unknown, situation: string) {
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
