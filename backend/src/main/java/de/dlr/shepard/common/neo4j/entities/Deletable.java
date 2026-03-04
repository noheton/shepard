package de.dlr.shepard.common.neo4j.entities;

public interface Deletable {
  boolean isDeleted();
  void setDeleted(boolean deleted);
}
