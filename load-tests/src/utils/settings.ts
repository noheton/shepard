/**
 * Interface for Settings that can be defined in settings.json during runtime.
 */
export interface Settings {
  BACKEND_BASE_URL: string;
  API_KEY: string;
}

export function isSettings(obj: object): obj is Settings {
  const settings = obj as Settings;
  return settings.API_KEY !== undefined && settings.BACKEND_BASE_URL !== undefined;
}

let _settings: Settings | undefined = undefined;

export function getSettings(): Settings {
  if (_settings != undefined) return _settings as Settings;
  const fileContent = JSON.parse(open("/var/k6/settings.json"));
  if (isSettings(fileContent)) return (_settings = fileContent);
  throw new Error("settings.json is not a valid Settings object. Check your settings file.");
}
