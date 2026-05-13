package de.dlr.shepard.publish.minter;

/**
 * Operator-readable failure raised by a {@link Minter} implementation
 * when a mint cannot complete (network blip on ePIC, DataCite 5xx,
 * credential expired, malformed prefix). The {@code MockMinter} in
 * KIP1a never throws this — but the contract is part of the SPI from
 * day one so KIP1c (ePIC) and KIP1d (DataCite) plug in without
 * widening the interface.
 *
 * <p>The REST layer maps {@code MinterException} to an RFC 7807
 * problem response (type {@code publish.minter.failed}) — the
 * {@link #getMessage()} is suitable for the {@code detail} field.
 */
public class MinterException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MinterException(String message) {
    super(message);
  }

  public MinterException(String message, Throwable cause) {
    super(message, cause);
  }
}
