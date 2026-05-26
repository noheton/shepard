/**
 * FS1e1 composable wrapping the file-storage migration endpoints:
 *   GET  /v2/admin/storage           — list storage adapters + active provider
 *   POST /v2/admin/files/migrate     — trigger a migration job (202 Accepted)
 *   GET  /v2/admin/files/migrate/status — poll the in-memory job state
 *   POST /v2/admin/files/migrate/rollback/{appId} — per-file FS1e3 rollback
 *
 * Raw fetch — no generated client for these endpoints yet (same pattern as
 * useSqlTimeseriesConfig and useUnhideAdminConfig).
 */

// ─── Wire shapes ─────────────────────────────────────────────────────────────

export interface StorageAdapterIO {
  id: string;
  enabled: boolean;
  active: boolean;
}

export interface StorageStatusIO {
  activeProviderId: string | null;
  adapters: StorageAdapterIO[];
}

export type FileMigrationStatus = "IDLE" | "RUNNING" | "DONE" | "FAILED";

export interface FileMigrationStateIO {
  status: FileMigrationStatus;
  sourceProviderId: string | null;
  targetProviderId: string | null;
  filesTotal: number;
  filesMigrated: number;
  filesFailed: number;
  startedAt: string | null;
  updatedAt: string | null;
  errorMessage: string | null;
}

// ─── URL helpers ─────────────────────────────────────────────────────────────

function v2BaseUrl(): string {
  const cfg = useRuntimeConfig().public;
  const explicit = cfg.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (cfg.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function authHeaders(): Record<string, string> {
  const { data: session } = useAuth();
  const token = session.value?.accessToken;
  return token
    ? { Authorization: `Bearer ${token}`, Accept: "application/json" }
    : { Accept: "application/json" };
}

// ─── Composable ──────────────────────────────────────────────────────────────

export function useFileMigration() {
  // Storage adapter list
  const storageStatus = ref<StorageStatusIO | null>(null);
  const isLoadingStorage = ref(false);
  const storageError = ref<string | null>(null);

  // Current/last migration state
  const migrationState = ref<FileMigrationStateIO | null>(null);
  const isLoadingState = ref(false);
  const migrationError = ref<string | null>(null);

  // Trigger
  const isTriggering = ref(false);
  const triggerError = ref<string | null>(null);
  const triggerSuccess = ref(false);

  // Rollback
  const isRollingBack = ref(false);
  const rollbackError = ref<string | null>(null);
  const rollbackSuccess = ref<string | null>(null);

  // Polling handle
  let pollInterval: ReturnType<typeof setInterval> | null = null;

  // ─── Storage adapters ────────────────────────────────────────────────────

  async function refreshStorage() {
    isLoadingStorage.value = true;
    storageError.value = null;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/admin/storage`, {
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      storageStatus.value = (await res.json()) as StorageStatusIO;
    } catch (e) {
      storageError.value = "Failed to load storage adapters.";
      handleError(e, "fetching storage adapters");
    } finally {
      isLoadingStorage.value = false;
    }
  }

  // ─── Migration status ────────────────────────────────────────────────────

  async function refreshMigrationState() {
    isLoadingState.value = true;
    migrationError.value = null;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/admin/files/migrate/status`, {
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      migrationState.value = (await res.json()) as FileMigrationStateIO;
    } catch (e) {
      migrationError.value = "Failed to load migration status.";
      handleError(e, "fetching migration state");
    } finally {
      isLoadingState.value = false;
    }
  }

  function startPolling() {
    if (pollInterval !== null) return;
    pollInterval = setInterval(async () => {
      await refreshMigrationState();
      if (
        migrationState.value?.status === "DONE" ||
        migrationState.value?.status === "FAILED" ||
        migrationState.value?.status === "IDLE"
      ) {
        stopPolling();
      }
    }, 2000);
  }

  function stopPolling() {
    if (pollInterval !== null) {
      clearInterval(pollInterval);
      pollInterval = null;
    }
  }

  // ─── Trigger migration ───────────────────────────────────────────────────

  async function triggerMigration(
    sourceProviderId: string,
    targetProviderId: string,
  ): Promise<boolean> {
    isTriggering.value = true;
    triggerError.value = null;
    triggerSuccess.value = false;
    try {
      const res = await fetch(`${v2BaseUrl()}/v2/admin/files/migrate`, {
        method: "POST",
        headers: { ...authHeaders(), "Content-Type": "application/json" },
        body: JSON.stringify({ sourceProviderId, targetProviderId }),
      });
      if (!res.ok) {
        const body = await res.text().catch(() => "");
        let detail = `Trigger failed (HTTP ${res.status})`;
        try {
          const parsed = JSON.parse(body);
          if (typeof parsed?.detail === "string") detail = parsed.detail;
          else if (typeof parsed?.title === "string") detail = parsed.title;
          else if (typeof parsed?.message === "string") detail = parsed.message;
        } catch {
          // ignore parse errors
        }
        triggerError.value = detail;
        return false;
      }
      migrationState.value = (await res.json()) as FileMigrationStateIO;
      triggerSuccess.value = true;
      // Start polling automatically when job kicks off
      startPolling();
      return true;
    } catch (e) {
      triggerError.value = "Failed to trigger migration.";
      handleError(e, "triggering file migration");
      return false;
    } finally {
      isTriggering.value = false;
    }
  }

  // ─── Per-file rollback (FS1e3) ───────────────────────────────────────────

  async function rollbackFile(appId: string): Promise<boolean> {
    isRollingBack.value = true;
    rollbackError.value = null;
    rollbackSuccess.value = null;
    try {
      const res = await fetch(
        `${v2BaseUrl()}/v2/admin/files/migrate/rollback/${encodeURIComponent(appId)}`,
        {
          method: "POST",
          headers: authHeaders(),
        },
      );
      if (!res.ok) {
        const body = await res.text().catch(() => "");
        let detail = `Rollback failed (HTTP ${res.status})`;
        try {
          const parsed = JSON.parse(body);
          if (typeof parsed?.detail === "string") detail = parsed.detail;
          else if (typeof parsed?.title === "string") detail = parsed.title;
          else if (typeof parsed?.message === "string") detail = parsed.message;
        } catch {
          // ignore parse errors
        }
        rollbackError.value = detail;
        return false;
      }
      rollbackSuccess.value = appId;
      await refreshMigrationState();
      return true;
    } catch (e) {
      rollbackError.value = "Failed to roll back file.";
      handleError(e, "rolling back file migration");
      return false;
    } finally {
      isRollingBack.value = false;
    }
  }

  // ─── Lifecycle ───────────────────────────────────────────────────────────

  onUnmounted(() => {
    stopPolling();
  });

  // Initial load
  refreshStorage();
  refreshMigrationState();

  // Auto-poll if a job was already running before the page opened
  watch(migrationState, (state) => {
    if (state?.status === "RUNNING") {
      startPolling();
    }
  });

  return {
    // Storage
    storageStatus,
    isLoadingStorage,
    storageError,
    refreshStorage,
    // Migration state
    migrationState,
    isLoadingState,
    migrationError,
    refreshMigrationState,
    // Trigger
    isTriggering,
    triggerError,
    triggerSuccess,
    triggerMigration,
    // Rollback
    isRollingBack,
    rollbackError,
    rollbackSuccess,
    rollbackFile,
    // Polling control (exposed for testing)
    startPolling,
    stopPolling,
  };
}
