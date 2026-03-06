package de.dlr.shepard.data.migrations.neo4j;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SampleNodeCreatorFactory {

  private final String randomElement;

  public SampleNodeCreator instance(String suffix) {
    return new SampleNodeCreator(suffix, randomElement);
  }
}
