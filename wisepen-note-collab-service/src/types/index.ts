import { WebSocket } from 'ws';
import * as Y from 'yjs';
import * as awarenessProtocol from 'y-protocols/awareness';

export interface ClientIntent {
  operationType: OperationType;
  source?: string;
  userId?: string; 
}

export type OperationType =
  | 'PASTE'
  | 'UNDO'
  | 'REDO'
  | 'OTHER'
  | 'KEYBOARD';

export type OplogGranularity = 'FINE' | 'NORMAL' | 'COARSE';

export interface UserConnection {
  ws: WebSocket;
  userId: string;
  resourceId: string;

  // SharedWorker (多 Tab 共享连接)
  // HTML5 的 SharedWorker 技术可使得若干标签页在本地共用 WebSocket 连接到服务器
  clientIds: Set<number>;
}

export interface Room {
  // 资源ID
  resourceId: string;
  // 资源对应的 YDoc
  yDoc: Y.Doc;

  awareness: awarenessProtocol.Awareness;

  // 用户连接
  connections: Map<WebSocket, UserConnection>;
  // 上一个状态向量
  prevStateVector: Uint8Array | null;
  // 当前版本
  currentVersion: number;
  
  dirty: boolean;
  // 延时销毁定时器
  idleTimer: ReturnType<typeof setTimeout> | null;

  // 攒批缓冲区
  pendingBroadcasts: Uint8Array[];
  // 攒批定时器
  flushBroadcastsTimer: NodeJS.Timeout | null;

  activeUsersInWindow: Set<string>;

  oplogGranularityMs: number;
}

export interface NoteSnapshotMessage {
  resourceId: string;
  version: number;
  type: 'FULL' | 'DELTA';
  data: string; // base64
  plainText?: string;
  updatedBy: string[];
}

export interface OplogEntry {
  userId: string;
  operationType: string;
  updateData?: string; // base64
  contentSummary?: string;
  timestamp: number;
  mergedCount: number;
  details?: any[];
}

export interface NoteOperationLogMessage {
  resourceId: string;
  entries: OplogEntry[];
}

export interface PermissionCheckResponse {
  code: number;
  msg: string;
  data: {
    resPermissionLevel: string;
    permissionSource?: string;
  };
}

export interface SnapshotResponse {
  code: number;
  msg: string;
  data: {
    resourceId: string;
    fullSnapshot: string | null; // base64
    deltas?: string[] | null;    // base64 array
    version: number;
  };
}
