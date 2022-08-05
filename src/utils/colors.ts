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
