/* tslint:disable */
/* eslint-disable */
/**
 * A page of ReferenceV2 items with pagination metadata.
 * Mirrors the Java PagedResponseIO<ReferenceV2IO> wire shape from APISIMP-REFERENCES-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseReferenceV2
 */

import type { ReferenceV2 } from './ReferenceV2';
import {
    ReferenceV2FromJSON,
    ReferenceV2ToJSON,
} from './ReferenceV2';

export interface PagedResponseReferenceV2 {
    /**
     * Items on this page.
     * @type {Array<ReferenceV2>}
     * @memberof PagedResponseReferenceV2
     */
    items: Array<ReferenceV2>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseReferenceV2
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseReferenceV2
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseReferenceV2
     */
    pageSize: number;
}

export function instanceOfPagedResponseReferenceV2(value: object): value is PagedResponseReferenceV2 {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseReferenceV2FromJSON(json: any): PagedResponseReferenceV2 {
    return PagedResponseReferenceV2FromJSONTyped(json, false);
}

export function PagedResponseReferenceV2FromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseReferenceV2 {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(ReferenceV2FromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseReferenceV2ToJSON(value?: PagedResponseReferenceV2 | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(ReferenceV2ToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
