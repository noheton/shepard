package de.dlr.shepard.cli.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class AdminCliExceptionTest {

  @Test
  void singleArgConstructorPreservesMessage() {
    AdminCliException e = new AdminCliException("boom");
    assertThat(e.getMessage()).isEqualTo("boom");
    assertThat(e.getCause()).isNull();
  }

  @Test
  void causeIsPreserved() {
    Throwable root = new IllegalStateException("root");
    AdminCliException e = new AdminCliException("wrapped", root);
    assertThat(e.getMessage()).isEqualTo("wrapped");
    assertThat(e.getCause()).isSameAs(root);
  }
}
