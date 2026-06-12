/**
 * V2-SWEEP-001-CLIENT-REGEN — local git-reference wire shapes.
 *
 * The regenerated `@dlr-shepard/backend-client` folded the per-kind git
 * endpoints into the unified `/v2/references?kind=git` surface, whose generated
 * `ReferenceV2` model carries an untyped `payload` map ({repoUrl, ref, path,
 * mode}). The previously-generated typed `GitReferenceIO` / `CreateGitReferenceIO`
 * / `PatchGitReferenceIO` no longer exist as client exports.
 *
 * These local interfaces preserve the shape the UI consumers (GitReferencesPane,
 * dataTableElementMappingUtil) already read — `appId`, `repoUrl`, `ref`, `path` —
 * so the typed `ReferencesApi` calls in `useFetchGitReferences` /
 * `useManageGitReferences` map cleanly into and out of them.
 */

export interface GitReferenceIO {
  appId: string;
  repoUrl: string;
  ref?: string;
  path?: string;
}

export interface CreateGitReferenceIO {
  repoUrl: string;
  ref?: string;
  path?: string;
}

export type PatchGitReferenceIO = Partial<CreateGitReferenceIO>;
