package de.dlr.shepard.plugins.git;

import de.dlr.shepard.context.references.git.entities.GitReference;
import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

public final class GitPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "git";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(GitReference.class.getPackageName());
  }
}
