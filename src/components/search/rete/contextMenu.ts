import type { BaseSchemes, NodeEditor } from "rete";
import { ContextMenuPlugin } from "rete-context-menu-plugin";
import type { Item, Items } from "rete-context-menu-plugin/_types/types";
import { SolutionNode } from "./ReteNodes";

export class CustomContextMenu<Scheme extends BaseSchemes> {
  private createItem(
    editor: NodeEditor<Scheme>,
    key: string,
    label: string,
    gen: () => Scheme["Node"],
  ): Item {
    return {
      key: key,
      label: label,
      handler: () => editor.addNode(gen()),
    };
  }

  getContextMenu(
    editor: NodeEditor<Scheme>,
    nodes: { label: string; gen: () => Scheme["Node"] }[],
  ) {
    const items = nodes.map((value, index) =>
      this.createItem(editor, String(index), value.label, value.gen),
    );

    const props: { items: Items<Scheme> } = {
      items(context) {
        if (context === "root") {
          return {
            searchBar: false,
            list: items,
          };
        }

        if (!(context instanceof SolutionNode)) {
          return {
            searchBar: false,
            list: [
              {
                label: "Delete",
                key: "delete",
                async handler() {
                  const nodeId = context.id;
                  const connections = editor.getConnections().filter(c => {
                    return c.source === nodeId || c.target === nodeId;
                  });

                  for (const connection of connections) {
                    await editor.removeConnection(connection.id);
                  }
                  await editor.removeNode(nodeId);
                },
              },
            ],
          };
        }

        return {
          searchBar: false,
          list: [],
        };
      },
    };

    return new ContextMenuPlugin<Scheme>(props);
  }
}
