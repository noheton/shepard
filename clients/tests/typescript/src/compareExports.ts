import * as client from "../../../typescript/src/index";
import * as newClient from "../../../typescript/src/index";

export const compareExports = () => {
  const oldExports = Object.keys(client);
  const newExports = Object.keys(newClient);

  console.log("The following exports are missing in the new client");
  const missingExports = oldExports.filter((v) => newExports.indexOf(v) < 0);
  missingExports.forEach((k) => console.log(" - ", k, "(of type", typeof (client as any)[k] + ")"));

  console.log("The following exports are added in the new client");
  const addedExports = newExports.filter((v) => oldExports.indexOf(v) < 0);
  addedExports.forEach((k) => console.log(" - ", k, "(of type", typeof (newClient as any)[k] + ")"));

  if (missingExports.length > 0) {
    throw new Error("Some exports are missing in the new version!");
  }
};

compareExports();
