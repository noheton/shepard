import { isNumeric } from "@/utils/helpers";
import { ClassicPreset } from "rete";

function jsonParse(str: string) {
  try {
    return JSON.parse(str);
  } catch {
    return {};
  }
}

interface SimpleClause {
  property: string;
  operator: string;
  value: string | number | boolean;
}

const socket = new ClassicPreset.Socket("socket");

export class LabelledInputControl extends ClassicPreset.Control {
  value = "";
  constructor(
    public label: string,
    public change?: () => void,
  ) {
    super();
  }
}

export class DropdownInputControl extends ClassicPreset.Control {
  value = "";
  constructor(
    public label: string,
    public options: Map<string, string>,
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

  constructor(change?: () => void) {
    super("Simple Clause");

    this.addControl("prop", new LabelledInputControl("property", change));
    this.addControl(
      "op",
      new DropdownInputControl("operator", operators, change),
    );
    this.addControl("value", new LabelledInputControl("value", change));
    this.addOutput("value", new ClassicPreset.Output(socket, "Output"));
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

abstract class LogicNode extends ClassicPreset.Node<
  { e1: ClassicPreset.Socket; e2: ClassicPreset.Socket },
  { value: ClassicPreset.Socket },
  { value: ClassicPreset.Control }
> {
  height = 180;
  width = 180;

  constructor(operator: string) {
    super(operator);
    const e1 = new ClassicPreset.Input(socket, "E1");
    e1.showControl = false;
    e1.addControl(new ClassicPreset.InputControl("text", { readonly: true }));

    const e2 = new ClassicPreset.Input(socket, "E2");
    e2.showControl = false;
    e2.addControl(new ClassicPreset.InputControl("text", { readonly: true }));

    this.addInput("e1", e1);
    this.addInput("e2", e2);
    this.addOutput("value", new ClassicPreset.Output(socket, "Output"));
  }
}

export class AndNode extends LogicNode {
  constructor() {
    super("AND");
  }

  static makeAnd(e1: string, e2: string): string {
    const e1Obj = jsonParse(e1);
    const e2Obj = jsonParse(e2);

    const result = { AND: [e1Obj, e2Obj] };
    return JSON.stringify(result);
  }

  data(inputs: { e1?: string[]; e2?: string[] }): { value: string } {
    const e1 = inputs.e1 ? inputs.e1[0] : "";
    const e2 = inputs.e2 ? inputs.e2[0] : "";
    const value = AndNode.makeAnd(e1, e2);
    return { value };
  }
}

export class OrNode extends LogicNode {
  constructor() {
    super("OR");
  }

  static makeOr(e1: string, e2: string): string {
    const e1Obj = jsonParse(e1);
    const e2Obj = jsonParse(e2);

    const result = { OR: [e1Obj, e2Obj] };
    return JSON.stringify(result);
  }

  data(inputs: { e1?: string[]; e2?: string[] }): { value: string } {
    const e1 = inputs.e1 ? inputs.e1[0] : "";
    const e2 = inputs.e2 ? inputs.e2[0] : "";
    const value = OrNode.makeOr(e1, e2);
    return { value };
  }
}

export class NotNode extends ClassicPreset.Node<
  { e1: ClassicPreset.Socket },
  { value: ClassicPreset.Socket },
  { value: ClassicPreset.Control }
> {
  height = 150;
  width = 180;

  constructor() {
    super("NOT");
    const e1 = new ClassicPreset.Input(socket, "E1");
    e1.showControl = false;
    e1.addControl(new ClassicPreset.InputControl("text", { readonly: true }));

    this.addInput("e1", e1);
    this.addOutput("value", new ClassicPreset.Output(socket, "Output"));
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
    const input = new ClassicPreset.Input(socket, "Input");
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
