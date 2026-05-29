import type { Member } from "~/composables/common/permissions/useMemberSearch";
import type { UserRole } from "./UserRole";

export interface MemberPermissions {
  member: Member;
  roleList: UserRole[];
}
