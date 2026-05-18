export interface TimeseriesSeries {
  name: string;
  data: Array<[number, number]>; // [timestamp_ns, value]
  color?: string;
}
