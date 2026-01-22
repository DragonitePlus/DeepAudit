import axios from 'axios';
import { 
  RiskProperties, 
  SysUserRiskProfile, 
  SysSensitiveTable, 
  PageResult 
} from '../types';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// Risk Configuration Services
export const riskConfigService = {
  getConfig: async () => {
    const response = await api.get<RiskProperties>('/risk/config');
    return response.data;
  },
  updateConfig: async (config: RiskProperties) => {
    const response = await api.post<string>('/risk/config', config);
    return response.data;
  },
};

// User Risk Profile Services
export const userRiskService = {
  getProfiles: async (page: number = 1, size: number = 10) => {
    const response = await api.get<PageResult<SysUserRiskProfile>>('/risk/users', {
      params: { page, size },
    });
    return response.data;
  },
  getStats: async () => {
    const response = await api.get<{ totalUsers: number; monitoredUsers: number }>('/risk/stats');
    return response.data;
  },
};

// Sensitive Table Services
export const sensitiveTableService = {
  getAll: async () => {
    const response = await api.get<SysSensitiveTable[]>('/sensitive-tables');
    return response.data;
  },
  create: async (table: SysSensitiveTable) => {
    const response = await api.post<string>('/sensitive-tables', table);
    return response.data;
  },
  update: async (id: number, table: SysSensitiveTable) => {
    const response = await api.put<string>(`/sensitive-tables/${id}`, table);
    return response.data;
  },
  delete: async (id: number) => {
    const response = await api.delete<string>(`/sensitive-tables/${id}`);
    return response.data;
  },
};

// Audit Services
export const auditService = {
  getTrend: async () => {
    const response = await api.get<{ timeSlot: string; totalScore: number }[]>('/audit/trend');
    return response.data;
  }
};

export default api;
