/* tslint:disable */
/* eslint-disable */
/**
 * A page of OntologyAlignment items with pagination metadata.
 * Mirrors the Java PagedResponseIO<OntologyAlignmentIO> wire shape from APISIMP-ONTOLOGY-ALIGNMENT-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseOntologyAlignment
 */

import type { OntologyAlignment } from './OntologyAlignment';
import {
    OntologyAlignmentFromJSON,
    OntologyAlignmentToJSON,
} from './OntologyAlignment';

export interface PagedResponseOntologyAlignment {
    /**
     * Items on this page.
     * @type {Array<OntologyAlignment>}
     * @memberof PagedResponseOntologyAlignment
     */
    items: Array<OntologyAlignment>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseOntologyAlignment
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseOntologyAlignment
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseOntologyAlignment
     */
    pageSize: number;
}

export function instanceOfPagedResponseOntologyAlignment(value: object): value is PagedResponseOntologyAlignment {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseOntologyAlignmentFromJSON(json: any): PagedResponseOntologyAlignment {
    return PagedResponseOntologyAlignmentFromJSONTyped(json, false);
}

export function PagedResponseOntologyAlignmentFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseOntologyAlignment {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(OntologyAlignmentFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseOntologyAlignmentToJSON(value?: PagedResponseOntologyAlignment | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(OntologyAlignmentToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
