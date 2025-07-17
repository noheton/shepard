export function valsToNeo4jImg(fcid: number, oid: string, alt: string): string {
  return `[[image fcid="${fcid}" oid="${oid}" alt="${alt}"]]`;
}

export const db_img_regex = () =>
  /\[\[image fcid="(.*?)" oid="(.*?)" alt="(.*?)"]]/g;
