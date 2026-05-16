package de.dlr.shepard.context.references.git.entities;

/**
 * Three use-modes from {@code aidocs/38 §2}:
 *
 * <ul>
 *   <li>{@link #LOOSE_LINK} — paste-a-URL mode. No fetch, no auth. G1a default.</li>
 *   <li>{@link #TRACKED_ARTIFACT} — {@code (repoUrl, ref, path)} tuple fetched via
 *       the user's PAT; inline preview rendered server-side. G1b.</li>
 *   <li>{@link #PINNED_SNAPSHOT} — {@code (repoUrl, sha, path)} pinned commit;
 *       immutable, used for reproducible RO-Crate exports. G1c.</li>
 * </ul>
 *
 * <p>Stored on the Neo4j node as a string property. Rows missing the property
 * (pre-G1b rows) are treated as {@link #LOOSE_LINK} by the migration V26 and by
 * the {@code GitReference} accessor defaulting.
 */
public enum GitReferenceMode {
  LOOSE_LINK,
  TRACKED_ARTIFACT,
  PINNED_SNAPSHOT,
}
