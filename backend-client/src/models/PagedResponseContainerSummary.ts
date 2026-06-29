/* tslint:disable */
/* eslint-disable */
/**
 * A page of ContainerSummaryIO items with pagination metadata.
 * Mirrors the Java PagedResponseIO<ContainerSummaryIO> wire shape from APISIMP-CONTAINERS-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseContainerSummary
 */

import type { ContainerSummaryIO } from './ContainerSummaryIO';
import {
    ContainerSummaryIOFromJSON,
    ContainerSummaryIOToJSON,
} from './ContainerSummaryIO';

export interface PagedResponseContainerSummary {
    /**
     * Items on this page.
     * @type {Array<ContainerSummaryIO>}
     * @memberof PagedResponseContainerSummary
     */
    items: Array<ContainerSummaryIO>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseContainerSummary
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseContainerSummary
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseContainerSummary
     */
    pageSize: number;
}

export function instanceOfPagedResponseContainerSummary(value: object): value is PagedResponseContainerSummary {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseContainerSummaryFromJSON(json: any): PagedResponseContainerSummary {
    return PagedResponseContainerSummaryFromJSONTyped(json, false);
}

export function PagedResponseContainerSummaryFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseContainerSummary {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(ContainerSummaryIOFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseContainerSummaryToJSON(value?: PagedResponseContainerSummary | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(ContainerSummaryIOToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
