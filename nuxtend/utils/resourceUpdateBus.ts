import { useEventBus } from "@vueuse/core";

const collectionBus = useEventBus("collection-updated");

export const handleCollectionUpdate = () => {
  collectionBus.emit("collection-updated");
};

export const onCollectionUpdated = (listener: () => void) => {
  collectionBus.on(listener);
};

const dataObjectBus = useEventBus("data-object-updated");

export const handleDataObjectUpdate = () => {
  dataObjectBus.emit("data-object-updated");
};

export const onDataObjectUpdated = (listener: () => void) => {
  dataObjectBus.on(listener);
};
