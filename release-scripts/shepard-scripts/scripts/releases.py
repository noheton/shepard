from datetime import datetime

import click
import jinja2
from gitlab.client import Gitlab
from gitlab.v4.objects.merge_requests import MergeRequest
from gitlab.v4.objects.projects import Project

BREAKING_CHANGE_LABEL = "Breaking Change"
DEPENDENCY_BUMP_LABEL = "dependencies"
TEMPLATE_FILE = "templates/release_notes.md"


def _get_changes(
    project: Project, merged_after: str
) -> tuple[list[MergeRequest], list[MergeRequest], list[MergeRequest]]:
    breaking_changes: list[MergeRequest] = []
    dependency_changes: list[MergeRequest] = []
    other_changes: list[MergeRequest] = []

    merge_requests: list[MergeRequest] = project.mergerequests.list(
        all=True,
        state="merged",
        order_by="updated_at",
        updated_after=merged_after,
        target_branch=project.default_branch,
    )  # type: ignore

    for merge_request in merge_requests:
        if merge_request.merged_at < merged_after:
            continue
        if BREAKING_CHANGE_LABEL in merge_request.labels:
            breaking_changes.append(merge_request)
        elif DEPENDENCY_BUMP_LABEL in merge_request.labels:
            dependency_changes.append(merge_request)
        else:
            other_changes.append(merge_request)

    return (breaking_changes, dependency_changes, other_changes)


def _get_mr_list(merge_requests: list[MergeRequest]) -> str:
    result = ""
    for merge_request in merge_requests:
        if len(result) > 0:
            result = result + "\n"
        result = result + f"- {merge_request.title} ({merge_request.web_url})"
    return result


def _get_release_notes(
    breaking_changes: list[MergeRequest],
    other_changes: list[MergeRequest],
    description: str = "",
) -> str:
    template_loader = jinja2.FileSystemLoader(searchpath="./")
    template_env = jinja2.Environment(loader=template_loader, autoescape=True)
    template = template_env.get_template(TEMPLATE_FILE)
    result = template.render(
        description=description,
        breaking_changes=_get_mr_list(breaking_changes),
        other_changes=_get_mr_list(other_changes),
    )
    return result


def _get_release_tag() -> str:
    now = datetime.now()
    return now.strftime("%Y.%m.%d-release")


def create_release(gitlab_instance: str, token: str, project_id: int):
    instance = Gitlab(gitlab_instance, token)

    try:
        project = instance.projects.get(project_id)
    except KeyError as ex:
        raise click.Abort(f"Project {ex} could not be found") from ex

    latest_release = project.releases.list(per_page=1, page=0)[0]  # type: ignore
    breaking, _dependencies, others = _get_changes(project, latest_release.released_at)
    release_tag = _get_release_tag()

    click.echo({project.name_with_namespace})
    click.echo("Merge Requests:")
    click.echo("\n".join([mr.title for mr in breaking + others]))
    click.echo()

    title = click.prompt("Release title")
    release_notes = click.edit(_get_release_notes(breaking, others), require_save=False)

    click.echo(f"Title: {title}")
    click.echo(f"Tag: {release_tag}")
    click.echo("Release notes:")
    click.echo(release_notes)
    click.echo()
    if not click.confirm("OK?", abort=True):
        raise click.Abort("Not confirmed")

    project.releases.create(
        {
            "name": title,
            "tag_name": release_tag,
            "description": release_notes,
            "ref": project.default_branch,
        }
    )
    click.echo("Successfully released")
