package de.dlr.shepard.v2.admin.hdf.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A5b — JSON response body of {@code POST /v2/admin/hdf/rebuild-acls}.
 *
 * <p>Shape stays plain JSON (not RFC 7807) — the rebuild endpoint can
 * return 200 with partial errors (some containers synced, others
 * didn't). RFC 7807 is reserved for the all-or-nothing 5xx / 4xx
 * paths the {@code HdfAdminRest} class wraps separately.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "HdfRebuildAclsResult")
public class HdfRebuildAclsResultIO {

  @Schema(description = "Number of HdfContainers visited (regardless of outcome).")
  private int containersProcessed;

  @Schema(description = "Number of HdfContainers whose HSDS ACL was successfully (re)written.")
  private int containersSynced;

  @Schema(description = "Per-container error entries; empty when every sync succeeded.")
  private List<Error> errors = new ArrayList<>();

  public HdfRebuildAclsResultIO() {}

  public HdfRebuildAclsResultIO(int containersProcessed, int containersSynced, List<Error> errors) {
    this.containersProcessed = containersProcessed;
    this.containersSynced = containersSynced;
    this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
  }

  public int getContainersProcessed() {
    return containersProcessed;
  }

  public void setContainersProcessed(int containersProcessed) {
    this.containersProcessed = containersProcessed;
  }

  public int getContainersSynced() {
    return containersSynced;
  }

  public void setContainersSynced(int containersSynced) {
    this.containersSynced = containersSynced;
  }

  public List<Error> getErrors() {
    return errors == null ? Collections.emptyList() : errors;
  }

  public void setErrors(List<Error> errors) {
    this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
  }

  @Schema(name = "HdfRebuildAclsResultError")
  public static class Error {

    @Schema(description = "appId of the HdfContainer that failed to sync.")
    private String containerAppId;

    @Schema(description = "Operator-readable reason for the failure.")
    private String reason;

    public Error() {}

    public Error(String containerAppId, String reason) {
      this.containerAppId = containerAppId;
      this.reason = reason;
    }

    public String getContainerAppId() {
      return containerAppId;
    }

    public void setContainerAppId(String containerAppId) {
      this.containerAppId = containerAppId;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }
}
