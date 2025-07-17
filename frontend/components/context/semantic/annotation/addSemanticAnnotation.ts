import {
  SemanticAnnotationApi,
  type SemanticAnnotation,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export type SemanticAnnotationCreateArgs = Omit<
  SemanticAnnotation,
  "id" | "name" | "propertyName" | "valueName"
>;

export type AddSemanticAnnotationToCollectionArgs = {
  collectionId: number;
  annotation: SemanticAnnotationCreateArgs;
};

export type AddSemanticAnnotationToDataObjectArgs = {
  collectionId: number;
  dataObjectId: number;
  annotation: SemanticAnnotationCreateArgs;
};

export type AddSemanticAnnotationToReferenceArgs = {
  collectionId: number;
  dataObjectId: number;
  referenceId: number;
  annotation: SemanticAnnotationCreateArgs;
};

export type AddSemanticAnnotationArgs =
  | AddSemanticAnnotationToCollectionArgs
  | AddSemanticAnnotationToDataObjectArgs
  | AddSemanticAnnotationToReferenceArgs;

export function isAddSemanticAnnotationToCollectionArgs(
  args: AddSemanticAnnotationArgs,
): args is AddSemanticAnnotationToCollectionArgs {
  return (
    (args as AddSemanticAnnotationToCollectionArgs).collectionId !== undefined
  );
}

export function isAddSemanticAnnotationToDataObjectArgs(
  args: AddSemanticAnnotationArgs,
): args is AddSemanticAnnotationToDataObjectArgs {
  return (
    (args as AddSemanticAnnotationToDataObjectArgs).dataObjectId !==
      undefined &&
    (args as AddSemanticAnnotationToDataObjectArgs).collectionId !== undefined
  );
}

export function isAddSemanticAnnotationToReferenceArgs(
  args: AddSemanticAnnotationArgs,
): args is AddSemanticAnnotationToReferenceArgs {
  return (
    (args as AddSemanticAnnotationToReferenceArgs).referenceId !== undefined &&
    (args as AddSemanticAnnotationToReferenceArgs).dataObjectId !== undefined &&
    (args as AddSemanticAnnotationToReferenceArgs).collectionId !== undefined
  );
}

export async function createAnnotationForCollection(
  collectionId: number,
  annotation: SemanticAnnotationCreateArgs,
) {
  return await useShepardApi(
    SemanticAnnotationApi,
  ).value.createCollectionAnnotation({
    collectionId: collectionId,
    semanticAnnotation: {
      propertyRepositoryId: annotation.propertyRepositoryId,
      propertyIRI: annotation.propertyIRI,
      valueRepositoryId: annotation.valueRepositoryId,
      valueIRI: annotation.valueIRI,
    },
  });
}

export async function createAnnotationForDataObject(
  collectionId: number,
  dataObjectId: number,
  annotation: SemanticAnnotationCreateArgs,
) {
  return await useShepardApi(
    SemanticAnnotationApi,
  ).value.createDataObjectAnnotation({
    collectionId: collectionId,
    dataObjectId: dataObjectId,
    semanticAnnotation: {
      propertyRepositoryId: annotation.propertyRepositoryId,
      propertyIRI: annotation.propertyIRI,
      valueRepositoryId: annotation.valueRepositoryId,
      valueIRI: annotation.valueIRI,
    },
  });
}

export async function createAnnotationForReference(
  collectionId: number,
  dataObjectId: number,
  referenceId: number,
  annotation: SemanticAnnotationCreateArgs,
) {
  return await useShepardApi(
    SemanticAnnotationApi,
  ).value.createReferenceAnnotation({
    collectionId: collectionId,
    dataObjectId: dataObjectId,
    referenceId: referenceId,
    semanticAnnotation: {
      propertyRepositoryId: annotation.propertyRepositoryId,
      propertyIRI: annotation.propertyIRI,
      valueRepositoryId: annotation.valueRepositoryId,
      valueIRI: annotation.valueIRI,
    },
  });
}

/**
 * Convenience function to add a semantic annotation to a collection,
 * data object or reference based on the given parameters.
 * @param args
 * @returns
 */
export async function addSemanticAnnotation(args: AddSemanticAnnotationArgs) {
  if (isAddSemanticAnnotationToReferenceArgs(args)) {
    return createAnnotationForReference(
      args.collectionId,
      args.dataObjectId,
      args.referenceId,
      args.annotation,
    );
  } else if (isAddSemanticAnnotationToDataObjectArgs(args)) {
    return createAnnotationForDataObject(
      args.collectionId,
      args.dataObjectId,
      args.annotation,
    );
  } else if (isAddSemanticAnnotationToCollectionArgs(args)) {
    return createAnnotationForCollection(args.collectionId, args.annotation);
  } else {
    throw new Error("Invalid arguments for addSemanticAnnotation");
  }
}
