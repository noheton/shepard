import type { ShepardFileDataTableItem } from "./ShepardFileDataTableItem";
import type { FileMeta } from "./fileReferenceTypes";

export const mapShepardFilesToDataTableItems = (
  files: FileMeta[],
): ShepardFileDataTableItem[] => {
  return files.map(file => mapShepardFileToDataTableItem(file));
};

export const mapShepardFileToDataTableItem = (
  file: FileMeta,
): ShepardFileDataTableItem => ({
  name: {
    filename: file.filename ?? "",
    availability: file.availability ?? "error",
  },
  oid: file.oid ?? "",
  createdAt: file.createdAt ?? new Date(0),
  actions: {
    download: {
      enabled: mapDownloadEnabled(file),
      filename: file.filename ?? "",
      oid: file.oid ?? "",
    },
    showDetails: {
      enabled: mapShowDetailsEnabled(file),
      oid: file.oid ?? "",
      fileType: mapShepardFilenameToFileType(file.filename ?? ""),
    },
  },
});

export const mapShepardFilenameToFileType = (filename: string) => {
  const extension = filename.split(".").pop()?.toLowerCase();
  if (
    extension === "txt" ||
    extension === "yml" ||
    extension === "yaml" ||
    extension === "json" ||
    extension === "md" ||
    extension === "toml"
  )
    return "text";
  if (
    extension === "png" ||
    extension === "jpg" ||
    extension === "jpeg" ||
    extension === "bmp" ||
    extension === "gif"
  ) {
    return "image";
  }
  return "unknown";
};

export const mapShowDetailsEnabled = (file: FileMeta) => {
  return file.oid && file.availability === "available" ? true : false;
};

export const mapDownloadEnabled = (file: FileMeta) => {
  return file.oid && file.availability === "available" ? true : false;
};
