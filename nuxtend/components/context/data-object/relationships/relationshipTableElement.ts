export type RelationshipTableElement = {
  relationship: string | undefined;
  name: { value: string; path: string };
  type: { value: string; collection?: string };
  created: {
    createdAt: Date;
    createdBy: string;
  };
};
