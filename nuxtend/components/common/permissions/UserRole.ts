export const UserRole = {
  manager: "Manager",
  reader: "Reader",
  writer: "Writer",
};

export type UserRole = (typeof UserRole)[keyof typeof UserRole];
