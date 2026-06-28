/* tslint:disable */
/* eslint-disable */
/**
 * A page of Snapshot items with pagination metadata.
 * Mirrors the Java PagedResponseIO<SnapshotIO> wire shape from APISIMP-COLLECTION-SNAPSHOT-PLAIN-ARRAY.
 * @export
 * @interface PagedResponseSnapshot
 */

import type { Snapshot } from './Snapshot';
import {
    SnapshotFromJSON,
    SnapshotToJSON,
} from './Snapshot';

export interface PagedResponseSnapshot {
    /**
     * Items on this page.
     * @type {Array<Snapshot>}
     * @memberof PagedResponseSnapshot
     */
    items: Array<Snapshot>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseSnapshot
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseSnapshot
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseSnapshot
     */
    pageSize: number;
}

export function instanceOfPagedResponseSnapshot(value: object): value is PagedResponseSnapshot {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseSnapshotFromJSON(json: any): PagedResponseSnapshot {
    return PagedResponseSnapshotFromJSONTyped(json, false);
}

export function PagedResponseSnapshotFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseSnapshot {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(SnapshotFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseSnapshotToJSON(value?: PagedResponseSnapshot | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(SnapshotToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
