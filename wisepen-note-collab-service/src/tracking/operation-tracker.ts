import { Room, OplogEntry } from '../types';


/** 每个房间的待发送操作日志缓冲区 */
const oplogBuffers = new Map<string, OplogEntry[]>();

export function trackOperation(
  room: Room,
  userId: string,
  operationType: string,
  delta: any[] // Yjs 传递过来的具体修改细节
): void {
  let buffer = oplogBuffers.get(room.resourceId);
  if (!buffer) {
    buffer = [];
    oplogBuffers.set(room.resourceId, buffer);
  }

  const now = Date.now();
  // 房间级别的粒度配置（未来可从数据库加载，默认为 3000ms）
  const mergeWindow = room.oplogGranularityMs || 3000;

  if (mergeWindow > 0 && buffer.length > 0) {
    const last = buffer[buffer.length - 1];
    if (
      last.userId === userId &&
      last.operationType === operationType &&
      now - last.timestamp < mergeWindow
    ) {
      last.mergedCount += 1;
      last.timestamp = now;
      if (delta) {
        last.details = last.details || [];
        last.details.push(delta); 
      }
      return;
    }
  }

  buffer.push({
    userId,
    operationType,
    timestamp: now,
    mergedCount: 1,
    details: delta ? [delta] : [],
  });
}

/**
 * 取出并清空指定房间的操作日志缓冲区
 */
export function drainOplog(resourceId: string): OplogEntry[] {
  const buffer = oplogBuffers.get(resourceId);
  if (!buffer || buffer.length === 0) return [];
  oplogBuffers.set(resourceId, []);
  return buffer;
}

export function clearOplog(resourceId: string): void {
  oplogBuffers.delete(resourceId);
}