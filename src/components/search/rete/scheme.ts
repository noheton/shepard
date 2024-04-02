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

export type ComplexClauseNode = AndNode | OrNode | XOrNode | NotNode;
export type ClauseNode = SimpleClauseNode | ComplexClauseNode;
export type Node = SimpleClauseNode | ComplexClauseNode | SolutionNode;

export class Connection<
  A extends Node,
  B extends Node,
> extends ClassicPreset.Connection<A, B> {}

export type ConnProps =
  | Connection<SimpleClauseNode, ComplexClauseNode>
  | Connection<SimpleClauseNode, SolutionNode>
  | Connection<ComplexClauseNode, ComplexClauseNode>
  | Connection<ComplexClauseNode, SolutionNode>;
export type Schemes = GetSchemes<Node, ConnProps>;

export type AreaExtra = VueArea2D<Schemes> | ContextMenuExtra;
