import { JSONValue } from "k6";

export function getIdFromResponse(response: JSONValue): number {
  if (response != null && typeof response == "object" && "id" in response) {
    return response.id as number;
  }
  console.error(JSON.stringify(response));
  throw new Error("Response body does not contain property 'id'.");
}
