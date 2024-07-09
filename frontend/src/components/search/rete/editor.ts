import { CustomContextMenu } from "@/components/search/rete/contextMenu";
import DropdownInput from "@/components/search/rete/DropdownInput.vue";
import LabelledInput from "@/components/search/rete/LabelledInput.vue";
import { NodeEditor } from "rete";
import { AreaExtensions, AreaPlugin } from "rete-area-plugin";
import {
  AutoArrangePlugin,
  Presets as ArrangePresets,
} from "rete-auto-arrange-plugin";
import {
  ConnectionPlugin,
  Presets as ConnectionPresets,
} from "rete-connection-plugin";
import { DataflowEngine } from "rete-engine";
import { Presets, VuePlugin } from "rete-vue-plugin/vue2";
import {
  AndNode,
  DropdownInputControl,
  isSimpleClause,
  LabelledInputControl,
  NotNode,
  OrNode,
  SimpleClauseNode,
  SolutionNode,
  XOrNode,
} from "./ReteNodes";
import {
  Connection,
  type AreaExtra,
  type ClauseNode,
  type ConnProps,
  type Node,
  type Schemes,
} from "./scheme";

export async function createEditor(container: HTMLElement, input: object) {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new VuePlugin<Schemes, AreaExtra>();
  const arrange = new AutoArrangePlugin<Schemes>();
  const engine = new DataflowEngine<Schemes>();

  function process() {
    engine.reset();
    editor
      .getNodes()
      .filter(n => !(n instanceof SimpleClauseNode))
      .forEach(n => engine.fetch(n.id));
  }

  async function updateNode(id: string) {
    await area.update("node", id);
  }

  const contextMenu = new CustomContextMenu().getContextMenu(editor, [
    { label: "Simple Clause", gen: () => new SimpleClauseNode(process) },
    { label: "AND", gen: () => new AndNode(updateNode) },
    { label: "OR", gen: () => new OrNode(updateNode) },
    { label: "XOR", gen: () => new XOrNode(updateNode) },
    { label: "NOT", gen: () => new NotNode() },
  ]);
  area.use(contextMenu);

  function parseNodes(input: object): {
    nodes: Node[];
    connections: ConnProps[];
  } {
    const nodes: Node[] = [];
    const connections: ConnProps[] = [];

    const s = new SolutionNode();
    nodes.push(s);

    const parsed = parseNode(input);
    nodes.push(parsed.rootNode);
    connections.push(new Connection(parsed.rootNode, "value", s, "input"));

    nodes.push(...parsed.nodes);
    connections.push(...parsed.connections);

    return { nodes: nodes, connections: connections };
  }

  function parseNode(input_obj: object): {
    rootNode: ClauseNode;
    nodes: ClauseNode[];
    connections: ConnProps[];
  } {
    if (isSimpleClause(input_obj)) {
      return {
        rootNode: new SimpleClauseNode(process, input_obj),
        nodes: [],
        connections: [],
      };
    }

    let node: ClauseNode | undefined = undefined;
    const nodes: ClauseNode[] = [];
    const connections: ConnProps[] = [];
    const operation = Object.keys(input_obj)[0];
    const child = Object.values(input_obj)[0];
    const children = [];

    switch (operation) {
      case "AND":
        node = new AndNode(updateNode, child.length);
        children.push(...child);
        break;
      case "OR":
        node = new OrNode(updateNode, child.length);
        children.push(...child);
        break;
      case "XOR":
        node = new XOrNode(updateNode, child.length);
        children.push(...child);
        break;
      case "NOT":
        node = new NotNode();
        children.push(child);
        break;
      default:
        console.error("Operation not supported: " + operation);
    }

    if (node == undefined) {
      return {
        rootNode: new SimpleClauseNode(process),
        nodes: [],
        connections: [],
      };
    }

    for (let i = 0; i < children.length; i++) {
      const parsed = parseNode(children[i]);
      nodes.push(parsed.rootNode);
      connections.push(
        new Connection(parsed.rootNode, "value", node, "e" + (i + 1)),
      );
      nodes.push(...parsed.nodes);
      connections.push(...parsed.connections);
    }

    return { rootNode: node, nodes: nodes, connections: connections };
  }

  // enables the user to select nodes
  AreaExtensions.selectableNodes(area, AreaExtensions.selector(), {
    accumulating: AreaExtensions.accumulateOnCtrl(),
  });

  render.addPreset(Presets.contextMenu.setup());
  render.addPreset(Presets.classic.setup());
  render.addPreset(
    Presets.classic.setup({
      customize: {
        control(data) {
          if (data.payload instanceof LabelledInputControl) {
            return LabelledInput;
          }
          if (data.payload instanceof DropdownInputControl) {
            return DropdownInput;
          }
          return Presets.classic.Control;
        },
      },
    }),
  );

  connection.addPreset(ConnectionPresets.classic.setup());

  arrange.addPreset(ArrangePresets.classic.setup());

  editor.use(engine);
  editor.use(area);
  area.use(connection);
  area.use(render);
  area.use(arrange);

  AreaExtensions.simpleNodesOrder(area);
  AreaExtensions.showInputControl(area);

  // refreshes all nodes when a connection is created or removed
  editor.addPipe(context => {
    if (["connectioncreated", "connectionremoved"].includes(context.type)) {
      process();
    }
    return context;
  });

  const { nodes, connections } = parseNodes(input);
  for (const node of nodes) {
    await editor.addNode(node);
  }
  for (const connection of connections) {
    await editor.addConnection(connection);
  }

  await arrange.layout();
  AreaExtensions.zoomAt(area, editor.getNodes());

  return {
    get: () => engine.fetch(nodes[0].id),
    destroy: () => area.destroy(),
  };
}
