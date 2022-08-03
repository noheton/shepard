declare global {
  interface Window {
    configs?: { [key: string]: string };
  }
}

export default function getEnv(name: string): string {
  if (!window.configs) return import.meta.env[name];
  return window.configs[name] || import.meta.env[name];
}
