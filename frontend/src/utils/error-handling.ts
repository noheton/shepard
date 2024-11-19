import type { ResponseError } from "@dlr-shepard/backend-client";
import { useEventBus } from "@vueuse/core";
import log from "loglevel";
import { errorKey, type ErrorType } from "./event-bus";

const bus = useEventBus(errorKey);

function isErrorType(error: object): error is ErrorType {
  return "status" in error && "exception" in error && "message" in error;
}

function isJsonString(str: string) {
  try {
    JSON.parse(str);
  } catch (e) {
    return false;
  }
  return true;
}

async function parseResponseError(error: ResponseError): Promise<ErrorType> {
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
  return {
    status: 400,
    exception: "",
    message: "",
  };
}

export function handleError(e: ResponseError, situation: string) {
  parseResponseError(e as ResponseError).then(parsedError => {
    log.error("Error while " + situation + ": " + JSON.stringify(parsedError));
    bus.emit({
      situation: situation,
      error: parsedError,
    });
  });
}

export function logError(e: ResponseError, situation: string) {
  parseResponseError(e as ResponseError).then(parsedError => {
    log.error("Error while " + situation + ": " + JSON.stringify(parsedError));
  });
}
