/* tslint:disable */
/* eslint-disable */
/**
 * A page of AdminGitCredentialListItemIO items with pagination metadata.
 * Mirrors the Java PagedResponseIO<AdminGitCredentialListItemIO> wire shape from APISIMP-GIT-CRED-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseAdminGitCredentialListItem
 */

import type { AdminGitCredentialListItemIO } from './AdminGitCredentialListItemIO';
import {
    AdminGitCredentialListItemIOFromJSON,
    AdminGitCredentialListItemIOToJSON,
} from './AdminGitCredentialListItemIO';

export interface PagedResponseAdminGitCredentialListItem {
    /**
     * Items on this page.
     * @type {Array<AdminGitCredentialListItemIO>}
     * @memberof PagedResponseAdminGitCredentialListItem
     */
    items: Array<AdminGitCredentialListItemIO>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseAdminGitCredentialListItem
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseAdminGitCredentialListItem
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseAdminGitCredentialListItem
     */
    pageSize: number;
}

export function instanceOfPagedResponseAdminGitCredentialListItem(value: object): value is PagedResponseAdminGitCredentialListItem {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseAdminGitCredentialListItemFromJSON(json: any): PagedResponseAdminGitCredentialListItem {
    return PagedResponseAdminGitCredentialListItemFromJSONTyped(json, false);
}

export function PagedResponseAdminGitCredentialListItemFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseAdminGitCredentialListItem {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(AdminGitCredentialListItemIOFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseAdminGitCredentialListItemToJSON(value?: PagedResponseAdminGitCredentialListItem | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(AdminGitCredentialListItemIOToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
