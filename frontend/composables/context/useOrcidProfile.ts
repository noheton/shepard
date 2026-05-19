/**
 * Fetches public ORCID data for a given ORCID iD using the ORCID public API.
 * Returns keywords and recent works (up to 5). No auth required — public profile only.
 */
export interface OrcidWork {
  title: string;
  year: number | null;
  doi: string | null;
  url: string | null;
}

export interface OrcidProfile {
  keywords: string[];
  works: OrcidWork[];
}

export function useOrcidProfile(orcid: Ref<string | null | undefined>) {
  const profile = ref<OrcidProfile | null>(null);
  const loading = ref(false);
  const error = ref(false);

  async function fetch(id: string) {
    loading.value = true;
    error.value = false;
    profile.value = null;
    try {
      const base = `https://pub.orcid.org/v3.0/${encodeURIComponent(id)}`;
      const headers = { Accept: "application/json" };

      const [personRes, worksRes] = await Promise.all([
        globalThis.fetch(`${base}/person`, { headers }),
        globalThis.fetch(`${base}/works`, { headers }),
      ]);

      // keywords
      const keywords: string[] = [];
      if (personRes.ok) {
        const person = await personRes.json();
        const kws: any[] = person?.keywords?.keyword ?? [];
        for (const kw of kws) {
          const val = kw?.content;
          if (val) keywords.push(val);
        }
      }

      // works — sorted newest-first, take top 5
      const works: OrcidWork[] = [];
      if (worksRes.ok) {
        const worksData = await worksRes.json();
        const groups: any[] = worksData?.group ?? [];
        const parsed = groups
          .map((g: any) => {
            const summary = g?.["work-summary"]?.[0];
            const title = summary?.title?.title?.value ?? null;
            const year = summary?.["publication-date"]?.year?.value
              ? parseInt(summary["publication-date"].year.value, 10)
              : null;
            const doi = summary?.["external-ids"]?.["external-id"]?.find(
              (e: any) => e["external-id-type"] === "doi",
            )?.["external-id-value"] ?? null;
            const url = doi
              ? `https://doi.org/${doi}`
              : summary?.url?.value ?? null;
            return { title, year, doi, url } as OrcidWork;
          })
          .filter((w) => w.title)
          .sort((a, b) => (b.year ?? 0) - (a.year ?? 0))
          .slice(0, 5);
        works.push(...parsed);
      }

      profile.value = { keywords, works };
    } catch {
      error.value = true;
    } finally {
      loading.value = false;
    }
  }

  watch(
    orcid,
    (id) => {
      if (id) fetch(id);
      else profile.value = null;
    },
    { immediate: true },
  );

  return { profile, loading, error };
}
