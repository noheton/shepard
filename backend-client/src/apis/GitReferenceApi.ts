import * as runtime from '../runtime';

export interface GitReferenceIO {
  appId: string;
  repoUrl: string;
  ref?: string;
  path?: string;
}

export interface CreateGitReferenceIO {
  repoUrl: string;
  ref?: string;
  path?: string;
}

export interface PatchGitReferenceIO {
  repoUrl?: string;
  ref?: string | null;
  path?: string | null;
}

export class GitReferenceApi extends runtime.BaseAPI {

  async listGitReferences(dataObjectAppId: string): Promise<GitReferenceIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/git-references`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    });

    return response.json();
  }

  async createGitReference(dataObjectAppId: string, body: CreateGitReferenceIO): Promise<GitReferenceIO> {
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/git-references`,
      method: 'POST',
      headers: headerParameters,
      query: {},
      body,
    });

    return response.json();
  }

  async getGitReference(dataObjectAppId: string, appId: string): Promise<GitReferenceIO> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/git-references/${encodeURIComponent(appId)}`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    });

    return response.json();
  }

  async patchGitReference(dataObjectAppId: string, appId: string, body: PatchGitReferenceIO): Promise<GitReferenceIO> {
    const headerParameters: runtime.HTTPHeaders = {};
    headerParameters['Content-Type'] = 'application/json';

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/git-references/${encodeURIComponent(appId)}`,
      method: 'PATCH',
      headers: headerParameters,
      query: {},
      body,
    });

    return response.json();
  }

  async deleteGitReference(dataObjectAppId: string, appId: string): Promise<void> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    await this.request({
      path: `/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/git-references/${encodeURIComponent(appId)}`,
      method: 'DELETE',
      headers: headerParameters,
      query: {},
    });
  }
}
