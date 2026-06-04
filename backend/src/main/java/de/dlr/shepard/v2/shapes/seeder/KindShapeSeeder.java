package de.dlr.shepard.v2.shapes.seeder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.ServiceLoader;

/**
 * V2CONV-B7 — at startup, iterates every {@link PayloadKind} registered via
 * {@link ServiceLoader} and, for those whose {@link PayloadKind#shapeDescriptor()}
 * returns a non-null {@link ShapeSpec}, seeds (or idempotently updates) a
 * {@code ShepardTemplate (DATAOBJECT_RECIPE)} in Neo4j.
 *
 * <h3>Template shape</h3>
 * <ul>
 *   <li>{@code name} — {@code "<kind.name()>-data-shape"}</li>
 *   <li>{@code templateKind} — {@code "DATAOBJECT_RECIPE"}</li>
 *   <li>{@code body} — {@code {"dataObject": {"shapeSpec": <ShapeBuildRequestIO JSON>}}}</li>
 *   <li>{@code tags} — includes the sentinel {@value #SYSTEM_TAG} so the seeder can
 *       recognise system-managed rows and skip admin-customised ones</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * On each startup the seeder compares the newly computed {@code shapeSpec} JSON
 * against the value stored in the existing template's body. If they are equal,
 * no write occurs. If they differ <em>and</em> the existing template carries
 * {@value #SYSTEM_TAG}, a copy-on-write new version is minted (the prior is
 * retired). If they differ but the system tag is absent, the admin has customised
 * the template — the seeder skips the update and logs a warning.
 *
 * <h3>CDI context</h3>
 * {@link ShepardTemplateDAO} is {@code @RequestScoped}. This {@code @ApplicationScoped}
 * observer activates a request context via {@link RequestContextController} around
 * each DAO call (the same pattern used by {@code DbConnectivityWarmer}).
 *
 * <h3>Fail-soft</h3>
 * Per the "secondary writes are fire-and-forget" rule, any exception during seeding
 * is caught, logged as a warning, and does not abort startup.
 */
@ApplicationScoped
public class KindShapeSeeder {

  /** Tag applied to system-seeded templates. Absence means admin has customised the row. */
  static final String SYSTEM_TAG = "system:kind-shape-seeder";

  static final String TEMPLATE_KIND = "DATAOBJECT_RECIPE";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  ShepardTemplateDAO templateDAO;

  @Inject
  RequestContextController requestContextController;

  /**
   * Observed after {@code ShepardMain.init()} completes — migrations are already applied
   * and Neo4j is ready.
   */
  void onStart(@Observes StartupEvent event) {
    ServiceLoader<PayloadKind> kinds = ServiceLoader.load(
      PayloadKind.class,
      Thread.currentThread().getContextClassLoader()
    );
    for (PayloadKind kind : kinds) {
      ShapeSpec spec;
      try {
        spec = kind.shapeDescriptor();
      } catch (Exception e) {
        Log.warnf(e, "KindShapeSeeder: shapeDescriptor() threw for kind '%s' — skipping", kind.name());
        continue;
      }
      if (spec == null) {
        Log.debugf("KindShapeSeeder: kind '%s' returned null shapeDescriptor — skipping", kind.name());
        continue;
      }
      try {
        seedKind(kind.name(), spec);
      } catch (Exception e) {
        Log.warnf(e, "KindShapeSeeder: failed to seed template for kind '%s' — startup continues", kind.name());
      }
    }
  }

