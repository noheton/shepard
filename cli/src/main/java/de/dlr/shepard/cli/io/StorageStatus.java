package de.dlr.shepard.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * FS1e1 — CLI-side mirror of {@code StorageStatusIO} from
 * {@code GET /v2/admin/storage}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StorageStatus {

  private final String activeProviderId;
  private final List<Adapter> adapters;

  public StorageStatus(
    @JsonProperty("activeProviderId") String activeProviderId,
    @JsonProperty("adapters") List<Adapter> adapters
  ) {
    this.activeProviderId = activeProviderId;
    this.adapters = adapters != null ? adapters : List.of();
  }

  public String getActiveProviderId() { return activeProviderId; }
  public List<Adapter> getAdapters() { return adapters; }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class Adapter {
    private final String id;
    private final boolean enabled;
    private final boolean active;

    public Adapter(
      @JsonProperty("id") String id,
      @JsonProperty("enabled") boolean enabled,
      @JsonProperty("active") boolean active
    ) {
      this.id = id;
      this.enabled = enabled;
      this.active = active;
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public boolean isActive() { return active; }
  }
}
