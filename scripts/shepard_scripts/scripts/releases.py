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
SEMVER_PATTERN = r"^(?P<major>\d+)\.(?P<minor>\d+)\.(?P<patch>\d+)(?:-(?P<prerelease>[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$"  # noqa: E501

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


def __get_mrs_by_id(
    project: Project, iids: list[int]
) -> tuple[list[MergeRequest], list[MergeRequest], list[MergeRequest]]:
    breaking_changes: list[MergeRequest] = []
    dependency_changes: list[MergeRequest] = []
    other_changes: list[MergeRequest] = []

    merge_requests: list[MergeRequest] = project.mergerequests.list(
        all=True,
        state="merged",
        order_by="updated_at",
        iids=iids,
        target_branch=DEV_BRANCH,
    )  # type: ignore

    if len(merge_requests) == 0:
        raise click.ClickException(
            f"Could not find any Merge Requests with the provided Ids: {iids}. "
            + "Please check that these MRs exist!"
        )

    for merge_request in merge_requests:
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
        raise click.ClickException(f"Project {ex} could not be found") from ex


def get_user_id(gitlab_instance: str, token: str) -> int:
    instance = Gitlab(gitlab_instance, token)
    try:
        # instance.user is None, so we have to fall back to the low-level api
        current_user: dict[str, Any] = instance.http_get("/user")  # type: ignore
        if "id" in current_user:
            return current_user["id"]
        raise click.ClickException("Current user has no id")
    except (GitlabHttpError, GitlabParsingError) as ex:
        raise click.ClickException("Could not fetch current user") from ex


def _get_latest_release(project: Project, since_release: str) -> tuple[str, str]:
    """Get latests release date and tag.
    ---
    Returns:
    Tuple[str, str] : date, tag
    """

    if not since_release:
        releases: list[ProjectRelease] = project.releases.list(per_page=1, page=0)  # type: ignore
        if not releases:
            raise Exception(
                "No past release could be found! "
                + "For this script to work there needs to be an exising release!"
            )
        release = releases[0]
    else:
        try:
            release = project.releases.get(since_release)
        except Exception as e:
            raise Exception(
                f"Release {since_release} could not be found! "
                + "Maybe the release does not exist or it is misspelled?"
            ) from e

    return release.released_at, release.tag_name


def _get_current_milestone(project: Project) -> ProjectMilestone:
    # Find current active milestone
    milestones: list[ProjectMilestone] = project.milestones.list(
        all=True, state="active"
    )  # type: ignore

    # Filter expired milestones
    milestones = [ms for ms in milestones if ms.expired is False]

    if not milestones:
        raise click.ClickException("Could not fetch milestones from gitlab")

    return milestones[0]


def _get_dependency_dashboard(project: Project) -> Issue:
    issues: list[Issue] = project.issues.list(
        all=True, state="opened", search="Dependency Dashboard"
    )  # type: ignore

    if not issues:
        raise click.ClickException("Could not fetch issues from gitlab")

    return issues[0]


def _generate_next_tag(
    latest_release_tag, has_breaking_changes: bool, isHotFixRelease: bool
) -> str:
    match = re.match(SEMVER_PATTERN, latest_release_tag)
    version_dict = defaultdict(int)

    defaultReleaseType = "minor"
    if isHotFixRelease:
        defaultReleaseType = "patch"

    if match:
        version_dict["major"] = int(match.group("major"))
        version_dict["minor"] = int(match.group("minor"))
        version_dict["patch"] = int(match.group("patch"))
    else:
        click.echo(
            "The last version is not SEMVER, switching to SEMVER starting at 1.0.0"
        )
        return "1.0.0"

    release_type = defaultReleaseType
    if has_breaking_changes:
        major = click.confirm(
            "The release contains breaking changes!"
            + " Do you want to perform a 'major' release?"
        )
        release_type = "major" if major else defaultReleaseType
    if release_type != "major":
        click.echo(f"The current version tag is {latest_release_tag}.")
        release_type = click.prompt(
            "What is the next release type?",
            type=click.Choice(["patch", "minor", "major"]),
            default=defaultReleaseType,
        )

    # increase release number according to release type
    version_dict[release_type] += 1

    if release_type == "minor":
        version_dict["patch"] = 0

    if release_type == "major":
        version_dict["patch"] = 0
        version_dict["minor"] = 0

    return (
        f"{version_dict['major']}.{version_dict['minor']}.{version_dict['patch']}"
        + "-release"
    )


def _generate_release_notes(
    project: Project,
    breaking_changes: list[MergeRequest],
    other_changes: list[MergeRequest],
) -> str:
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


def get_release_details(
    project: Project, isHotfixRelease: bool, since_release: str
) -> tuple[str, str]:
    latest_release_date, latest_release_tag = _get_latest_release(
        project, since_release
    )

    breaking_changes: list[MergeRequest] = []
    other_changes: list[MergeRequest] = []

    if isHotfixRelease:
        mr_iids: list[int] = prompt_mr_iids()
        breaking_changes, _dependencies, other_changes = __get_mrs_by_id(
            project, mr_iids
        )
    else:
        breaking_changes, _dependencies, other_changes = _get_changes(
            project, latest_release_date
        )
    release_notes = _generate_release_notes(project, breaking_changes, other_changes)
    next_tag = _generate_next_tag(
        latest_release_tag,
        has_breaking_changes=bool(breaking_changes),
        isHotFixRelease=isHotfixRelease,
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


def prompt_mr_iids() -> list[int]:
    mr_iids_input: str = click.prompt(
        "Enter the IDs of the Merge Requests you want to include in the hotfix release.\n"  # noqa: E501
        + "You can specify single IDs ('412'), or multiple IDs using comma separation "
        + "('412,441,51')\n"
        + "ID(s)",
        type=str,
    )
    id_list: list[int] = []
    if "," in mr_iids_input:
        split_list = mr_iids_input.split(",")
        for mr_id in split_list:
            if mr_id.isdigit():
                id_list.append(int(mr_id))

    elif mr_iids_input.isdigit():
        id_list.append(int(mr_iids_input))
    else:
        raise click.ClickException(
            "Please enter a valid integer ID, or a comma separated list of IDs!"
        )

    return id_list


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
