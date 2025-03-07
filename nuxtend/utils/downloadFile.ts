export const downloadFile = (response: Blob, filename: string): void => {
  const link = document.createElement("a");
  link.href = URL.createObjectURL(response);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
};

export const sanitizeFilename = (filename: string): string => {
  return filename.replace(/[^a-z0-9.]/gi, "_");
};
