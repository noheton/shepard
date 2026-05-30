"""URDF loader -- thin wrapper over :class:`ikpy.chain.Chain`.

Responsibilities:

1. Construct an ``ikpy.chain.Chain`` from a URDF file path, with
   optional explicit ``base_elements`` / ``last_link_vector``
   overrides (used by KR210 + multi-tip URDFs where ikpy's
   auto-detect picks the wrong tip).
2. Surface the **movable-joint indices** -- the positions in the
   chain's joints array where the underlying URDF joint is NOT
   ``fixed``. The sidecar (-04) uses this to know which channels to
   emit in the joint trajectory; without it, the sidecar would emit
   a column of zeros for every fixed link.

We do not cache the chain at module scope. Per the design doc
(aidocs/117 hard guards) chain lifecycle is the sidecar's job;
:class:`IkSolver` builds one per instance.
"""

from __future__ import annotations

from typing import List, Optional

from ikpy.chain import Chain


class UrdfLoader:
    """Stateless helper. All methods are static; the class exists
    only so the public surface is a single import."""

    @staticmethod
    def load(
        urdf_path: str,
        base_elements: Optional[List[str]] = None,
        active_links_mask: Optional[List[bool]] = None,
    ) -> Chain:
        """Build an :class:`ikpy.chain.Chain` from a URDF file.

        Parameters
        ----------
        urdf_path
            Filesystem path to the URDF XML file.
        base_elements
            Optional explicit list of URDF link names to treat as the
            chain root. ikpy auto-detects in most cases; KUKA cell
            URDFs with multiple top-level links (mount + fixture)
            usually need this set explicitly to e.g.
            ``["base_link"]``.
        active_links_mask
            Optional mask over the chain links; when set, takes
            precedence over the movable-joint mask computed from the
            URDF joint types. Use only when a caller explicitly wants
            to freeze a movable joint (rare).
        """
        kwargs = {}
        if base_elements is not None:
            kwargs["base_elements"] = base_elements
        if active_links_mask is not None:
            kwargs["active_links_mask"] = active_links_mask
        return Chain.from_urdf_file(urdf_path, **kwargs)

    @staticmethod
    def movable_joint_indices(chain: Chain) -> List[int]:
        """Indices in ``chain.links`` whose URDF joint type is not ``fixed``.

        ikpy maps URDF ``fixed`` joints to ``ikpy.link.URDFLink``
        instances flagged via the ``joint_type == 'fixed'`` attribute.
        Synthetic ``OriginLink`` instances (ikpy's auto-inserted base
        link) are also non-movable. This method returns the
        complement -- the channels the trajectory should populate.
        """
        movable: List[int] = []
        for i, link in enumerate(chain.links):
            joint_type = getattr(link, "joint_type", None)
            # OriginLink has no joint_type attribute; treat as fixed.
            if joint_type is None or joint_type == "fixed":
                continue
            movable.append(i)
        return movable

    @staticmethod
    def movable_joint_names(chain: Chain) -> List[str]:
        """The URDF joint names corresponding to
        :meth:`movable_joint_indices`. Used by the sidecar to label
        emitted TS channels per the
        ``urn:shepard:urdf:joint:<name>`` annotation preselection
        principle."""
        return [chain.links[i].name for i in UrdfLoader.movable_joint_indices(chain)]
