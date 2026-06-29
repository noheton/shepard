/* tslint:disable */
/* eslint-disable */
/**
 * A page of OntologyBundleIO items with pagination metadata.
 * Mirrors the Java PagedResponseIO<OntologyBundleIO> wire shape from APISIMP-ONTOLOGY-BUNDLES-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseOntologyBundleIO
 */

import type { OntologyBundleIO } from './OntologyBundleIO';
import {
    OntologyBundleIOFromJSON,
    OntologyBundleIOToJSON,
} from './OntologyBundleIO';

export interface PagedResponseOntologyBundleIO {
    /**
     * Items on this page.
     * @type {Array<OntologyBundleIO>}
     * @memberof PagedResponseOntologyBundleIO
     */
    items: Array<OntologyBundleIO>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseOntologyBundleIO
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseOntologyBundleIO
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseOntologyBundleIO
     */
    pageSize: number;
}

export function instanceOfPagedResponseOntologyBundleIO(value: object): value is PagedResponseOntologyBundleIO {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseOntologyBundleIOFromJSON(json: any): PagedResponseOntologyBundleIO {
    return PagedResponseOntologyBundleIOFromJSONTyped(json, false);
}

export function PagedResponseOntologyBundleIOFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseOntologyBundleIO {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(OntologyBundleIOFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseOntologyBundleIOToJSON(value?: PagedResponseOntologyBundleIO | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(OntologyBundleIOToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
