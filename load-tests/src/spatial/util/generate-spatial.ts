import { SpatialDataPoint } from "./spatial-types";

/* Seedable random number generator */
var seed = 1;
function random() {
  var x = Math.sin(seed++) * 10000;
  return x - Math.floor(x);
}

var currentLayer = 0;
var currentTrack = 0;
var precision = 0.01;
var currentX = 0;
var currentY = 0;
var currentZ = 0;
var counter = 0;

function generateRandomDataArray(length: number): number[] {
  const dataArray: number[] = [];
  for (let i = 0; i < length; i++) {
    dataArray.push(random());
  }
  return dataArray;
}

/** This generates a random spatial data point */
export function generateSingleRandomSpatialDataPoint(lengthOfDataList: number = 100) {
  currentX += precision;

  if (counter % 100 === 0) {
    currentTrack++;
    currentY += precision;
  }

  if (currentTrack % 10 === 0) {
    currentLayer++;
    currentZ += precision;
  }

  const point: SpatialDataPoint = {
    x: currentX,
    y: currentY,
    z: currentZ,
    timestamp: 1000 + seed,
    metadata: {
      isSuccess: Math.floor(Math.random() * 100) % 2 === 0, // set isSuccess to be completely random
      orientation: generateRandomDataArray(10),
      layer: currentLayer,
      track: currentTrack,
      "MTLH_TCP in BASE": {
        "can be found in corresponding fsd csv-file.": random() % 2 === 0,
      },
      base: {
        A: random() * 1000,
        B: random() * 2000,
        C: random() * 3000,
        X: random(),
        Y: random(),
        Z: random(),
        baseName: "baseName",
      },
      name: `name ${random()}`,
    },
    measurements: {
      value: random() * 12345,
      data: generateRandomDataArray(lengthOfDataList),
    },
  };

  counter++;
  return point;
}

export function generateMultipleSpatialDataPoints(
  numOfPoints: number,
  lengthOfDataList: number = 100,
): SpatialDataPoint[] {
  const pointList: SpatialDataPoint[] = [];

  for (let i = 0; i < numOfPoints; i++) {
    pointList.push(generateSingleRandomSpatialDataPoint(lengthOfDataList));
  }
  return pointList;
}
