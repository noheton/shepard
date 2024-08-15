import * as client from "@dlr-shepard/shepard-client";
import * as newClient from "../../../typescript/src/index";

export const compareExports = () => {
  const oldExports = Object.keys(client);
  const newExports = Object.keys(newClient);

  console.log("The following exports are not contained in the new client", "\n");
  oldExports
    .filter((v) => newExports.indexOf(v) < 0)
    .forEach((k) => console.log(k, "(of type", typeof (client as any)[k] + ")"));

  console.log("\n");
  console.log("The following exports are added in the new client", "\n");
  newExports
    .filter((v) => oldExports.indexOf(v) < 0)
    .forEach((k) => console.log(k, "(of type", typeof (newClient as any)[k] + ")"));
};

compareExports();
