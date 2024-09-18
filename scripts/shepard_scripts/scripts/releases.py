import re
from collections import defaultdict
from typing import Any

import click
import jinja2
from gitlab import GitlabHttpError, GitlabParsingError
from gitlab.client import Gitlab
from gitlab.v4.objects import (
    Issue,
    MergeRequest,
    Project,
    ProjectMilestone,
    ProjectRelease,
)

BREAKING_CHANGE_LABEL = "Breaking Change"
REFACTORING_LABEL = "issue::refactoring"
DEPENDENCY_BUMP_LABEL = "dependencies"
TEMPLATE_FILE = "templates/release_notes.md"
MAIN_BRANCH = "main"
DEV_BRANCH = "develop"
SEMVER_PATTERN = r"^(?P<major>\d+)\.(?P<minor>\d+)\.(?P<patch>\d+)$"

DEPENDENCY_ISSUE_DESCRIPTION = (
    "After performing the release we need to update all dependencies. "
    "The person that performed the release should also perform the dependency updates. "
    "You can read more about dependency updates in our "
    "[documentation](https://gitlab.com/dlr-shepard/shepard/-/blob/main/architecture/shepard-architectural-documentation.adoc?ref_type=heads#dependency-updates)"
    "."
)


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
        target_branch=DEV_BRANCH,
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


def get_project(gitlab_instance: str, token: str, project_id: int) -> Project:
    instance = Gitlab(gitlab_instance, token)
    try:
        return instance.projects.get(project_id)
    except KeyError as ex:
        raise click.Abort(f"Project {ex} could not be found") from ex


def get_user_id(gitlab_instance: str, token: str) -> int:
    instance = Gitlab(gitlab_instance, token)
    try:
        # instance.user is None, so we have to fall back to the low-level api
        current_user: dict[str, Any] = instance.http_get("/user")  # type: ignore
        if "id" in current_user:
            return current_user["id"]
        raise click.Abort("Current user has no id")
    except (GitlabHttpError, GitlabParsingError) as ex:
        raise click.Abort("Could not fetch current user") from ex


def _get_latest_release(project: Project) -> tuple[str, str]:
    """Get latests release date and tag.
    ---
    Returns:
    Tuple[str, str] : date, tag
    """
    releases: list[ProjectRelease] = project.releases.list(per_page=1, page=0)  # type: ignore
    if not releases:
        return ("2001-01-01T00:00:00.000Z", "0.0.0")
    else:
        return (releases[0].released_at, releases[0].tag_name)


def _get_current_milestone(project: Project) -> ProjectMilestone:
    # Find current active milestone
    milestones: list[ProjectMilestone] = project.milestones.list(
        all=True, state="active"
    )  # type: ignore

    # Filter expired milestones
    milestones = [ms for ms in milestones if ms.expired is False]

    if not milestones:
        raise click.Abort("Could not fetch milestones from gitlab")

    return milestones[0]


def _get_dependency_dashboard(project: Project) -> Issue:
    issues: list[Issue] = project.issues.list(
        all=True, state="opened", search="Dependency Dashboard"
    )  # type: ignore

    if not issues:
        raise click.Abort("Could not fetch issues from gitlab")

    return issues[0]


def _generate_next_tag(latest_release_tag, has_breaking_changes: bool) -> str:
    match = re.match(SEMVER_PATTERN, latest_release_tag)
    version_dict = defaultdict(int)
    if match:
        version_dict["major"] = int(match.group("major"))
        version_dict["minor"] = int(match.group("minor"))
        version_dict["patch"] = int(match.group("patch"))
    else:
        click.echo(
            "The last version is not SEMVER, switching to SEMVER starting at 1.0.0"
        )
        return "1.0.0"

    release_type = "patch"
    if has_breaking_changes:
        click.echo(
            "The release contains breaking changes!"
            + " Automatically increasing 'major' version."
        )
        release_type = "major"
    else:
        click.echo(f"The current version tag is {latest_release_tag}.")
        release_type = click.prompt(
            "What is the next release type?",
            type=click.Choice(["patch", "minor", "major"]),
            default="patch",
        )

    # increase release number according to release type
    version_dict[release_type] += 1
    return (
        f"{version_dict['major']}.{version_dict['minor']}.{version_dict['patch']}"
        + "-release"
    )


def _generate_release_notes(
    project: Project,
    breaking_changes: list[MergeRequest],
    other_changes: list[MergeRequest],
) -> str:
    click.echo({project.name_with_namespace})
    click.echo("Merge Requests:")
    click.echo("\n".join([mr.title for mr in breaking_changes + other_changes]))
    click.echo()

    release_notes = (
        click.edit(
            _get_release_notes(breaking_changes, other_changes), require_save=False
        )
        or ""
    )

    return release_notes


def get_release_details(project: Project) -> tuple[str, str]:
    latest_release_date, latest_release_tag = _get_latest_release(project)

    breaking_changes, _dependencies, other_changes = _get_changes(
        project, latest_release_date
    )
    release_notes = _generate_release_notes(project, breaking_changes, other_changes)
    next_tag = _generate_next_tag(
        latest_release_tag, has_breaking_changes=bool(breaking_changes)
    )

    return (next_tag, release_notes)


def prompt_title(tag: str) -> str:
    release_name = click.prompt("Release name")
    title = f"{tag} {release_name}"
    return title


def prompt_confirm(title: str, tag: str, notes: str):
    click.echo(f"Title: {title}")
    click.echo(f"Tag: {tag}")
    click.echo("Release notes:")
    click.echo(notes)
    click.echo()
    click.confirm("OK?", abort=True)


def create_release(project: Project, title: str, tag: str, notes: str):
    project.releases.create(
        {
            "name": title,
            "tag_name": tag,
            "description": notes,
            "ref": MAIN_BRANCH,
        }
    )
    click.echo("Successfully released")


def create_dependency_issue(project: Project, user_id: int):
    if not click.confirm(
        "Do you want to automatically create a 'Update Dependencies' GitLab issue?"
    ):
        return

    milestone = _get_current_milestone(project)
    dashboard = _get_dependency_dashboard(project)
    description = (
        f"{DEPENDENCY_ISSUE_DESCRIPTION}\n\n"
        f"For more information see the Dependency Dashboard: #{dashboard.get_id()}\n\n"
        f"/relate #{dashboard.get_id()}\n"
    )

    project.issues.create(
        {
            "title": "Dependency Updates",
            "description": description,
            "labels": REFACTORING_LABEL,
            "milestone_id": milestone.get_id(),
            "assignee_ids": [user_id],
        }
    )
    click.echo("Successfully created 'Update Dependencies' issue.")
