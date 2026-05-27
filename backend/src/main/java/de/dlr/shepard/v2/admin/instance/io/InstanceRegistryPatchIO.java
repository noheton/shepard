package de.dlr.shepard.v2.admin.instance.io;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;

/**
 * FE-PROV-INSTANCE-REGISTRY — request body for
 * {@code PATCH /v2/admin/instances} (RFC 7396 merge-patch).
 *
 * <p>RFC 7396 semantics:
 * <ul>
 *   <li>Field absent → leave the current {@code instances} list alone.</li>
 *   <li>Field {@code null} → clear the list (set to empty).</li>
 *   <li>Field present (even {@code []}) → full replace of the list.</li>
 * </ul>
 *
 * <p>Arrays are atomic under RFC 7396 — no element-level merge is performed.
 * The {@code instancesTouched} flag distinguishes "absent" from "explicit
 * null" so the REST layer can apply the correct RFC 7396 action.
 */
public final class InstanceRegistryPatchIO {

  private List<RegisteredInstanceIO> instances;
  private boolean instancesTouched;

  public List<RegisteredInstanceIO> getInstances() {
    return instances;
  }

  /**
   * Jackson invokes this when the JSON body mentions {@code instances} —
   * including the explicit-null case via {@link Nulls#SET}.
   */
  @JsonSetter(nulls = Nulls.SET)
  public void setInstances(List<RegisteredInstanceIO> instances) {
    this.instances = instances;
    this.instancesTouched = true;
  }

  public boolean isInstancesTouched() {
    return instancesTouched;
  }
}
