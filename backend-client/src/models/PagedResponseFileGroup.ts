/* tslint:disable */
/* eslint-disable */
/**
 * A page of FileGroup items with pagination metadata.
 * Mirrors the Java PagedResponseIO<FileGroupIO> wire shape from APISIMP-UNBOUNDED-FILEGROUPS.
 * @export
 * @interface PagedResponseFileGroup
 */

import type { FileGroup } from './FileGroup';
import {
    FileGroupFromJSON,
    FileGroupToJSON,
} from './FileGroup';

export interface PagedResponseFileGroup {
    /**
     * Items on this page.
     * @type {Array<FileGroup>}
     * @memberof PagedResponseFileGroup
     */
    items: Array<FileGroup>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseFileGroup
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseFileGroup
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseFileGroup
     */
    pageSize: number;
}

export function instanceOfPagedResponseFileGroup(value: object): value is PagedResponseFileGroup {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseFileGroupFromJSON(json: any): PagedResponseFileGroup {
    return PagedResponseFileGroupFromJSONTyped(json, false);
}

export function PagedResponseFileGroupFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseFileGroup {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(FileGroupFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseFileGroupToJSON(value?: PagedResponseFileGroup | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(FileGroupToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
