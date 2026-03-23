import axios, { AxiosInstance } from 'axios';
import { getNoteServiceUrl } from '../nacos/registry';
import { PermissionCheckResponse, SnapshotResponse } from '../types';

/**
 * 每次获取最新的客户端实例，内置服务发现逻辑
 */
export async function getNoteServiceClient(): Promise<AxiosInstance> {
  try {
    const baseURL = await getNoteServiceUrl();
    return axios.create({
      baseURL,
      timeout: 10000,
      headers: {
        'X-From-Source': 'APISIX-wX0iR6tY'
      }
    });
  } catch (err) {
    console.error('[NoteServiceClient] 无法从 Nacos 发现 Java 笔记服务:', err);
    throw err;
  }
}

export async function checkPermission(
  resourceId: string,
  userId: string,
  resourceType: string,
  groupRoles: Record<string, string>,
): Promise<string> {
  const client = await getNoteServiceClient();
  const resp = await client.post<PermissionCheckResponse>(
    '/internal/note/checkPermission',
    { resourceId, userId, resourceType, groupRoles },
  );

  const resData = resp?.data;
  if (!resData || resData.code !== 200 || !resData.data) {
    throw new Error(`[Auth] 权限校验失败: code=${resData?.code}, msg=${resData?.msg}`);
  }

  return resp.data.data.resPermissionLevel;
}

export async function getLatestSnapshot(
  resourceId: string,
): Promise<{ fullSnapshot: Uint8Array | null; deltas: Uint8Array[] | null; version: number }> {
  const client = await getNoteServiceClient();
  const resp = await client.get<SnapshotResponse>(
    `/internal/note/snapshot/${resourceId}`,
  );

  const resData = resp?.data;
  if (!resData || resData.code !== 200 || !resData.data) {
    throw new Error(`[Snapshot] 获取快照失败: code=${resData?.code}, msg=${resData?.msg}`);
  }

  const { fullSnapshot, version, deltas } = resp.data.data;
  return {
    fullSnapshot: fullSnapshot ? Buffer.from(fullSnapshot, 'base64') : null,
    deltas: deltas ? deltas.map(d => new Uint8Array(Buffer.from(d, 'base64'))) : null,
    version,
  };
}

export async function getOplogGranularity(): Promise<string> {
  const client = await getNoteServiceClient();
  const resp = await client.get<{ code: number; data: string }>(
    '/internal/note/config/oplog-granularity',
  );
  return resp.data.data;
}