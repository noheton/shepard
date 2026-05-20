export interface ChannelSearchable {
  measurement?: string | null;
  device?: string | null;
  location?: string | null;
  symbolicName?: string | null;
  field?: string | null;
}

/**
 * Returns true when `item` matches `query` across the five channel
 * dimension fields (case-insensitive substring match). An empty query
 * matches everything.
 */
export function channelMatchesSearch(
  item: ChannelSearchable,
  query: string,
): boolean {
  if (!query) return true;
  const q = query.toLowerCase();
  return [item.measurement, item.device, item.location, item.symbolicName, item.field]
    .some(v => v?.toLowerCase().includes(q));
}

/**
 * Filters items by a set of selected keys.
 * When `selectedKeys` is empty, returns all items (show-all default).
 */
export function filterChannelsBySelection<T>(
  items: T[],
  selectedKeys: Set<string>,
  keyFn: (item: T) => string,
): T[] {
  if (selectedKeys.size === 0) return items;
  return items.filter(item => selectedKeys.has(keyFn(item)));
}
