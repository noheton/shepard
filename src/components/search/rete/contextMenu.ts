import type { NodeEditor } from "rete";
import { ContextMenuPlugin } from "rete-context-menu-plugin";
import type { Item, Items } from "rete-context-menu-plugin/_types/types";
import { AndNode, OrNode, SolutionNode, XOrNode } from "./ReteNodes";
import type { Schemes } from "./scheme";

export class CustomContextMenu {
  private createItem(
    editor: NodeEditor<Schemes>,
    key: string,
    label: string,
    gen: () => Schemes["Node"],
  ): Item {
    return {
      key: key,
      label: label,
      handler: () => editor.addNode(gen()),
    };
  }

  getContextMenu(
    editor: NodeEditor<Schemes>,
    nodes: { label: string; gen: () => Schemes["Node"] }[],
  ) {
    const items = nodes.map((value, index) =>
      this.createItem(editor, String(index), value.label, value.gen),
    );

    const props: { items: Items<Schemes> } = {
      items(context) {
        if (context === "root") {
          return {
            searchBar: false,
            list: items,
          };
        }

        const menu: { searchBar: boolean; list: Item[] } = {
          searchBar: false,
          list: [],
        };
        if (!(context instanceof SolutionNode)) {
          menu.list.push({
            label: "Delete",
            key: "delete",
            async handler() {
              const nodeId = context.id;
              const connections = editor
                .getConnections()
                .filter(c => c.source === nodeId || c.target === nodeId);

              for (const connection of connections) {
                await editor.removeConnection(connection.id);
              }
              await editor.removeNode(nodeId);
            },
          });
        }
        if (
          context instanceof AndNode ||
          context instanceof OrNode ||
          context instanceof XOrNode
        ) {
          menu.list.push({
            label: "Add Input",
            key: "addInputNode",
            async handler() {
              context.addInputNode();
            },
          });

          if (Object.keys(context.inputs).length > 2) {
            menu.list.push({
              label: "Del Input",
              key: "removeInputNode",
              async handler() {
                context.removeInputNode(editor);
              },
            });
          }
        }

        return menu;
      },
    };

    return new ContextMenuPlugin<Schemes>(props);
  }
}
