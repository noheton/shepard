package de.dlr.shepard.common.versionz;

import lombok.Getter;

@Getter
public class VersionzIO {

  private String version;

  public VersionzIO(String version) {
    this.version = version;
  }
}
