/**
 * Client-side ORCID format + checksum validation.
 *
 * Mirrors `de.dlr.shepard.auth.users.validation.OrcidValidator` (Java)
 * so the profile form can surface inline validation errors without a
 * round-trip. Both sides agree on:
 *
 *   - canonical wire shape: `NNNN-NNNN-NNNN-NNN[N|X]` (19 chars, three
 *     hyphens at positions 4, 9, 14)
 *   - ISO 7064 mod 11-2 checksum, with `X` representing value 10 in the
 *     check position
 *
 * RDM-002: previously the field lived inside an Edit dialog and only the
 * backend validated. Surfacing the field inline on `/me/profile` needs a
 * matching client-side check so a user gets immediate feedback when the
 * checksum doesn't match.
 */
export function isValidOrcid(orcid: string | null | undefined): boolean {
  if (orcid == null) return false;
  if (orcid.length !== 19) return false;
  if (orcid.charAt(4) !== "-" || orcid.charAt(9) !== "-" || orcid.charAt(14) !== "-") {
    return false;
  }

  const digits: string[] = [];
  for (let i = 0; i < 19; i++) {
    if (i === 4 || i === 9 || i === 14) continue;
    digits.push(orcid.charAt(i));
  }

  for (let i = 0; i < 15; i++) {
    const c = digits[i]!;
    if (c < "0" || c > "9") return false;
  }
  const checkChar = digits[15]!;
  if (!((checkChar >= "0" && checkChar <= "9") || checkChar === "X")) return false;

  let total = 0;
  for (let i = 0; i < 15; i++) {
    total = (total + (digits[i]!.charCodeAt(0) - "0".charCodeAt(0))) * 2;
  }
  const remainder = total % 11;
  const expected = (12 - remainder) % 11;
  const actual = checkChar === "X" ? 10 : checkChar.charCodeAt(0) - "0".charCodeAt(0);
  return expected === actual;
}

/**
 * Vuetify-rules-compatible validator for the ORCID `v-text-field`. Empty
 * string is treated as "no ORCID set" (legal — the user can clear the
 * field). Non-empty input must pass {@link isValidOrcid}.
 */
export function orcidVTextFieldRule(value: string | null | undefined): true | string {
  if (value == null || value === "") return true;
  if (isValidOrcid(value)) return true;
  return "Not a valid ORCID — must be NNNN-NNNN-NNNN-NNNX with a matching ISO 7064 mod 11-2 checksum.";
}
