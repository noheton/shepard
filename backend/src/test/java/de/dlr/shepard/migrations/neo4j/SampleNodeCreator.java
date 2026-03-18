package de.dlr.shepard.migrations.neo4j;

import static org.neo4j.cypherdsl.core.Cypher.*;

import lombok.AllArgsConstructor;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Literal;
import org.neo4j.cypherdsl.core.Node;

@AllArgsConstructor
public class SampleNodeCreator {

  private String suffix;
  private String randomElement;

  private String getSuffix() {
    return " " + suffix;
  }

  public Node timeseries() {
    return timeseries("device");
  }

  public Node timeseries(String device) {
    return node("Timeseries").withProperties(
      "measurement",
      literal("measurement" + getSuffix()),
      device,
      literal("device" + getSuffix()),
      "location",
      literal("location" + getSuffix()),
      "symbolicName",
      literal("symbolicName" + getSuffix()),
      "field",
      literal("field" + getSuffix())
    );
  }

  public Node timeseriesReference() {
    return timeseriesReference("Timeseries Reference");
  }

  public Node timeseriesReference(int index) {
    return timeseriesReference("ref" + index);
  }

  Node timeseriesReference(String name) {
    return node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity").withProperties(
      "createdAt",
      Cypher.literalOf(100),
      "deleted",
      Cypher.literalOf(false),
      "end",
      Cypher.literalOf(2000),
      "name",
      literal(name + getSuffix()),
      "shepardId",
      Cypher.literalOf(5),
      "start",
      Cypher.literalOf(1000)
    );
  }

  public Node annotation() {
    return node("SemanticAnnotation").withProperties(
      "propertyName",
      literal("prop" + getSuffix()),
      "valueName",
      literal("value" + getSuffix())
    );
  }

  public Node timeseriesContainer(int index) {
    return timeseriesContainer("TimeseriesContainer " + index);
  }

  public Node timeseriesContainer() {
    return timeseriesContainer("TimeseriesContainer");
  }

  public Node timeseriesContainer(String name) {
    return node("TimeseriesContainer", "BasicEntity", "BasicContainer").withProperties(
      "createdAt",
      Cypher.literalOf(200),
      "deleted",
      Cypher.literalOf(false),
      "name",
      literal(name + getSuffix())
    );
  }

  public Literal<String> literal(String of) {
    return Cypher.literalOf(of + "-" + randomElement);
  }
}
