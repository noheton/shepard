package de.dlr.shepard.plugins.importer.runs;

/**
 * IMP1a / PR-2 — enumeration of source-kinds an
 * {@link ImporterRun} can be created against.
 *
 * <p>Stored as a {@code text} column on the database side
 * (Hibernate {@code EnumType.STRING}) so adding a new kind is a
 * no-DDL change. The set listed here is the closed set the plugin
 * currently advertises; PR-3 ships the first source-adapter
 * ({@link #DLR_V5_SHEPARD}). Adding {@code GIT}, {@code S3},
 * {@code LOCAL_DROPBOX} in later PRs is a single enum value + a
 * matching adapter class.
 */
public enum ImporterSourceKind {
  /**
   * PR-3 — pull from a remote DLR shepard v5 instance via
   * {@code /shepard/api/...}. The single-instance pull the MFFD
   * dropbox import script worked against.
   */
  DLR_V5_SHEPARD,

  /** Reserved for the future {@code GitSource} adapter. */
  GIT,

  /** Reserved for the future {@code S3Source} adapter. */
  S3,

  /** Reserved for the future {@code LocalDropboxSource} adapter. */
  LOCAL_DROPBOX,
}
