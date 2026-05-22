---
layout: default
title: Bibliography
description: Citable sources used in Shepard's design and documentation
hero: true
hero_eyebrow: Provenance
hero_title: Bibliography
hero_lede: The citable evidence trail behind every design decision.
---

This is the bibliography of standards, regulations, papers, software, and
ontologies cited across Shepard's documentation and design notes. It is
auto-generated from a canonical BibTeX file
([`docs/_data/references.bib`]({{ site.github.repository_url | default: 'https://github.com/noheton/shepard' }}/blob/main/docs/_data/references.bib)),
which downstream tools (Zotero, Mendeley, LaTeX) can ingest directly.

**Maintenance rule.** Every `aidocs/*` document that cites a paper, standard,
or piece of software adds an entry to `docs/_data/references.bib` in the same
pull request. The bibliography is the single source of truth for citations.
See [CLAUDE.md](https://github.com/noheton/shepard/blob/main/CLAUDE.md) for
the durable rule.

This site also publishes a [`CITATION.cff`](https://github.com/noheton/shepard/blob/main/CITATION.cff)
at the repository root, which GitHub uses to power the "Cite this repository"
button. Use that for citing Shepard itself.

{% assign category_order = "standards,regulations,policy,papers,ontologies,software,dlr_internal" | split: "," %}
{% assign category_labels = "Standards,Regulations,Policy reports & position statements,Papers & pre-prints,Ontologies & vocabularies,Software,DLR-internal sources" | split: "," %}

<nav class="toc" aria-label="Bibliography sections">
  <strong>Sections</strong>
  <ul>
  {% for cat in category_order %}
    {% assign idx = forloop.index0 %}
    {% assign label = category_labels[idx] %}
    <li><a href="#{{ cat | replace: '_', '-' }}">{{ label }}</a></li>
  {% endfor %}
  </ul>
</nav>

{% for cat in category_order %}
  {% assign idx = forloop.index0 %}
  {% assign label = category_labels[idx] %}

## {{ label }}
{: #{{ cat | replace: '_', '-' }} }

<ul class="biblio">
{% for pair in site.data.references %}
  {% assign key = pair[0] %}
  {% assign ref = pair[1] %}
  {% if ref.category == cat %}
  <li id="ref-{{ key }}" class="biblio-entry">
    <span class="biblio-cite">
      {% if ref.author %}{{ ref.author }}{% if ref.year %} ({{ ref.year }}){% endif %}.{% endif %}
      {% if ref.title %}
        {% if ref.url %}<a href="{{ ref.url }}"><em>{{ ref.title }}</em></a>{% else %}<em>{{ ref.title }}</em>{% endif %}.
      {% endif %}
      {% if ref.journal %}{{ ref.journal }}{% if ref.volume %}, {{ ref.volume }}{% endif %}{% if ref.number %}({{ ref.number }}){% endif %}{% if ref.pages %}, {{ ref.pages }}{% endif %}.{% endif %}
      {% if ref.institution %}{{ ref.institution }}.{% endif %}
      {% if ref.version %}Version&nbsp;{{ ref.version }}.{% endif %}
      {% if ref.doi %} DOI: <a href="https://doi.org/{{ ref.doi }}">{{ ref.doi }}</a>.{% endif %}
      {% if ref.eprint %} arXiv: <a href="https://arxiv.org/abs/{{ ref.eprint }}">{{ ref.eprint }}</a>.{% endif %}
      {% if ref.type and ref.entry_type == "techreport" %} <span class="biblio-meta">{{ ref.type }}</span>{% endif %}
    </span>
    {% if ref.note %}<br><span class="biblio-note">{{ ref.note }}</span>{% endif %}
    <br><span class="biblio-key">Citation key: <code>{{ key }}</code></span>
  </li>
  {% endif %}
{% endfor %}
</ul>

{% endfor %}

## How to cite

* **In a docs/* page:** link to the anchor on this page using the citation
  key, e.g. `[Wilkinson et al. (2016)](/bibliography#ref-wilkinsonFair2016)`.
* **In an aidocs/* note:** add a footnote-style reference using the citation
  key and add an entry to `references.bib` if it isn't there yet.
* **In a downstream LaTeX document:** clone the repo and run
  `\bibliography{docs/_data/references}` — the keys are stable.
* **Citing Shepard itself:** use the `CITATION.cff` at the repo root.
  GitHub's "Cite this repository" UI consumes it automatically.

Snapshot date: {{ site.snapshot_date }}.
