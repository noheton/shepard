import type { ResponseError } from "@dlr-shepard/backend-client";
import { useEventBus, type EventBusKey } from "@vueuse/core";
import log from "loglevel";

const errorKey: EventBusKey<{ error: ErrorType; situation: string }> =
  Symbol("error-key");

const errorBus = useEventBus(errorKey);

export type ErrorType = {
  status: number;
  exception: string;
  message: string;
};

function isErrorType(error: object): error is ErrorType {
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

async function parseResponseError(error: unknown): Promise<ErrorType> {
  if (isResponseError(error)) {
    const result = await error.response.body?.getReader().read();
    if (result?.value) {
      const errorString = new TextDecoder().decode(result.value);
      let errorObject: ErrorType;
      if (isJsonString(errorString) && isErrorType(JSON.parse(errorString))) {
        errorObject = JSON.parse(errorString);
      } else {
        errorObject = {
          status: error.response.status,
          exception: error.response.statusText,
          message: "",
        };
      }
      return errorObject;
    }
  }
  return {
    status: 400,
    exception: "Invalid request",
    message: JSON.stringify(error),
  };
}

export function handleError(e: unknown, situation: string) {
  parseResponseError(e).then(parsedError => {
    log.error("Error while " + situation + ": " + JSON.stringify(parsedError));
    errorBus.emit({
      situation: situation,
      error: parsedError,
    });
  });
}

export function logError(e: unknown, situation: string) {
  parseResponseError(e).then(parsedError => {
    log.error("Error while " + situation + ": " + JSON.stringify(parsedError));
  });
}

export const onError = (
  listener: (e: { error: ErrorType; situation: string }) => void,
) => {
  errorBus.on(listener);
};
