package de.dlr.shepard.v2.containers.handlers;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import jakarta.ws.rs.BadRequestException;
import java.util.Map;

/**
 * V2CONV-A3 — shared create/patch field handling for the in-tree
 * {@link ContainerKindHandler} implementations. Keeps the mutable-field set
 * (today {@code name} and {@code status}) in one place so file / timeseries /
 * structured-data handlers stay byte-identical.
 */
final class ContainerPatchSupport {

  private ContainerPatchSupport() {}

  /**
   * Extract the required {@code name} from a create body.
   *
   * @throws BadRequestException when {@code name} is absent or blank.
   */
  static String requireName(Map<String, Object> body) {
    Object n = body == null ? null : body.get("name");
    if (!(n instanceof String s) || s.isBlank()) {
      throw new BadRequestException("create body must carry a non-blank 'name'");
    }
    return s;
  }

  /**
   * Apply the RFC 7396 merge-patch mutable fields ({@code name}, {@code status})
   * to a container entity. Keys absent from the patch are left unchanged. A
   * present-but-explicit-null {@code name} is rejected (name is required); an
   * explicit-null {@code status} clears the status.
   *
   * @return true when a field actually changed (so the caller persists + audits).
   */
  static boolean applyMutableFields(BasicContainer c, Map<String, Object> patch) {
    if (patch == null || patch.isEmpty()) return false;
    boolean changed = false;
    if (patch.containsKey("name")) {
      Object v = patch.get("name");
      if (!(v instanceof String s) || s.isBlank()) {
        throw new BadRequestException("'name' must be a non-blank string");
      }
      if (!s.equals(c.getName())) {
        c.setName(s);
        changed = true;
      }
    }
    if (patch.containsKey("status")) {
      Object v = patch.get("status");
      String s = v == null ? null : v.toString();
      if (s != null && !isValidStatus(s)) {
        throw new BadRequestException(
          "'status' must be one of DRAFT, IN_REVIEW, READY, PUBLISHED, ARCHIVED (or null)"
        );
      }
      if (!java.util.Objects.equals(s, c.getStatus())) {
        c.setStatus(s);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean isValidStatus(String s) {
    return switch (s) {
      case "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED" -> true;
      default -> false;
    };
  }
}
