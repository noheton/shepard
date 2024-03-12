import { ClassicPreset, type GetSchemes } from "rete";
import type { ContextMenuExtra } from "rete-context-menu-plugin";
import type { VueArea2D } from "rete-vue-plugin";
import type {
  AndNode,
  NotNode,
  OrNode,
  SimpleClauseNode,
  SolutionNode,
  XOrNode,
} from "./ReteNodes";

type ComplexClauseNode = AndNode | OrNode | XOrNode | NotNode;
type Node = SimpleClauseNode | ComplexClauseNode | SolutionNode;

class Connection<
  A extends Node,
  B extends Node,
> extends ClassicPreset.Connection<A, B> {}

type ConnProps = Connection<
  SimpleClauseNode | ComplexClauseNode,
  ComplexClauseNode | SolutionNode
>;
export type Schemes = GetSchemes<Node, ConnProps>;

export type AreaExtra = VueArea2D<Schemes> | ContextMenuExtra;
