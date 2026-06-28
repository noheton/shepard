/* tslint:disable */
/* eslint-disable */
/**
 * A page of ShepardTemplate items with pagination metadata.
 * Mirrors the Java PagedResponseIO<ShepardTemplateIO> wire shape from APISIMP-TEMPLATES-LIST-UNBOUNDED.
 * @export
 * @interface PagedResponseShepardTemplate
 */

import type { ShepardTemplate } from './ShepardTemplate';
import {
    ShepardTemplateFromJSON,
    ShepardTemplateToJSON,
} from './ShepardTemplate';

export interface PagedResponseShepardTemplate {
    /**
     * Items on this page.
     * @type {Array<ShepardTemplate>}
     * @memberof PagedResponseShepardTemplate
     */
    items: Array<ShepardTemplate>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseShepardTemplate
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseShepardTemplate
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseShepardTemplate
     */
    pageSize: number;
}

export function instanceOfPagedResponseShepardTemplate(value: object): value is PagedResponseShepardTemplate {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseShepardTemplateFromJSON(json: any): PagedResponseShepardTemplate {
    return PagedResponseShepardTemplateFromJSONTyped(json, false);
}

export function PagedResponseShepardTemplateFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseShepardTemplate {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(ShepardTemplateFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseShepardTemplateToJSON(value?: PagedResponseShepardTemplate | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(ShepardTemplateToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
