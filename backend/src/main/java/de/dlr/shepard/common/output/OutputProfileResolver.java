package de.dlr.shepard.common.output;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for the resolved {@link OutputProfile}.
 * Populated by {@link OutputProfileFilter} from the {@code ?profile=}
 * query parameter; injected into REST resources / serialisers that
 * want to vary their response shape.
 *
 * <p>Designed in {@code aidocs/56 §3}.
 */
@RequestScoped
public class OutputProfileResolver {

  private OutputProfile profile = OutputProfile.DEFAULT;

  public OutputProfile getProfile() {
    return profile;
  }

  public void setProfile(OutputProfile profile) {
    this.profile = profile == null ? OutputProfile.DEFAULT : profile;
  }

  /** Convenience: {@code true} when the caller asked for the minimal payload. */
  public boolean isMetadataOnly() {
    return profile == OutputProfile.METADATA;
  }

  /** Convenience: {@code true} when the caller asked for relation-id-only payload. */
  public boolean isRelationsOnly() {
    return profile == OutputProfile.RELATIONS;
  }
}
