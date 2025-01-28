import { useEventBus, type EventBusKey } from "@vueuse/core";

const successKey: EventBusKey<{ message: string }> = Symbol("success-key");

const successBus = useEventBus(successKey);

export const emitSuccess = (message: string) => {
  successBus.emit({ message });
};

export const onSuccess = (listener: (e: { message: string }) => void) => {
  successBus.on(listener);
};
