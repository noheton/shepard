export function HSVtoRGB(returnColor: number[]) {
  let color: string[] = ["0", "0", "0"];
  const H = returnColor[0];
  const S = returnColor[1];
  const V = returnColor[2];
  const hi = Math.floor(H / 60);
  const f = H / 60 - hi;
  const p = V * (1 - S);
  const q = V * (1 - S * f);
  const t = V * (1 - S * (1 - f));
  if (hi == 0 || hi == 6) {
    color = [
      (V * 255).toFixed().toString(),
      (t * 255).toFixed().toString(),
      (p * 255).toFixed().toString(),
    ];
  } else if (hi == 1) {
    color = [
      (q * 255).toFixed().toString(),
      (V * 255).toFixed().toString(),
      (p * 255).toFixed().toString(),
    ];
  } else if (hi == 2) {
    color = [
      (p * 255).toFixed().toString(),
      (V * 255).toFixed().toString(),
      (t * 255).toFixed().toString(),
    ];
  } else if (hi == 3) {
    color = [
      (p * 255).toFixed().toString(),
      (q * 255).toFixed().toString(),
      (V * 255).toFixed().toString(),
    ];
  } else if (hi == 4) {
    color = [
      (t * 255).toFixed().toString(),
      (p * 255).toFixed().toString(),
      (V * 255).toFixed().toString(),
    ];
  } else if (hi == 5) {
    color = [
      (V * 255).toFixed().toString(),
      (p * 255).toFixed().toString(),
      (q * 255).toFixed().toString(),
    ];
  } else if (S == 0) {
    color = [
      (V * 255).toFixed.toString(),
      (V * 255).toFixed.toString(),
      (V * 255).toFixed.toString(),
    ];
  }
  return (
    "rgba(" +
    color[0] +
    ", " +
    color[1] +
    ", " +
    color[2] +
    ", 1.0)"
  ).toString();
}

export function colorCalculator(counter: number) {
  let returnColor = [];
  const baseColorArray = [
    [202, 1, 0.733],
    [1, 0.659, 0.702],
  ];
  if (counter < 3) {
    returnColor = baseColorArray[0];
    returnColor[1] = returnColor[1] - counter * 0.4;
  } else {
    returnColor = baseColorArray[1];
    returnColor[1] = returnColor[1] - (counter - 3) * 0.3;
  }
  return HSVtoRGB(returnColor);
}
