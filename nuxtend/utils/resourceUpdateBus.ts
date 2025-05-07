import { useEventBus } from "@vueuse/core";

const collectionBus = useEventBus("collection-updated");

export const handleCollectionUpdate = () => {
  collectionBus.emit("collection-updated");
};

export const onCollectionUpdated = (listener: () => void) => {
  const stopListening = collectionBus.on(listener);
  onUnmounted(stopListening);
};

const shepardObjectBus = useEventBus("shepard-object-updated");

export const handleShepardObjectUpdate = () => {
  shepardObjectBus.emit("shepard-object-updated");
};

export const onShepardObjectUpdated = (listener: () => void) => {
  const stopListening = shepardObjectBus.on(listener);
  onUnmounted(stopListening);
};

const containerBus = useEventBus("container-updated");

export const handleContainerUpdate = () => {
  containerBus.emit("container-updated");
};

export const onContainerUpdated = (listener: () => void) => {
  const stopListening = containerBus.on(listener);
  onUnmounted(stopListening);
};
const dataObjectBus = useEventBus("data-object-updated");

export const handleDataObjectUpdate = () => {
  dataObjectBus.emit("data-object-updated");
};

export const onDataObjectUpdated = (listener: () => void) => {
  const stopListening = dataObjectBus.on(listener);
  onUnmounted(stopListening);
};

const annotationBus = useEventBus("annotations-changed");

export const handleAnnotationListUpdate = () => {
  annotationBus.emit("annotations-changed");
};

export const onAnnotationsUpdated = (listener: () => void) => {
  const stopListening = annotationBus.on(listener);
  onUnmounted(stopListening);
};

const semanticRepositoryBus = useEventBus("semantic-repositories-changed");

export const handleSemanticRepositoryListUpdate = () => {
  semanticRepositoryBus.emit("semantic-repositories-changed");
};

export const onSemanticRepositoriesUpdated = (listener: () => void) => {
  const stopListening = semanticRepositoryBus.on(listener);
  onUnmounted(stopListening);
};
