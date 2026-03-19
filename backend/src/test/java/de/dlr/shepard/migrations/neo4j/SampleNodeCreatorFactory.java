package de.dlr.shepard.migrations.neo4j;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SampleNodeCreatorFactory {

  private final String randomElement;

  public SampleNodeCreator instance(String suffix) {
    return new SampleNodeCreator(suffix, randomElement);
  }
}
