/* tslint:disable */
/* eslint-disable */
/**
 * shepard backend — v2 lab journal entry CRUD
 * APISIMP-LJE-ENTRY-V2-CRUD (fire-582)
 *
 * GET/PUT/DELETE /v2/lab-journal/{appId}
 */

import * as runtime from '../runtime';
import type {
  LabJournalEntry,
  UpdateLabJournalEntryV2,
} from '../models/index';
import {
    LabJournalEntryFromJSON,
    LabJournalEntryToJSON,
    UpdateLabJournalEntryV2FromJSON,
    UpdateLabJournalEntryV2ToJSON,
} from '../models/index';

export interface GetLabJournalEntryRequest {
    appId: string;
}

export interface UpdateLabJournalEntryRequest {
    appId: string;
    updateLabJournalEntryV2: UpdateLabJournalEntryV2;
}

export interface DeleteLabJournalEntryRequest {
    appId: string;
}

export class LabJournalEntryV2Api extends runtime.BaseAPI {

    async getLabJournalEntryRaw(requestParameters: GetLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<LabJournalEntry>> {
        if (requestParameters['appId'] == null) {
            throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling getLabJournalEntry().');
        }
        const headerParameters: runtime.HTTPHeaders = {};
        if (this.configuration && this.configuration.apiKey) {
            headerParameters["X-API-KEY"] = await this.configuration.apiKey("X-API-KEY");
        }
        if (this.configuration && this.configuration.accessToken) {
            const token = this.configuration.accessToken;
            const tokenString = await token("bearer", []);
            if (tokenString) headerParameters["Authorization"] = `Bearer ${tokenString}`;
        }
        const response = await this.request({
            path: `/v2/lab-journal/{appId}`.replace(`{${"appId"}}`, encodeURIComponent(String(requestParameters['appId']))),
            method: 'GET',
            headers: headerParameters,
        }, initOverrides);
        return new runtime.JSONApiResponse(response, (jsonValue) => LabJournalEntryFromJSON(jsonValue));
    }

    /**
     * [v2] Get a lab journal entry by appId.
     */
    async getLabJournalEntry(requestParameters: GetLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<LabJournalEntry> {
        const response = await this.getLabJournalEntryRaw(requestParameters, initOverrides);
        return await response.value();
    }

    async updateLabJournalEntryRaw(requestParameters: UpdateLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<LabJournalEntry>> {
        if (requestParameters['appId'] == null) {
            throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling updateLabJournalEntry().');
        }
        if (requestParameters['updateLabJournalEntryV2'] == null) {
            throw new runtime.RequiredError('updateLabJournalEntryV2', 'Required parameter "updateLabJournalEntryV2" was null or undefined when calling updateLabJournalEntry().');
        }
        const headerParameters: runtime.HTTPHeaders = {};
        headerParameters['Content-Type'] = 'application/json';
        if (this.configuration && this.configuration.apiKey) {
            headerParameters["X-API-KEY"] = await this.configuration.apiKey("X-API-KEY");
        }
        if (this.configuration && this.configuration.accessToken) {
            const token = this.configuration.accessToken;
            const tokenString = await token("bearer", []);
            if (tokenString) headerParameters["Authorization"] = `Bearer ${tokenString}`;
        }
        const response = await this.request({
            path: `/v2/lab-journal/{appId}`.replace(`{${"appId"}}`, encodeURIComponent(String(requestParameters['appId']))),
            method: 'PUT',
            headers: headerParameters,
            body: UpdateLabJournalEntryV2ToJSON(requestParameters['updateLabJournalEntryV2']),
        }, initOverrides);
        return new runtime.JSONApiResponse(response, (jsonValue) => LabJournalEntryFromJSON(jsonValue));
    }

    /**
     * [v2] Update the content of a lab journal entry.
     */
    async updateLabJournalEntry(requestParameters: UpdateLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<LabJournalEntry> {
        const response = await this.updateLabJournalEntryRaw(requestParameters, initOverrides);
        return await response.value();
    }

    async deleteLabJournalEntryRaw(requestParameters: DeleteLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<runtime.ApiResponse<void>> {
        if (requestParameters['appId'] == null) {
            throw new runtime.RequiredError('appId', 'Required parameter "appId" was null or undefined when calling deleteLabJournalEntry().');
        }
        const headerParameters: runtime.HTTPHeaders = {};
        if (this.configuration && this.configuration.apiKey) {
            headerParameters["X-API-KEY"] = await this.configuration.apiKey("X-API-KEY");
        }
        if (this.configuration && this.configuration.accessToken) {
            const token = this.configuration.accessToken;
            const tokenString = await token("bearer", []);
            if (tokenString) headerParameters["Authorization"] = `Bearer ${tokenString}`;
        }
        const response = await this.request({
            path: `/v2/lab-journal/{appId}`.replace(`{${"appId"}}`, encodeURIComponent(String(requestParameters['appId']))),
            method: 'DELETE',
            headers: headerParameters,
        }, initOverrides);
        return new runtime.VoidApiResponse(response);
    }

    /**
     * [v2] Delete a lab journal entry.
     */
    async deleteLabJournalEntry(requestParameters: DeleteLabJournalEntryRequest, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<void> {
        await this.deleteLabJournalEntryRaw(requestParameters, initOverrides);
    }
}
