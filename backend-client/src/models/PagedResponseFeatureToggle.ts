/* tslint:disable */
/* eslint-disable */
/**
 * A page of FeatureToggle items with pagination metadata.
 * Mirrors the Java PagedResponseIO<FeatureToggleIO> wire shape from APISIMP-FEATURES-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseFeatureToggle
 */

import type { FeatureToggle } from './FeatureToggle';
import {
    FeatureToggleFromJSON,
    FeatureToggleToJSON,
} from './FeatureToggle';

export interface PagedResponseFeatureToggle {
    /**
     * Items on this page.
     * @type {Array<FeatureToggle>}
     * @memberof PagedResponseFeatureToggle
     */
    items: Array<FeatureToggle>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseFeatureToggle
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseFeatureToggle
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseFeatureToggle
     */
    pageSize: number;
}

export function instanceOfPagedResponseFeatureToggle(value: object): value is PagedResponseFeatureToggle {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseFeatureToggleFromJSON(json: any): PagedResponseFeatureToggle {
    return PagedResponseFeatureToggleFromJSONTyped(json, false);
}

export function PagedResponseFeatureToggleFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseFeatureToggle {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(FeatureToggleFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseFeatureToggleToJSON(value?: PagedResponseFeatureToggle | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(FeatureToggleToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
