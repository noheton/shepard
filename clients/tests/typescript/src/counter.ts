import {
  ApikeyApi,
  CreateApiKeyRequest,
  DeleteApiKeyRequest,
  GetAllApiKeysRequest,
  GetApiKeyRequest,
  PermissionsPermissionTypeEnum,
} from "@dlr-shepard/shepard-client";

export function setupCounter(element: HTMLButtonElement) {
  let counter = 0;
  const setCounter = (count: number) => {
    counter = count;
    element.innerHTML = `count is ${counter}`;
  };
  element.addEventListener("click", () => setCounter(counter + 1));
  setCounter(0);

  PermissionsPermissionTypeEnum;
  var test: CreateApiKeyRequest;
  var test2: DeleteApiKeyRequest;
  var t3: GetAllApiKeysRequest;
  var t4: GetApiKeyRequest;
  ApikeyApi;
}
