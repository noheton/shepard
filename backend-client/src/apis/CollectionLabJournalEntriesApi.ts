/* tslint:disable */
/* eslint-disable */

import * as runtime from '../runtime';
import type { LabJournalEntry } from '../models/LabJournalEntry';
import { LabJournalEntryFromJSON } from '../models/LabJournalEntry';

export interface ListCollectionLabJournalEntriesRequest {
  collectionAppId: string;
}

/**
 * Collection bulk lab-journal-entries API — UI-020
 * GET /v2/collections/{collectionAppId}/lab-journal-entries
 *
 * Returns every non-deleted LabJournalEntry attached to any non-deleted
 * DataObject in the collection, sorted by createdAt DESC. Replaces the
 * per-DataObject N+1 fan-out that previously broke MFFD-Dropbox (8500
 * concurrent requests + thousands of console errors). The frontend groups
 * client-side via the `dataObjectId` field on each entry.
 */
export class CollectionLabJournalEntriesApi extends runtime.BaseAPI {

  async listCollectionLabJournalEntriesRaw(
    requestParameters: ListCollectionLabJournalEntriesRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<runtime.ApiResponse<LabJournalEntry[]>> {
    if (requestParameters['collectionAppId'] == null) {
      throw new runtime.RequiredError(
        'collectionAppId',
        'Required parameter "collectionAppId" was null or undefined when calling listCollectionLabJournalEntries().',
      );
    }

    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request(
      {
        path: `/v2/collections/${encodeURIComponent(requestParameters['collectionAppId'])}/lab-journal-entries`,
        method: 'GET',
        headers: headerParameters,
        query: {},
      },
      initOverrides,
    );

    return new runtime.JSONApiResponse(response, (jsonValue) => jsonValue.map(LabJournalEntryFromJSON));
  }

  async listCollectionLabJournalEntries(
    requestParameters: ListCollectionLabJournalEntriesRequest,
    initOverrides?: RequestInit | runtime.InitOverrideFunction,
  ): Promise<LabJournalEntry[]> {
    const response = await this.listCollectionLabJournalEntriesRaw(requestParameters, initOverrides);
    return await response.value();
  }
}
