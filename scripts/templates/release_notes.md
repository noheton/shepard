# shepard Release {{ version }}

We are happy to announce the release of shepard {{ version }}!

{% if breaking_changes %}

## Breaking Changes

{{ breaking_changes }}

## Other Changes

{% else %}
**Detailed changelist:**

{% endif %}

{{ other_changes }}
