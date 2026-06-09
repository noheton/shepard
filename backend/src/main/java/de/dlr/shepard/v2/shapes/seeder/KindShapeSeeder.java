package de.dlr.shepard.v2.shapes.seeder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.spi.payload.PayloadKind;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.shapes.builder.ChannelBindingSpec;
import de.dlr.shepard.v2.shapes.builder.InMember;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ViewRecipeSpec;
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
  static final String VIEW_TEMPLATE_KIND = "VIEW_RECIPE";

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
      // DATAOBJECT_RECIPE — data shape
      ShapeSpec dataSpec;
      try {
        dataSpec = kind.shapeDescriptor();
      } catch (Exception e) {
        Log.warnf(e, "KindShapeSeeder: shapeDescriptor() threw for kind '%s' — skipping", kind.name());
        dataSpec = null;
      }
      if (dataSpec != null) {
        try {
          seedKind(kind.name(), dataSpec);
        } catch (Exception e) {
          Log.warnf(e, "KindShapeSeeder: failed to seed DATAOBJECT_RECIPE for kind '%s' — startup continues", kind.name());
        }
      } else {
        Log.debugf("KindShapeSeeder: kind '%s' returned null shapeDescriptor — skipping DATAOBJECT_RECIPE", kind.name());
      }

      // V2CONV-B8 — VIEW_RECIPE — view shape
      ViewRecipeSpec viewSpec;
      try {
        viewSpec = kind.viewShapeDescriptor();
      } catch (Exception e) {
        Log.warnf(e, "KindShapeSeeder: viewShapeDescriptor() threw for kind '%s' — skipping", kind.name());
        viewSpec = null;
      }
      if (viewSpec != null) {
        try {
          seedViewKind(kind.name(), viewSpec);
        } catch (Exception e) {
          Log.warnf(e, "KindShapeSeeder: failed to seed VIEW_RECIPE for kind '%s' — startup continues", kind.name());
        }
      } else {
        Log.debugf("KindShapeSeeder: kind '%s' returned null viewShapeDescriptor — skipping VIEW_RECIPE", kind.name());
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

  /**
   * V2CONV-B8 — seeds or idempotently updates the VIEW_RECIPE template for one kind.
   * Mirrors {@link #seedKind} but uses {@value #VIEW_TEMPLATE_KIND} and the
   * {@code "<kindName>-view-shape"} naming convention.
   * Package-visible for testing.
   */
  void seedViewKind(String kindName, ViewRecipeSpec spec) {
    String templateName = kindName + "-view-shape";
    String newSpecJson = serializeViewRecipeSpec(spec);

    boolean ctxActivated = false;
    try {
      ctxActivated = requestContextController.activate();
    } catch (Exception e) {
      Log.debugf(e, "KindShapeSeeder: could not activate request context for view kind '%s'", kindName);
    }
    try {
      var existing = templateDAO.findLatestByName(templateName, VIEW_TEMPLATE_KIND);
      if (existing.isEmpty()) {
        ShepardTemplate t = buildViewTemplate(templateName, kindName, newSpecJson);
        templateDAO.createOrUpdate(t);
        Log.infof("KindShapeSeeder: seeded new VIEW_RECIPE template '%s' (kind=%s)", templateName, kindName);
      } else {
        ShepardTemplate prior = existing.get();
        String priorSpecJson = extractViewSpecJson(prior.getBody());
        if (newSpecJson.equals(priorSpecJson)) {
          Log.debugf("KindShapeSeeder: VIEW_RECIPE template '%s' is up-to-date — no write", templateName);
          return;
        }
        if (prior.getTags() == null || !prior.getTags().contains(SYSTEM_TAG)) {
          Log.warnf(
            "KindShapeSeeder: VIEW_RECIPE template '%s' has been admin-customised (system tag absent) — skipping update",
            templateName
          );
          return;
        }
        prior.setRetired(true);
        templateDAO.createOrUpdate(prior);

        ShepardTemplate next = templateDAO.nextVersionOf(prior);
        next.setBody(buildViewBody(newSpecJson));
        templateDAO.createOrUpdate(next);
        Log.infof(
          "KindShapeSeeder: updated VIEW_RECIPE template '%s' to v%d (kind=%s)",
          templateName, next.getVersion(), kindName
        );
      }
    } finally {
      if (ctxActivated) {
        try {
          requestContextController.deactivate();
        } catch (Exception e) {
          Log.debugf(e, "KindShapeSeeder: failed to deactivate request context for view kind '%s'", kindName);
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

  private ShepardTemplate buildViewTemplate(String name, String kindName, String specJson) {
    ShepardTemplate t = new ShepardTemplate(name, VIEW_TEMPLATE_KIND, buildViewBody(specJson));
    t.setTags(new ArrayList<>());
    t.getTags().add(SYSTEM_TAG);
    t.setDescription("Auto-seeded VIEW_RECIPE for payload kind '" + kindName + "'.");
    return t;
  }

  /**
   * Builds the VIEW_RECIPE body JSON from the canonical spec JSON string.
   * The top-level object IS the spec (no wrapper key) — VIEW_RECIPE body
   * passes {@code TemplateBodyValidator} when it contains {@code "renderer"}.
   */
  static String buildViewBody(String specJson) {
    // specJson already encodes the full body: {"renderer":..., ...}
    return specJson;
  }

  /**
   * Serialises a {@link ViewRecipeSpec} to the canonical VIEW_RECIPE body JSON.
   * The result contains at minimum {@code "renderer"} so it satisfies
   * {@code TemplateBodyValidator}.
   */
  static String serializeViewRecipeSpec(ViewRecipeSpec spec) {
    try {
      ObjectNode node = MAPPER.createObjectNode();
      if (spec.renderer() != null) {
        node.put("renderer", spec.renderer());
      } else {
        node.putNull("renderer");
      }
      if (spec.viewRecipeShape() != null) {
        node.put("viewRecipeShape", spec.viewRecipeShape());
      }
      ArrayNode bindings = node.putArray("channelBindings");
      if (spec.channelBindings() != null) {
        for (ChannelBindingSpec b : spec.channelBindings()) {
          ObjectNode bn = bindings.addObject();
          bn.put("role", b.role());
          bn.put("channelSelector", b.channelSelector() != null ? b.channelSelector() : "");
          if (b.unit() != null) bn.put("unit", b.unit());
          bn.put("required", b.required());
        }
      }
      return MAPPER.writeValueAsString(node);
    } catch (Exception e) {
      throw new IllegalStateException("KindShapeSeeder: failed to serialize ViewRecipeSpec to JSON", e);
    }
  }

  /**
   * Extracts the canonical spec JSON from a VIEW_RECIPE body for idempotency comparison.
   * Since VIEW_RECIPE bodies ARE the spec (no wrapper key), the body itself is the
   * comparison token. Returns {@code "{}"} on parse failure or blank input.
   */
  static String extractViewSpecJson(String body) {
    if (body == null || body.isBlank()) return "{}";
    try {
      // Normalise: re-serialise to strip whitespace differences.
      return MAPPER.writeValueAsString(MAPPER.readTree(body));
    } catch (Exception e) {
      return "{}";
    }
  }
}
