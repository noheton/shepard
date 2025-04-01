export const UserRole = {
  manager: "Manager",
  writer: "Writer",
  reader: "Reader",
};

export type UserRole = (typeof UserRole)[keyof typeof UserRole];
