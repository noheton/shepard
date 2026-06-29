/* tslint:disable */
/* eslint-disable */
/**
 * A page of Activity items with pagination metadata.
 * Mirrors the Java PagedResponseIO<ActivityIO> wire shape from APISIMP-PROVENANCE-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseActivity
 */

import type { Activity } from './Activity';
import {
    ActivityFromJSON,
    ActivityToJSON,
} from './Activity';

export interface PagedResponseActivity {
    /**
     * Items on this page.
     * @type {Array<Activity>}
     * @memberof PagedResponseActivity
     */
    items: Array<Activity>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseActivity
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseActivity
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseActivity
     */
    pageSize: number;
}

export function instanceOfPagedResponseActivity(value: object): value is PagedResponseActivity {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseActivityFromJSON(json: any): PagedResponseActivity {
    return PagedResponseActivityFromJSONTyped(json, false);
}

export function PagedResponseActivityFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseActivity {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(ActivityFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseActivityToJSON(value?: PagedResponseActivity | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(ActivityToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
