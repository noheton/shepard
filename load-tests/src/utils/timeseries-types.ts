export type TimeseriesData = {
  timeseries: TimeseriesObject;
  points: TimeseriesDataPoint[];
};

export type TimeseriesObject = {
  measurement: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
};

export type TimeseriesDataPoint = {
  timestamp: number;
  value: Object;
};
