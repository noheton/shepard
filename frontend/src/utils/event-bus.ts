import type { EventBusKey } from "@vueuse/core";

export type ErrorType = {
  status: number;
  exception: string;
  message: string;
};

export const errorKey: EventBusKey<{ error: ErrorType; situation: string }> =
  Symbol("error-key");
