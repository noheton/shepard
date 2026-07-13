/* tslint:disable */
/* eslint-disable */
/**
 * APISIMP-LJE-ENTRY-V2-CRUD — request body for PUT /v2/lab-journal/{appId}.
 */

import { mapValues } from '../runtime';

export interface UpdateLabJournalEntryV2 {
    /**
     * New journal content (CommonMark markdown).
     * @type {string}
     * @memberof UpdateLabJournalEntryV2
     */
    journalContent: string;
}

export function instanceOfUpdateLabJournalEntryV2(value: object): value is UpdateLabJournalEntryV2 {
    if (!('journalContent' in value) || value['journalContent'] === undefined) return false;
    return true;
}

export function UpdateLabJournalEntryV2FromJSON(json: any): UpdateLabJournalEntryV2 {
    return UpdateLabJournalEntryV2FromJSONTyped(json, false);
}

export function UpdateLabJournalEntryV2FromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateLabJournalEntryV2 {
    if (json == null) {
        return json;
    }
    return {
        'journalContent': json['journalContent'],
    };
}

export function UpdateLabJournalEntryV2ToJSON(value?: UpdateLabJournalEntryV2 | null): any {
    if (value == null) {
        return value;
    }
    return {
        'journalContent': value['journalContent'],
    };
}
