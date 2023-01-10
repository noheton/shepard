export interface DataPoint {
  x: number;
  y: number;
}

export interface Dataset {
  dataPoints: DataPoint[];
  label: string;
}

export interface PlottingData {
  datasets: Dataset[];
  xLabel: string;
}
