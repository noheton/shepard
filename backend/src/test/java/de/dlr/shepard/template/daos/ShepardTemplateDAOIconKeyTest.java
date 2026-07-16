package de.dlr.shepard.template.daos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.dlr.shepard.template.entities.ShepardTemplate;
import org.junit.jupiter.api.Test;

/**
 * TEMPLATE-ICONS-1 — verify ShepardTemplateDAO.nextVersionOf() copies
 * iconKey through on the copy-on-write path. The COW path is the
 * canonical edit shape per aidocs/54 §7; without an explicit
 * pass-through, an admin editing a description would silently strip
 * the icon — the failure mode aidocs/integrations/122 §5.1 calls out.
 */
class ShepardTemplateDAOIconKeyTest {

  @Test
  void nextVersionOfPreservesIconKeyOnCopyOnWrite() {
    ShepardTemplate prior = new ShepardTemplate("MFFD AFP Layup", "DATAOBJECT_RECIPE", "{}");
    prior.setAppId("prior-appid");
    prior.setVersion(1);
    prior.setIconKey("mdi-layers");

    ShepardTemplateDAO dao = new ShepardTemplateDAO();
    ShepardTemplate next = dao.nextVersionOf(prior);

    assertEquals("mdi-layers", next.getIconKey(), "iconKey must propagate to the new version");
    assertEquals(Integer.valueOf(2), next.getVersion());
  }

  @Test
  void nextVersionOfHandlesNullIconKey() {
    ShepardTemplate prior = new ShepardTemplate("Untagged", "DATAOBJECT_RECIPE", "{}");
    prior.setVersion(1);
    // iconKey deliberately null — the per-kind default takes over in the UI.

    ShepardTemplateDAO dao = new ShepardTemplateDAO();
    ShepardTemplate next = dao.nextVersionOf(prior);

    assertNull(next.getIconKey());
  }

  @Test
  void deprecatedListDistinctTagsDelegatesTo500Limit() {
    // The deprecated listDistinctTags(kind) must delegate to listDistinctTagsPaged(kind, 0, 500).
    // Guard that the fallback cap has not silently changed.
    int[] capturedPageSize = {-1};
    ShepardTemplateDAO dao = new ShepardTemplateDAO() {
      @Override
      public java.util.List<String> listDistinctTagsPaged(String templateKind, int page, int pageSize) {
        capturedPageSize[0] = pageSize;
        return java.util.Collections.emptyList();
      }
    };
    dao.listDistinctTags(null);
    assertEquals(500, capturedPageSize[0]);
  }
}
