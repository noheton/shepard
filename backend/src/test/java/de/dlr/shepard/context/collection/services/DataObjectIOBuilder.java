package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.context.collection.io.DataObjectIO;
import java.util.Map;

public class DataObjectIOBuilder {

  private DataObjectIO dataObjectIO;

  public DataObjectIOBuilder() {
    this.dataObjectIO = new DataObjectIO();
    dataObjectIO.setName("Default name");
  }

  public DataObjectIOBuilder setName(String name) {
    dataObjectIO.setName(name);
    return this;
  }

  public DataObjectIOBuilder setPredecessorIds(long[] predecessorIds) {
    dataObjectIO.setPredecessorIds(predecessorIds);
    return this;
  }

  public DataObjectIOBuilder setParentId(long parentId) {
    dataObjectIO.setParentId(parentId);
    return this;
  }

  public DataObjectIOBuilder setAttributes(Map<String, String> attributes) {
    dataObjectIO.setAttributes(attributes);
    return this;
  }

  public DataObjectIO build() {
    return this.dataObjectIO;
  }
}
