import mitt from "mitt";

type Events = {
  error: string;
};

export const emitter = mitt<Events>();