  /**
   * Seeds or idempotently updates the template for one kind.
   * Package-visible for testing.
   */
  void seedKind(String kindName, ShapeSpec spec) {
    String templateName = kindName + "-data-shape";
    String newShapeSpecJson = serializeShapeSpec(spec);

    boolean ctxActivated = false;
    try {
      ctxActivated = requestContextController.activate();
    } catch (Exception e) {
      Log.debugf(e, "KindShapeSeeder: could not activate request context for '%s'", kindName);
    }
    try {
      var existing = templateDAO.findLatestByName(templateName, TEMPLATE_KIND);
      if (existing.isEmpty()) {
        // First time — mint and save.
        ShepardTemplate t = buildTemplate(templateName, newShapeSpecJson);
        templateDAO.createOrUpdate(t);
        Log.infof("KindShapeSeeder: seeded new template '%s' (kind=%s)", templateName, kindName);
      } else {
        ShepardTemplate prior = existing.get();
        String priorShapeSpecJson = extractShapeSpecJson(prior.getBody());
        if (newShapeSpecJson.equals(priorShapeSpecJson)) {
          Log.debugf("KindShapeSeeder: template '%s' is up-to-date — no write", templateName);
          return;
        }
        // Shape changed — only update if the system tag is present.
        if (prior.getTags() == null || !prior.getTags().contains(SYSTEM_TAG)) {
          Log.warnf(
            "KindShapeSeeder: template '%s' has been admin-customised (system tag absent) — skipping update",
            templateName
          );
          return;
        }
        // Copy-on-write: retire prior, mint new version.
        prior.setRetired(true);
        templateDAO.createOrUpdate(prior);

        ShepardTemplate next = templateDAO.nextVersionOf(prior);
        next.setBody(buildBody(newShapeSpecJson));
        templateDAO.createOrUpdate(next);
        Log.infof(
          "KindShapeSeeder: updated template '%s' to v%d (kind=%s)",
          templateName, next.getVersion(), kindName
        );
      }
    } finally {
      if (ctxActivated) {
        try {
          requestContextController.deactivate();
        } catch (Exception e) {
          Log.debugf(e, "KindShapeSeeder: failed to deactivate request context for '%s'", kindName);
        }
      }
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private ShepardTemplate buildTemplate(String name, String shapeSpecJson) {
    ShepardTemplate t = new ShepardTemplate(name, TEMPLATE_KIND, buildBody(shapeSpecJson));
    t.setTags(new ArrayList<>());
    t.getTags().add(SYSTEM_TAG);
    t.setDescription("Auto-seeded SHACL data-shape for payload kind '" + extractKindName(name) + "'.");
    return t;
  }

  /** Builds the {@code {"dataObject": {"shapeSpec": ...}}} wrapper body. */
  static String buildBody(String shapeSpecJson) {
    try {
      ObjectNode root = MAPPER.createObjectNode();
      ObjectNode dataObject = root.putObject("dataObject");
      dataObject.set("shapeSpec", MAPPER.readTree(shapeSpecJson));
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("KindShapeSeeder: failed to build body JSON", e);
    }
  }

  /**
   * Serialise a {@link ShapeSpec} to the {@code ShapeBuildRequestIO} JSON format
   * (mirrors the wire shape without importing the IO class to avoid a cross-package
   * coupling from the seeder to the REST io layer).
   */
  static String serializeShapeSpec(ShapeSpec spec) {
    try {
      ObjectNode node = MAPPER.createObjectNode();
      if (spec.shapeIri() != null) node.put("shapeIri", spec.shapeIri());
      if (spec.targetClass() != null) node.put("targetClass", spec.targetClass());
      node.put("closed", spec.closed());
      if (spec.properties() != null && !spec.properties().isEmpty()) {
        ArrayNode props = node.putArray("properties");
        for (PropertyShapeSpec p : spec.properties()) {
          ObjectNode pn = props.addObject();
          pn.put("path", p.path());
          if (p.datatype() != null) pn.put("datatype", p.datatype());
          if (p.minCount() != null) pn.put("minCount", p.minCount());
          if (p.maxCount() != null) pn.put("maxCount", p.maxCount());
          if (p.node() != null) pn.put("node", p.node());
          if (p.in() != null && !p.in().isEmpty()) {
            ArrayNode inArr = pn.putArray("in");
            for (InMember m : p.in()) {
              ObjectNode mn = inArr.addObject();
              mn.put("value", m.value());
              mn.put("kind", m.kind() == null ? "LITERAL" : m.kind().name());
              if (m.datatype() != null) mn.put("datatype", m.datatype());
            }
          }
        }
      }
      return MAPPER.writeValueAsString(node);
    } catch (Exception e) {
      throw new IllegalStateException("KindShapeSeeder: failed to serialize ShapeSpec to JSON", e);
    }
  }

  /**
   * Extract the {@code shapeSpec} JSON string from a body of the form
   * {@code {"dataObject": {"shapeSpec": {...}}}}. Returns empty-object
   * JSON {@code "{}"} when the path is absent (safe no-match).
   */
  private static String extractShapeSpecJson(String body) {
    if (body == null || body.isBlank()) return "{}";
    try {
      var root = MAPPER.readTree(body);
      var shapeSpec = root.path("dataObject").path("shapeSpec");
      if (shapeSpec.isMissingNode() || shapeSpec.isNull()) return "{}";
      return MAPPER.writeValueAsString(shapeSpec);
    } catch (Exception e) {
      return "{}";
    }
  }

  private static String extractKindName(String templateName) {
    // templateName is "<kindName>-data-shape"
    if (templateName.endsWith("-data-shape")) {
      return templateName.substring(0, templateName.length() - "-data-shape".length());
    }
    return templateName;
  }
}
