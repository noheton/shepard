package de.dlr.shepard.template.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flattens a {@link ShepardTemplate}'s inheritance chain
 * ({@code parentTemplateAppId}) into a single effective definition, per
 * {@code aidocs/integrations/123}.
 *
 * <p>Resolution order is <strong>parent first, child overrides</strong>: the
 * resolver deep-merges the parent's flattened body into a result, then overlays
 * the child's own body on top. On key collision the <strong>child wins</strong>
 * (last-writer-wins). This mirrors JSON-Schema {@code allOf}-with-override / XSD
 * restriction-on-extension semantics. {@code iconKey} is inherited when the
 * child's value is {@code null}.
 *
 * <p>Merge rules (per {@code aidocs/123 §2}): objects merge recursively; arrays
 * and scalars the child supplies replace the parent's; the optional
 * {@code shapeGraph} Turtle string is concatenated (parent ahead of child —
 * {@code sh:and} over the two graphs) rather than overwritten.
 *
 * <p>Cycle defence is two-layered: write-time guards in {@code ShepardTemplateRest}
 * reject cyclic edges; {@link #resolveChain} also carries a {@code visited} set so
 * a cycle that slips through aborts fail-soft (logged WARN, partial flatten) rather
 * than looping forever.
 */
@RequestScoped
public class TemplateInheritanceResolver {

  @Inject
  ShepardTemplateDAO dao;

  private final ObjectMapper mapper = new ObjectMapper();

  /** CDI proxy ctor. */
  public TemplateInheritanceResolver() {}

  /** Test ctor — inject a DAO directly. */
  public TemplateInheritanceResolver(ShepardTemplateDAO dao) {
    this.dao = dao;
  }

  /**
   * Return the effective (flattened) JSON-DSL body, deep-merging the parent
   * chain with child-override semantics. No parent → body returned unchanged.
   */
  public String flattenBody(ShepardTemplate template) {
    if (template == null) return null;
    List<ShepardTemplate> chain = resolveChain(template);
    ObjectNode merged = mapper.createObjectNode();
    for (ShepardTemplate t : chain) {
      JsonNode body = parse(t.getBody());
      if (body != null && body.isObject()) {
        deepMerge(merged, (ObjectNode) body);
      }
    }
    return merged.toString();
  }

  /**
   * Effective {@code iconKey}: the child's when non-null, otherwise the nearest
   * non-null ancestor's, otherwise {@code null}.
   */
  public String flattenIconKey(ShepardTemplate template) {
    if (template == null) return null;
    String icon = null;
    for (ShepardTemplate t : resolveChain(template)) {
      if (t.getIconKey() != null && !t.getIconKey().isBlank()) icon = t.getIconKey();
    }
    return icon;
  }

  /**
   * Resolve the inheritance chain root-first → child-last. Cycle-safe via a
   * {@code visited} set: the first repeated appId aborts the walk (logged WARN).
   */
  public List<ShepardTemplate> resolveChain(ShepardTemplate child) {
    List<ShepardTemplate> reverse = new ArrayList<>();
    Set<String> visited = new LinkedHashSet<>();
    ShepardTemplate cur = child;
    while (cur != null) {
      String key = cur.getAppId();
      if (key != null && !visited.add(key)) {
        Log.warnf("Template inheritance cycle detected at appId=%s; aborting flatten (partial chain).", key);
        break;
      }
      reverse.add(cur);
      String parentAppId = cur.getParentTemplateAppId();
      if (parentAppId == null || parentAppId.isBlank()) break;
      Optional<ShepardTemplate> parent = dao.findByAppId(parentAppId);
      if (parent.isEmpty()) {
        Log.warnf("Template %s references missing parent %s; treating as root.", key, parentAppId);
        break;
      }
      cur = parent.get();
    }
    List<ShepardTemplate> out = new ArrayList<>(reverse);
    Collections.reverse(out);
    return out;
  }

  /**
   * True when {@code candidateParentAppId} is the template itself or appears in
   * the proposed parent's ancestor chain — setting it would form a cycle.
   */
  public boolean wouldCreateCycle(String selfAppId, String candidateParentAppId) {
    if (candidateParentAppId == null || candidateParentAppId.isBlank()) return false;
    if (candidateParentAppId.equals(selfAppId)) return true;
    Set<String> visited = new LinkedHashSet<>();
    String cur = candidateParentAppId;
    while (cur != null && !cur.isBlank()) {
      if (cur.equals(selfAppId)) return true;
      if (!visited.add(cur)) return true;
      Optional<ShepardTemplate> p = dao.findByAppId(cur);
      if (p.isEmpty()) break;
      cur = p.get().getParentTemplateAppId();
    }
    return false;
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) return null;
    try {
      return mapper.readTree(body);
    } catch (Exception e) {
      Log.warnf("Could not parse template body during inheritance flatten: %s", e.getMessage());
      return null;
    }
  }

  private void deepMerge(ObjectNode base, ObjectNode overlay) {
    var fields = overlay.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      String key = entry.getKey();
      JsonNode overlayVal = entry.getValue();
      JsonNode baseVal = base.get(key);
      if ("shapeGraph".equals(key) && baseVal != null && baseVal.isTextual() && overlayVal.isTextual()) {
        base.put(key, baseVal.textValue() + "\n" + overlayVal.textValue());
      } else if (baseVal != null && baseVal.isObject() && overlayVal.isObject()) {
        deepMerge((ObjectNode) baseVal, (ObjectNode) overlayVal);
      } else {
        base.set(key, overlayVal.deepCopy());
      }
    }
  }
}
