export interface ServiceStatus {
  username: string;
  serviceName: string;
  port: number;
  status: 'active' | 'inactive' | 'failed' | 'unknown' | 'error';
  subState: string;
  pid: string;
  since: string;
  _loading?: boolean;
}

export interface ActionResult {
  success: boolean;
  message: string;
  output?: string;
}

export interface LogResult {
  username: string;
  serviceName: string;
  lines: string[];
}

export interface PostgresStatus {
  status: string;
  message: string;
}

export interface BulkResult {
  results: Record<string, ActionResult>;
  successCount: number;
  failCount: number;
}

export interface LoginResponse {
  token: string;
  username: string;
}
