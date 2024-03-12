import { CustomContextMenu } from "@/components/search/rete/contextMenu";
import DropdownInput from "@/components/search/rete/DropdownInput.vue";
import LabelledInput from "@/components/search/rete/LabelledInput.vue";
import {
  AndNode,
  DropdownInputControl,
  LabelledInputControl,
  NotNode,
  OrNode,
  SimpleClauseNode,
  SolutionNode,
  XOrNode,
} from "@/components/search/rete/ReteNodes";
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
import type { AreaExtra, Schemes } from "./scheme";

export async function createEditor(container: HTMLElement) {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new VuePlugin<Schemes, AreaExtra>();
  const arrange = new AutoArrangePlugin<Schemes>();
  const engine = new DataflowEngine<Schemes>();

  function process() {
    engine.reset();
    editor.getNodes().forEach(n => engine.fetch(n.id));
  }

  function updateNode(id: string) {
    area.update("node", id);
  }

  const contextMenu = new CustomContextMenu().getContextMenu(editor, [
    { label: "Simple Clause", gen: () => new SimpleClauseNode(process) },
    { label: "AND", gen: () => new AndNode(updateNode) },
    { label: "OR", gen: () => new OrNode(updateNode) },
    { label: "XOR", gen: () => new XOrNode(updateNode) },
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
          if (data.payload instanceof DropdownInputControl) {
            return DropdownInput;
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
