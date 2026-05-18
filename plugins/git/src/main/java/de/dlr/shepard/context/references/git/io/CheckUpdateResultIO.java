package de.dlr.shepard.context.references.git.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * G1d — body returned by {@code POST /v2/data-objects/{do}/git-references/{ref}/check-update}.
 *
 * <p>Whether the upstream SHA has moved since the GitReference's last
 * resolution. {@code currentSha} is the SHA the upstream is at now;
 * {@code previousSha} is what we had recorded in
 * {@link de.dlr.shepard.context.references.git.entities.GitReference#getResolvedSha()}
 * before this call. {@code updated == (previousSha != null && !previousSha.equals(currentSha))}.
 *
 * <p>When {@code previousSha} is null (first check, or LOOSE_LINK mode
 * never resolved), the side-effect of the call is to populate
 * {@code resolvedSha} for the first time and the IO returns
 * {@code updated: false}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckUpdateResultIO(
  /** The SHA the upstream is at right now. */
  String currentSha,
  /** The SHA we had recorded before this call (may be null on first check). */
  String previousSha,
  /** True iff currentSha != previousSha AND previousSha was non-null. */
  boolean updated,
  /** Millis-epoch this check happened. */
  long checkedAtMillis
) {}
