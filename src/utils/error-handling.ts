import type { ResponseError } from "@dlr-shepard/shepard-client";
import { emitter, type ErrorType } from "./event-bus";

async function parseResponseError(error: ResponseError): Promise<ErrorType> {
  const result = await error.response.body?.getReader().read();
  if (result?.value) {
    const errorString = new TextDecoder().decode(result.value);
    const errorObject = JSON.parse(errorString);
    return errorObject as ErrorType;
  }
  return {
    status: 400,
    exception: "",
    message: "",
  };
}

export function handleError(e: ResponseError, situation: string) {
  parseResponseError(e as ResponseError).then(parsedError => {
    console.log(parsedError);
    emitter.emit("extendedError", {
      situation: situation,
      error: parsedError,
    });
  });
}
