import { SpatialDataPoint } from "./spatial-types";

/* Seedable random number generator */
var seed = 1;
function random() {
  var x = Math.sin(seed++) * 10000;
  return x - Math.floor(x);
}

function generateRandomDataArray(length: number): number[] {
  const dataArray: number[] = [];
  for (let i = 0; i < length; i++) {
    dataArray.push(random());
  }
  return dataArray;
}

const PRECISION = 0.1;
const LAYER_STEP = 10;
const TRACK_STEP = 1000;

/** This generates a random spatial data point */
export function generateSingleRandomSpatialDataPoint(i: number, lengthOfDataList: number = 100) {
  var currentTrack = i / TRACK_STEP;
  var currentLayer = currentTrack / LAYER_STEP;

  var currentX = (i % TRACK_STEP) * PRECISION;
  var currentY = (currentTrack % LAYER_STEP) * PRECISION;
  var currentZ = currentLayer * PRECISION;

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
