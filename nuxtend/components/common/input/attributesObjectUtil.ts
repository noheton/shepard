export const getAttributesArrayOfObject = (attributes: {
  [key: string]: string;
}): { key: string; value: string }[] =>
  Object.keys(attributes).map(attr => ({
    key: attr,
    value: attributes[attr] ?? "",
  }));

export const getObjectOfAttributesArray = (
  attrArr: { key: string; value: string }[],
): { [key: string]: string } =>
  attrArr
    .filter(attr => !!attr.key)
    .reduce((prev, curr) => ({ ...prev, [curr.key]: curr.value }), {});
