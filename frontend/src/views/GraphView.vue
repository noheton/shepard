<script setup lang="ts">
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import ReferenceService from "@/services/referenceService";
import { handleError } from "@/utils/error-handling";
import type {
  BasicReference,
  Collection,
  DataObject,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { DataSet } from "vis-data";
import { Network, type Edge, type IdType, type Node } from "vis-network";
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue2-helpers/vue-router";

const route = useRoute();

const currentCollectionId = computed<string>(() => route.params.collectionId);
const loading = ref(false);

const dataNodeColor = "#7fbadd"; // 5  shades lighter than $primary
const refNodeColor = "#93d3a2"; // 5  shades lighter than $success
const parentChildColor = "#b02a37"; // 2  shades darker than $danger
const referenceColor = "#208537"; // 2 shades darker than $success
const preSucColor = "#005d95"; // 2 shades darker than $primary
const graphOptions = {
  nodes: {
    shape: "box",
    shapeProperties: {
      interpolation: false,
    },
  },
  edges: {
    smooth: false,
    arrows: {
      to: {
        enabled: true,
      },
    },
  },
  physics: {
    barnesHut: {
      gravitationalConstant: -5000,
      centralGravity: 0.5,
      springConstant: 0.3,
      damping: 0.5,
    },
    minVelocity: 0.5,
    timestep: 0.3,
  },
  layout: {
    improvedLayout: false,
  },
  interaction: {
    selectable: true,
  },
};

function getConnectedEdges(nodeId: number) {
  const ret = new Array<Edge>();
  const edgeIds = network.getConnectedEdges(nodeId);
  edgeIds.forEach(id => {
    const edge = edges.get(id);
    if (edge != null) ret.push(edge);
  });
  return ret;
}

function findChildEdge(edges: Edge[], from: number, to: number) {
  return edges.find(
    edge =>
      edge?.from == from && edge?.to == to && edge.color == parentChildColor,
  );
}

function findReferenceEdge(edges: Edge[], from: number, to: number) {
  return edges.find(
    edge =>
      edge?.from == from && edge?.to == to && edge.color == referenceColor,
  );
}

const selectedNode = ref<{
  id: number;
  node: Node;
  dataObject?: DataObject;
  reference?: BasicReference;
}>();
const canExpandDataObjects = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject?.childrenIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const childrenIds = selectedNode.value.dataObject.childrenIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return childrenIds.some(
    childId => findChildEdge(connectedEdges, selectedId, childId) == undefined,
  );
});

const canCollapseDataObjects = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject?.childrenIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const childrenIds = selectedNode.value.dataObject.childrenIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return (
    childrenIds.length > 0 &&
    childrenIds.every(
      childId =>
        findChildEdge(connectedEdges, selectedId, childId) != undefined,
    )
  );
});

const canExpandReferences = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject?.referenceIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const referenceIds = selectedNode.value.dataObject.referenceIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return referenceIds.some(
    referenceId =>
      findReferenceEdge(connectedEdges, selectedId, referenceId) == undefined,
  );
});

const canCollapseReferences = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject?.referenceIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const referenceIds = selectedNode.value.dataObject.referenceIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return (
    referenceIds.length > 0 &&
    referenceIds.every(
      childId =>
        findReferenceEdge(connectedEdges, selectedId, childId) != undefined,
    )
  );
});

const nodes = new DataSet<Node, "id">([]);
const edges = new DataSet<Edge, "id">([]);
let network: Network;
function initGraph() {
  const container = document.getElementById("graph-container") as HTMLElement;
  network = new Network(
    container,
    { nodes: nodes, edges: edges },
    graphOptions,
  );
  network.on("select", params => {
    if (params.nodes.length > 0) {
      setPhysics(+params.nodes[0], false);
      updateSelectedNode(+params.nodes[0]);
    }
  });
  network.on("deselectNode", () => {
    selectedNode.value = undefined;
  });
  network.on("dragEnd", params => {
    if (params.nodes.length > 0) {
      setPhysics(+params.nodes[0], false);
      updateSelectedNode(+params.nodes[0]);
    }
  });
}

