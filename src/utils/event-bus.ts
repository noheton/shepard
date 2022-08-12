import mitt from "mitt";

export type ErrorType = {
  status: number;
  exception: string;
  message: string;
};

type Events = {
  error: string;
  extendedError: {
    error: ErrorType;
    situation: string;
  };
};

export const emitter = mitt<Events>();
