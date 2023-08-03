<script setup lang="ts">
import CollectionService from "@/services/collectionService";
import DataObjectService from "@/services/dataObjectService";
import { colorCalculator } from "@/utils/colors";
import { handleError } from "@/utils/error-handling";
import type {
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

const nodeColor = "#7fbadd";
const parentChildColor = colorCalculator(3);
const preSucColor = colorCalculator(0);
const graphOptions = {
  nodes: {
    color: nodeColor,
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

const selectedNode = ref<{ id: number; node: Node; dataObject: DataObject }>();
const canExpand = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject.childrenIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const childrenIds = selectedNode.value.dataObject.childrenIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return childrenIds.some(
    childId => findChildEdge(connectedEdges, childId, selectedId) == undefined,
  );
});
const canCollapse = computed(() => {
  if (
    selectedNode.value?.id == undefined ||
    selectedNode.value?.dataObject.childrenIds == undefined
  )
    return false;
  const selectedId = selectedNode.value.id;
  const childrenIds = selectedNode.value.dataObject.childrenIds;
  const connectedEdges = getConnectedEdges(selectedId);
  return (
    childrenIds.length > 0 &&
    childrenIds.every(
      childId =>
        findChildEdge(connectedEdges, childId, selectedId) != undefined,
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
      addNodes(rootObjects);
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
  addNodes(nodesToAdd);
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

function addNodes(newObjects: DataObject[]) {
  const tmpNodes = new Array<Node>();
  const tmpEdges = new Array<Edge>();

  newObjects.forEach(obj => {
    tmpNodes.push({
      id: obj.id,
      label: obj.name || "",
      physics: true,
    });
    if (obj.parentId)
      tmpEdges.push({
        from: obj.id,
        to: obj.parentId,
        color: parentChildColor,
      });
    obj.predecessorIds?.forEach(pre => {
      tmpEdges.push({ from: pre, to: obj.id, color: preSucColor });
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
  addNodes(rootObjects);
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
    const node = nodes.get(id);
    if (obj && node)
      selectedNode.value = {
        id: id,
        node: node,
        dataObject: obj,
      };
  }
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
          <div v-if="selectedNode">
            <div class="mb-3">
              <p>
                ID: <b>{{ selectedNode.id }}</b>
              </p>
              <p>
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
              </p>
            </div>
            <b-button-group class="mb-3" vertical>
              <b-button
                v-if="canExpand"
                @click="addDataObjects(selectedNode.id)"
              >
                Expand
              </b-button>
              <b-button
                v-if="canCollapse"
                @click="removeDataObjects(selectedNode.id)"
              >
                Collapse
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
          <div v-else class="mb-3">Select a node...</div>
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