const collection = ref<Collection>();
function fetchCollection() {
  CollectionService.getCollection({ collectionId: +currentCollectionId.value })
    .then(response => {
      collection.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

const dataObjects = ref<Map<number, DataObject>>(new Map());
function fetchDataObjects() {
  loading.value = true;
  const rootObjects = new Array<DataObject>();
  DataObjectService.getAllDataObjects({
    collectionId: +currentCollectionId.value,
  })
    .then(response => {
      response.forEach(obj => {
        if (obj.id) dataObjects.value.set(obj.id, obj);
        if (obj.parentId == undefined) rootObjects.push(obj);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching data objects");
    })
    .finally(() => {
      addDataNodes(rootObjects);
      loading.value = false;
    });
}

const references = ref<Map<number, BasicReference>>(new Map());
function fetchReferences(dataObjectId: number) {
  loading.value = true;
  const refObjects = new Array<BasicReference>();
  ReferenceService.getAllReferences({
    collectionId: +currentCollectionId.value,
    dataObjectId: dataObjectId,
  })
    .then(response => {
      response.forEach(obj => {
        if (obj.id) {
          references.value.set(obj.id, obj);
          refObjects.push(obj);
        }
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching references");
    })
    .finally(() => {
      addReferenceNodes(refObjects);
      updateSelectedNode(dataObjectId);
      loading.value = false;
    });
}

function addDataObjects(parentId?: number) {
  const nodesToAdd = new Array<DataObject>();

  const idsToAdd = parentId
    ? dataObjects.value.get(parentId)?.childrenIds
    : [...dataObjects.value.keys()];
  idsToAdd?.forEach(objId => {
    const obj = dataObjects.value.get(objId);
    if (obj?.id && !nodes.get(obj.id)) nodesToAdd.push(obj);
  });
  addDataNodes(nodesToAdd);
  updateSelectedNode(parentId);
}

function removeDataObjects(parentId: number) {
  const nodesToRemove = new Array<IdType>();
  const edgesToRemove = new Array<IdType>();

  const parent = dataObjects.value.get(parentId);
  parent?.childrenIds?.forEach(objId => {
    nodesToRemove.push(objId);
    edgesToRemove.push(...network.getConnectedEdges(objId));
  });

  nodes.remove(nodesToRemove);
  edges.remove(edgesToRemove);
  updateSelectedNode(parentId);
}
function removeReferences(dataObjectId: number) {
  const nodesToRemove = new Array<IdType>();
  const edgesToRemove = new Array<IdType>();

  const dataObject = dataObjects.value.get(dataObjectId);
  dataObject?.referenceIds?.forEach(objId => {
    nodesToRemove.push(objId);
    edgesToRemove.push(...network.getConnectedEdges(objId));
  });

  nodes.remove(nodesToRemove);
  edges.remove(edgesToRemove);
  updateSelectedNode(dataObjectId);
}

function addDataNodes(newObjects: DataObject[]) {
  const tmpNodes = new Array<Node>();
  const tmpEdges = new Array<Edge>();

  newObjects.forEach(obj => {
    tmpNodes.push({
      id: obj.id,
      label: obj.name || "",
      physics: true,
      color: dataNodeColor,
    });
    if (obj.parentId)
      tmpEdges.push({
        from: obj.parentId,
        to: obj.id,
        color: parentChildColor,
      });
    obj.predecessorIds?.forEach(pre => {
      tmpEdges.push({ from: pre, to: obj.id, color: preSucColor });
    });
  });

  nodes.add(tmpNodes);
  edges.add(tmpEdges);
}

function addReferenceNodes(newReferences: BasicReference[]) {
  const tmpNodes = new Array<Node>();
  const tmpEdges = new Array<Edge>();

  newReferences.forEach(obj => {
    tmpNodes.push({
      id: obj.id,
      label: obj.name || "",
      physics: true,
      color: refNodeColor,
    });
    tmpEdges.push({
      from: obj.dataObjectId,
      to: obj.id,
      color: referenceColor,
    });
  });
  nodes.add(tmpNodes);
  edges.add(tmpEdges);
}

function setPhysics(id: number, physics: boolean) {
  nodes.update([
    {
      id: id,
      physics: physics,
    },
  ]);
  updateSelectedNode(id);
}

function reset() {
  nodes.clear();
  edges.clear();
  const rootObjects = new Array<DataObject>();
  for (const obj of dataObjects.value.values()) {
    if (obj.parentId == undefined) rootObjects.push(obj);
  }
  addDataNodes(rootObjects);
  updateSelectedNode();
}

function unpinAll() {
  const updates = nodes
    .stream()
    .filter(node => !node.physics)
    .map(node => {
      return { id: node.id, physics: true };
    })
    .toItemArray();
  nodes.update(updates);
  updateSelectedNode();
}

function updateSelectedNode(id?: number) {
  if (id == undefined) {
    network.unselectAll();
    selectedNode.value = undefined;
  } else {
    network.selectNodes([id]);
    const obj = dataObjects.value.get(id);
    const ref = references.value.get(id);
    const node = nodes.get(id);
    if (node && (obj || ref))
      selectedNode.value = {
        id: id,
        node: node,
        dataObject: obj,
        reference: ref,
      };
  }
}

function getTabId(reference: BasicReference) {
  const tabIds: { [key: string]: number } = {
    TimeseriesReference: 0,
    StructuredDataReference: 1,
    FileReference: 2,
    URIReference: 3,
    CollectionReference: 4,
    DataObjectReference: 5,
  };
  return reference.type ? tabIds[reference.type] : 0;
}

onMounted(() => {
  initGraph();
  fetchCollection();
  fetchDataObjects();
});
</script>

<template>
  <div class="view-full">
    <b-container>
      <b-row>
        <b-col cols="3">
          <h3>{{ collection?.name }}</h3>
          <div class="mb-5">
            <div>
              <div v-if="selectedNode?.dataObject">
                Name:
                <b-link
                  :to="{
                    name: 'DataObject',
                    params: {
                      collectionId: currentCollectionId,
                      dataObjectId: selectedNode.id,
                    },
                  }"
                >
                  {{ selectedNode.dataObject.name || "" }}
                </b-link>
                <div>
                  ID: <b>{{ selectedNode.id }}</b>
                </div>

                <b-button-group class="mt-3" vertical>
                  <b-button
                    v-if="canExpandDataObjects"
                    @click="addDataObjects(selectedNode.id)"
                  >
                    Show Data Objects
                  </b-button>
                  <b-button
                    v-if="canCollapseDataObjects"
                    @click="removeDataObjects(selectedNode.id)"
                  >
                    Hide Data Objects
                  </b-button>

                  <b-button
                    v-if="canExpandReferences"
                    @click="fetchReferences(selectedNode.id)"
                  >
                    Show References
                  </b-button>
                  <b-button
                    v-if="canCollapseReferences"
                    @click="removeReferences(selectedNode.id)"
                  >
                    Hide References
                  </b-button>
                  <b-button
                    v-if="selectedNode.node.physics"
                    @click="setPhysics(selectedNode.id, false)"
                  >
                    Pin
                  </b-button>
                  <b-button v-else @click="setPhysics(selectedNode.id, true)">
                    Unpin
                  </b-button>
                </b-button-group>
              </div>

              <div v-else-if="selectedNode?.reference">
                Name:
                <b-link
                  :to="{
                    name: 'DataObject',
                    params: {
                      collectionId: currentCollectionId,
                      dataObjectId: selectedNode.reference.dataObjectId,
                    },
                    query: {
                      tabId: getTabId(selectedNode.reference),
                      referenceId: selectedNode.reference.id,
                    },
                  }"
                >
                  {{ selectedNode.reference.name || "" }}
                </b-link>
                <div>
                  ID: <b>{{ selectedNode.id }}</b>
                </div>
                <div>
                  Type: <b>{{ selectedNode.reference.type }}</b>
                </div>
                <b-button-group class="mt-3" vertical>
                  <b-button
                    v-if="selectedNode.node.physics"
                    @click="setPhysics(selectedNode.id, false)"
                  >
                    Pin
                  </b-button>
                  <b-button v-else @click="setPhysics(selectedNode.id, true)">
                    Unpin
                  </b-button>
                </b-button-group>
              </div>

              <div v-else class="mb-3">Select a node...</div>
            </div>
          </div>

          <b-button-group class="mb-3" vertical>
            <b-button @click="addDataObjects()">Expand All</b-button>
            <b-button @click="unpinAll()">Unpin All</b-button>
            <b-button @click="reset()">Reset</b-button>
          </b-button-group>
        </b-col>
        <b-col>
          <b-overlay :show="loading" spinner-type="grow">
            <div id="graph-container"></div>
          </b-overlay>
        </b-col>
      </b-row>
    </b-container>
  </div>
</template>

<style scoped>
#graph-container {
  width: 100%;
  height: 800px;
  border: 1px solid lightgray;
}
</style>
