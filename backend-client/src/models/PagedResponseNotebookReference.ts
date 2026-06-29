/* tslint:disable */
/* eslint-disable */
/**
 * A page of NotebookReference items with pagination metadata.
 * Mirrors the Java PagedResponseIO<NotebookReferenceIO> wire shape from APISIMP-NOTEBOOKS-LIST-ENVELOPE.
 * @export
 * @interface PagedResponseNotebookReference
 */

import type { NotebookReference } from './NotebookReference';
import {
    NotebookReferenceFromJSON,
    NotebookReferenceToJSON,
} from './NotebookReference';

export interface PagedResponseNotebookReference {
    /**
     * Items on this page.
     * @type {Array<NotebookReference>}
     * @memberof PagedResponseNotebookReference
     */
    items: Array<NotebookReference>;
    /**
     * Total item count before paging.
     * @type {number}
     * @memberof PagedResponseNotebookReference
     */
    total: number;
    /**
     * Zero-based page index returned.
     * @type {number}
     * @memberof PagedResponseNotebookReference
     */
    page: number;
    /**
     * Maximum items per page honoured by the server.
     * @type {number}
     * @memberof PagedResponseNotebookReference
     */
    pageSize: number;
}

export function instanceOfPagedResponseNotebookReference(value: object): value is PagedResponseNotebookReference {
    return 'items' in value && 'total' in value && 'page' in value && 'pageSize' in value;
}

export function PagedResponseNotebookReferenceFromJSON(json: any): PagedResponseNotebookReference {
    return PagedResponseNotebookReferenceFromJSONTyped(json, false);
}

export function PagedResponseNotebookReferenceFromJSONTyped(json: any, ignoreDiscriminator: boolean): PagedResponseNotebookReference {
    if (json == null) {
        return json;
    }
    return {
        'items': (json['items'] as Array<any>).map(NotebookReferenceFromJSON),
        'total': json['total'],
        'page': json['page'],
        'pageSize': json['pageSize'],
    };
}

export function PagedResponseNotebookReferenceToJSON(value?: PagedResponseNotebookReference | null): any {
    if (value == null) {
        return value;
    }
    return {
        'items': (value['items'] as Array<any>).map(NotebookReferenceToJSON),
        'total': value['total'],
        'page': value['page'],
        'pageSize': value['pageSize'],
    };
}
