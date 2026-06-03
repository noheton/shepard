package de.dlr.shepard.template.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** TPL-INHERIT — unit coverage for the inheritance flatten + cycle guard (aidocs/123). */
class TemplateInheritanceResolverTest {

  @Mock
  ShepardTemplateDAO dao;

  TemplateInheritanceResolver resolver;
  final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resolver = new TemplateInheritanceResolver(dao);
  }

  private ShepardTemplate tmpl(String appId, String parentAppId, String body) {
    var t = new ShepardTemplate("t-" + appId, "DATAOBJECT_RECIPE", body);
    t.setAppId(appId);
    t.setParentTemplateAppId(parentAppId);
    return t;
  }

  @Test
  void noParentReturnsBodyUnchanged() {
    var t = tmpl("a", null, "{\"x\":1}");
    String out = resolver.flattenBody(t);
    assertTrue(out.contains("\"x\":1"));
  }

  @Test
  void childOverridesParentScalar() throws Exception {
    var parent = tmpl("p", null, "{\"a\":1,\"b\":2}");
    var child = tmpl("c", "p", "{\"b\":99,\"d\":4}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    JsonNode n = mapper.readTree(resolver.flattenBody(child));
    assertEquals(1, n.get("a").asInt()); // inherited
    assertEquals(99, n.get("b").asInt()); // child wins
    assertEquals(4, n.get("d").asInt()); // child only
  }

  @Test
  void nestedObjectsMergeRecursively() throws Exception {
    var parent = tmpl("p", null, "{\"dataobjects\":{\"attributes\":{\"bench\":\"P1\",\"shift\":\"A\"}}}");
    var child = tmpl("c", "p", "{\"dataobjects\":{\"attributes\":{\"shift\":\"B\"}}}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    JsonNode attrs = mapper.readTree(resolver.flattenBody(child)).get("dataobjects").get("attributes");
    assertEquals("P1", attrs.get("bench").asText());
    assertEquals("B", attrs.get("shift").asText());
  }

  @Test
  void arrayOfObjectsMergesPositionally() throws Exception {
    // The canonical dataobjects[0].attributes shape — array, not object.
    var parent = tmpl("p", null, "{\"dataobjects\":[{\"attributes\":{\"bench\":\"P1\",\"shift\":\"A\"}}]}");
    var child = tmpl("c", "p", "{\"dataobjects\":[{\"attributes\":{\"shift\":\"B\"}}]}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    JsonNode attrs = mapper.readTree(resolver.flattenBody(child))
      .get("dataobjects").get(0).get("attributes");
    assertEquals("P1", attrs.get("bench").asText()); // inherited from parent index 0
    assertEquals("B", attrs.get("shift").asText()); // child overrides
  }

  @Test
  void grandparentChainFlattensRootFirst() throws Exception {
    var gp = tmpl("gp", null, "{\"a\":1}");
    var p = tmpl("p", "gp", "{\"b\":2}");
    var c = tmpl("c", "p", "{\"c\":3}");
    when(dao.findByAppId("gp")).thenReturn(Optional.of(gp));
    when(dao.findByAppId("p")).thenReturn(Optional.of(p));
    JsonNode n = mapper.readTree(resolver.flattenBody(c));
    assertEquals(1, n.get("a").asInt());
    assertEquals(2, n.get("b").asInt());
    assertEquals(3, n.get("c").asInt());
  }

  @Test
  void shapeGraphConcatenatesParentAheadOfChild() throws Exception {
    var parent = tmpl("p", null, "{\"shapeGraph\":\"PARENT\"}");
    var child = tmpl("c", "p", "{\"shapeGraph\":\"CHILD\"}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    String merged = mapper.readTree(resolver.flattenBody(child)).get("shapeGraph").asText();
    assertTrue(merged.indexOf("PARENT") < merged.indexOf("CHILD"));
  }

  @Test
  void iconKeyInheritedWhenChildNull() {
    var parent = tmpl("p", null, "{}");
    parent.setIconKey("mdi-layers");
    var child = tmpl("c", "p", "{}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    assertEquals("mdi-layers", resolver.flattenIconKey(child));
  }

  @Test
  void iconKeyChildWinsOverParent() {
    var parent = tmpl("p", null, "{}");
    parent.setIconKey("mdi-layers");
    var child = tmpl("c", "p", "{}");
    child.setIconKey("mdi-flask-outline");
    when(dao.findByAppId("p")).thenReturn(Optional.of(parent));
    assertEquals("mdi-flask-outline", resolver.flattenIconKey(child));
  }

  @Test
  void cycleInChainAbortsFailSoft() {
    // a -> b -> a (cycle); resolver must not loop forever.
    var a = tmpl("a", "b", "{\"x\":1}");
    var b = tmpl("b", "a", "{\"y\":2}");
    when(dao.findByAppId("a")).thenReturn(Optional.of(a));
    when(dao.findByAppId("b")).thenReturn(Optional.of(b));
    String out = resolver.flattenBody(a); // should return, not hang
    assertTrue(out.contains("\"x\":1") || out.contains("\"y\":2"));
  }

  @Test
  void wouldCreateCycleDetectsSelf() {
    assertTrue(resolver.wouldCreateCycle("self", "self"));
  }

  @Test
  void wouldCreateCycleDetectsAncestor() {
    // proposing parent=p for self=gp, where p's ancestor is gp.
    var p = tmpl("p", "gp", "{}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(p));
    assertTrue(resolver.wouldCreateCycle("gp", "p"));
  }

  @Test
  void wouldCreateCycleFalseForUnrelated() {
    var p = tmpl("p", null, "{}");
    when(dao.findByAppId("p")).thenReturn(Optional.of(p));
    assertFalse(resolver.wouldCreateCycle("self", "p"));
  }

  @Test
  void missingParentTreatedAsRoot() {
    var child = tmpl("c", "ghost", "{\"x\":1}");
    when(dao.findByAppId("ghost")).thenReturn(Optional.empty());
    assertTrue(resolver.flattenBody(child).contains("\"x\":1"));
  }
}
