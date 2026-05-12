package de.dlr.shepard.auth.users.validation;

/**
 * Validates ORCID identifiers per ISO 7064 mod 11-2 — the checksum
 * scheme defined in the ORCID format specification (16 base-10 digits
 * with an optional {@code X} for value 10 in the check position).
 *
 * <p>Canonical wire shape is four hyphen-separated groups of four
 * digits, e.g. {@code 0000-0002-1825-0097}. The validator accepts
 * exactly that shape — no whitespace, no embedded URI prefix.
 *
 * <p>Per {@code aidocs/16 U1a}, the validator is used by
 * {@code PATCH /v2/users/me} to gate {@code orcid} input before it
 * lands on the {@code User} entity.
 */
public final class OrcidValidator {

  private OrcidValidator() {}

  /**
   * @return {@code true} when {@code orcid} matches
   *         {@code NNNN-NNNN-NNNN-NNN[N|X]} and the final character
   *         satisfies the ISO 7064 mod 11-2 checksum; {@code false}
   *         for null, blank, malformed input, or checksum mismatch.
   */
  public static boolean isValid(String orcid) {
    if (orcid == null) return false;
    // 19 characters: 16 alphanumeric (last is 0-9 or X) + 3 hyphens at positions 4, 9, 14.
    if (orcid.length() != 19) return false;
    if (orcid.charAt(4) != '-' || orcid.charAt(9) != '-' || orcid.charAt(14) != '-') return false;

    char[] digits = new char[16];
    int j = 0;
    for (int i = 0; i < 19; i++) {
      char c = orcid.charAt(i);
      if (i == 4 || i == 9 || i == 14) continue;
      digits[j++] = c;
    }
    // The first 15 positions must be plain digits; only the last may be 'X'.
    for (int i = 0; i < 15; i++) {
      if (digits[i] < '0' || digits[i] > '9') return false;
    }
    char checkChar = digits[15];
    if (!((checkChar >= '0' && checkChar <= '9') || checkChar == 'X')) return false;

    int total = 0;
    for (int i = 0; i < 15; i++) {
      total = (total + (digits[i] - '0')) * 2;
    }
    int remainder = total % 11;
    int expected = (12 - remainder) % 11;
    int actual = checkChar == 'X' ? 10 : (checkChar - '0');
    return expected == actual;
  }
}
