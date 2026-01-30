// Backend Model: SysUserRiskProfile
export interface SysUserRiskProfile {
  appUserId: string;
  currentScore: number;
  riskLevel: 'NORMAL' | 'OBSERVATION' | 'BLOCKED';
  lastUpdateTime: string; // ISO String
  description?: string;
}

// Backend Model: RiskProperties
export interface RiskProperties {
  decayRate: number;
  observationThreshold: number;
  blockThreshold: number;
  windowTtl: number;
  mlWeight: number;
  modelPath?: string; // ONNX Model Path
}

// Backend Model: SysSensitiveTable
export interface SysSensitiveTable {
  id?: number; // Optional for create
  tableName: string;
  sensitivityLevel: number; // 1-4
  coefficient: number;
}

// Backend Model: SysAuditLog
export interface SysAuditLog {
  traceId: string;
  appUserId: string;
  sqlTemplate: string;
  tableNames: string;
  riskScore: number;
  resultCount: number;
  actionTaken: 'PASS' | 'BLOCK';
  createTime: string;
  clientIp: string;
  executionTime: number;
  feedbackStatus: number; // 0-Unmarked, 1-False Positive, 2-True Positive
}

// API Responses
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

// User (Frontend Auth)
export interface User {
  id: string;
  username: string;
  role: 'admin' | 'auditor';
  avatar?: string;
}
