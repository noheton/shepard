import * as runtime from '../runtime';

export interface GitCredentialIO {
  appId: string;
  host: string;
  displayName: string | null;
  username: string;
}

export interface CreateGitCredentialIO {
  host: string;
  displayName?: string | null;
  username: string;
  pat: string;
}

export interface PatchGitCredentialIO {
  displayName?: string | null;
  username?: string;
  pat?: string | null;
}

export class GitCredentialsApi extends runtime.BaseAPI {
  async listCredentials(initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<GitCredentialIO[]> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/me/git-credentials`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json() as Promise<GitCredentialIO[]>;
  }

  async createCredential(body: CreateGitCredentialIO, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<GitCredentialIO> {
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
      path: `/v2/me/git-credentials`,
      method: 'POST',
      headers: headerParameters,
      query: {},
      body,
    }, initOverrides);

    return response.json() as Promise<GitCredentialIO>;
  }

  async getCredential(appId: string, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<GitCredentialIO> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    const response = await this.request({
      path: `/v2/me/git-credentials/${encodeURIComponent(appId)}`,
      method: 'GET',
      headers: headerParameters,
      query: {},
    }, initOverrides);

    return response.json() as Promise<GitCredentialIO>;
  }

  async patchCredential(appId: string, body: PatchGitCredentialIO, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<GitCredentialIO> {
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
      path: `/v2/me/git-credentials/${encodeURIComponent(appId)}`,
      method: 'PATCH',
      headers: headerParameters,
      query: {},
      body,
    }, initOverrides);

    return response.json() as Promise<GitCredentialIO>;
  }

  async deleteCredential(appId: string, initOverrides?: RequestInit | runtime.InitOverrideFunction): Promise<void> {
    const headerParameters: runtime.HTTPHeaders = {};

    if (this.configuration && this.configuration.accessToken) {
      const token = this.configuration.accessToken;
      const tokenString = await token('bearer', []);
      if (tokenString) {
        headerParameters['Authorization'] = `Bearer ${tokenString}`;
      }
    }

    await this.request({
      path: `/v2/me/git-credentials/${encodeURIComponent(appId)}`,
      method: 'DELETE',
      headers: headerParameters,
      query: {},
    }, initOverrides);
  }
}
