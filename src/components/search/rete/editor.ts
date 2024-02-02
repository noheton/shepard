import { CustomContextMenu } from "@/components/search/rete/contextMenu";
import LabelledInput from "@/components/search/rete/LabelledInput.vue";
import {
  AndNode,
  LabelledInputControl,
  NotNode,
  OrNode,
  SimpleClauseNode,
  SolutionNode,
} from "@/components/search/rete/ReteNodes";
import { ClassicPreset, NodeEditor, type GetSchemes } from "rete";
import { AreaExtensions, AreaPlugin } from "rete-area-plugin";
import {
  AutoArrangePlugin,
  Presets as ArrangePresets,
} from "rete-auto-arrange-plugin";
import {
  ConnectionPlugin,
  Presets as ConnectionPresets,
} from "rete-connection-plugin";
import type { ContextMenuExtra } from "rete-context-menu-plugin";
import { DataflowEngine } from "rete-engine";
import { Presets, VuePlugin, type VueArea2D } from "rete-vue-plugin/vue2";

class Connection<
  A extends Node,
  B extends Node,
> extends ClassicPreset.Connection<A, B> {}

type ComplexClauseNode = AndNode | OrNode | NotNode;
type Node = SimpleClauseNode | ComplexClauseNode | SolutionNode;

type ConnProps = Connection<
  SimpleClauseNode | ComplexClauseNode,
  ComplexClauseNode | SolutionNode
>;
type Schemes = GetSchemes<Node, ConnProps>;

type AreaExtra = VueArea2D<Schemes> | ContextMenuExtra;

export async function createEditor(container: HTMLElement) {
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
      .filter(n => n instanceof AndNode)
      .forEach(n => engine.fetch(n.id));
    editor
      .getNodes()
      .filter(n => n instanceof OrNode)
      .forEach(n => engine.fetch(n.id));
    editor
      .getNodes()
      .filter(n => n instanceof NotNode)
      .forEach(n => engine.fetch(n.id));
    editor
      .getNodes()
      .filter(n => n instanceof SolutionNode)
      .forEach(n => engine.fetch(n.id));
  }

  const contextMenu = new CustomContextMenu<Schemes>().getContextMenu(editor, [
    { label: "Simple Clause", gen: () => new SimpleClauseNode(process) },
    { label: "AND", gen: () => new AndNode() },
    { label: "OR", gen: () => new OrNode() },
    { label: "NOT", gen: () => new NotNode() },
  ]);
  area.use(contextMenu);

  // enables the user to select nodes
  AreaExtensions.selectableNodes(area, AreaExtensions.selector(), {
    accumulating: AreaExtensions.accumulateOnCtrl(),
  });

  render.addPreset(Presets.contextMenu.setup());
  render.addPreset(Presets.classic.setup());

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

  const s = new SolutionNode();
  await editor.addNode(s);

  await arrange.layout();
  AreaExtensions.zoomAt(area, editor.getNodes());

  render.addPreset(
    Presets.classic.setup({
      customize: {
        control(data) {
          if (data.payload instanceof LabelledInputControl) {
            return LabelledInput;
          }
          return Presets.classic.Control;
        },
      },
    }),
  );

  return {
    get: () => engine.fetch(s.id),
    destroy: () => area.destroy(),
  };
}
