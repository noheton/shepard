import { isNumeric } from "@/utils/helpers";
import { ClassicPreset } from "rete";

function jsonParse(str: string) {
  try {
    return JSON.parse(str);
  } catch {
    return {};
  }
}

export interface SimpleClause {
  property: string;
  operator: string;
  value: string | number | boolean;
}

export function isSimpleClause(object: object): object is SimpleClause {
  return "property" in object && "operator" in object && "value" in object;
}

const socket = new ClassicPreset.Socket("socket");

export class LabelledInputControl extends ClassicPreset.Control {
  constructor(
    public label: string,
    public value: string = "",
    public change?: () => void,
  ) {
    super();
  }
}

export class DropdownInputControl extends ClassicPreset.Control {
  constructor(
    public label: string,
    public options: Map<string, string>,
    public value: string = "",
    public change?: () => void,
  ) {
    super();
  }
}

const operators = new Map<string, string>([
  ["contains", "contains"],
  ["eq", "equals"],
  ["ge", "greater or equal"],
  ["gt", "greater than"],
  ["in", "in list"],
  ["le", "lower or equal"],
  ["lt", "lower than"],
  ["ne", "not equal"],
]);

export class SimpleClauseNode extends ClassicPreset.Node<
  Record<string, never>,
  {
    prop: ClassicPreset.Socket;
    op: ClassicPreset.Socket;
    value: ClassicPreset.Socket;
  },
  {
    prop: LabelledInputControl;
    op: DropdownInputControl;
    value: LabelledInputControl;
  }
> {
  height = 240;
  width = 180;

  constructor(change?: () => void, init?: SimpleClause) {
    super("Simple Clause");

    this.addControl(
      "prop",
      new LabelledInputControl("property", init?.property, change),
    );
    this.addControl(
      "op",
      new DropdownInputControl("operator", operators, init?.operator, change),
    );
    this.addControl(
      "value",
      new LabelledInputControl(
        "value",
        init ? init.value.toString() : undefined,
        change,
      ),
    );
    this.addOutput("value", new ClassicPreset.Output(socket, "Output", false));
  }

  convertValue(text: string): string | number | boolean {
    if (["true", "false"].includes(text)) {
      return text == "true";
    }

    if (isNumeric(text)) {
      return +text;
    }

    return text;
  }

  data(): { value: string } {
    const clause: SimpleClause = {
      property: this.controls.prop.value,
      operator: this.controls.op.value,
      value: this.convertValue(this.controls.value.value),
    };
    const value = JSON.stringify(clause);

    return { value };
  }
}

const HEIGHT_PER_INPUT = 35;
abstract class LogicNode extends ClassicPreset.Node<
  Record<string, ClassicPreset.Socket>,
  { value: ClassicPreset.Socket },
  { value: ClassicPreset.Control }
> {
  height = 110;
  width = 180;

  constructor(
    operator: string,
    private update?: (id: string) => void,
    inputs: number = 2,
  ) {
    super(operator);
    for (let i = 0; i < inputs; i++) {
      this.addInputNode();
    }
    this.addOutput("value", new ClassicPreset.Output(socket, "Output", false));
  }

  addInputNode() {
    const index = Object.keys(this.inputs).length + 1;
    this.height += HEIGHT_PER_INPUT;
    const newNode = new ClassicPreset.Input(socket, "E" + index, false);
    newNode.showControl = false;
    newNode.addControl(
      new ClassicPreset.InputControl("text", { readonly: true }),
    );
    this.addInput("e" + index, newNode);
    if (this.update) this.update(this.id);
  }

  removeInputNode() {
    const index = Object.keys(this.inputs).length;
    this.height -= HEIGHT_PER_INPUT;
    this.removeInput("e" + index);
    if (this.update) this.update(this.id);
  }

  getLatestInputNodeId() {
    const index = Object.keys(this.inputs).length;
    return "e" + index;
  }

  protected cleanInputs(inputs: { [key: string]: string[] }) {
    return Object.values(inputs)
      .filter(v => v?.length > 0)
      .map(v => v[0]);
  }
}

export class AndNode extends LogicNode {
  constructor(update?: (id: string) => void, inputs = 2) {
    super("AND", update, inputs);
  }

  makeAnd(inputs: string[]): string {
    const jsonObjects = inputs.map(s => jsonParse(s));

    const result = { AND: jsonObjects };
    return JSON.stringify(result);
  }

  data(inputs: { [key: string]: string[] }): { value: string } {
    const value = this.makeAnd(this.cleanInputs(inputs));
    return { value };
  }
}

export class OrNode extends LogicNode {
  constructor(update?: (id: string) => void, inputs = 2) {
    super("OR", update, inputs);
  }

  makeOr(inputs: string[]): string {
    const jsonObjects = inputs.map(s => jsonParse(s));

    const result = { OR: jsonObjects };
    return JSON.stringify(result);
  }

  data(inputs: { [key: string]: string[] }): { value: string } {
    const value = this.makeOr(this.cleanInputs(inputs));
    return { value };
  }
}

export class XOrNode extends LogicNode {
  constructor(update?: (id: string) => void, inputs = 2) {
    super("XOR", update, inputs);
  }

  makeXOr(inputs: string[]): string {
    const jsonObjects = inputs.map(s => jsonParse(s));

    const result = { XOR: jsonObjects };
    return JSON.stringify(result);
  }

  data(inputs: { [key: string]: string[] }): { value: string } {
    const value = this.makeXOr(this.cleanInputs(inputs));
    return { value };
  }
}

export class NotNode extends ClassicPreset.Node<
  Record<string, ClassicPreset.Socket>,
  { value: ClassicPreset.Socket },
  { value: ClassicPreset.Control }
> {
  height = 145;
  width = 180;

  constructor() {
    super("NOT");
    const e1 = new ClassicPreset.Input(socket, "E1", false);
    e1.showControl = false;
    e1.addControl(new ClassicPreset.InputControl("text", { readonly: true }));

    this.addInput("e1", e1);
    this.addOutput("value", new ClassicPreset.Output(socket, "Output", false));
  }

  static makeNot(e1: string): string {
    const e1Obj = jsonParse(e1);

    const result = { NOT: e1Obj };
    return JSON.stringify(result);
  }

  data(inputs: { e1?: string[] }): { value: string } {
    const e1 = inputs.e1 ? inputs.e1[0] : "";
    const value = NotNode.makeNot(e1);
    return { value };
  }
}

export class SolutionNode extends ClassicPreset.Node<
  { input: ClassicPreset.Socket },
  { value: ClassicPreset.Socket },
  { value: ClassicPreset.Control }
> {
  height = 110;
  width = 180;

  constructor() {
    super("Solution");
    const input = new ClassicPreset.Input(socket, "Input", false);
    input.showControl = false;
    input.addControl(
      new ClassicPreset.InputControl("text", { readonly: true }),
    );

    this.addInput("input", input);
  }

  data(inputs: { input: string[] }): { value: string } {
    const value = inputs.input ? inputs.input[0] : "{}";
    return { value };
  }
}
