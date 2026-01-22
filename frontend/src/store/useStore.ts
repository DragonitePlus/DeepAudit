import { create } from 'zustand';
import { User, UserRisk, RiskAlgorithm, SensitiveTable } from '../types';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (user: User, token: string) => void;
  logout: () => void;
}

interface UIState {
  theme: 'light' | 'dark';
  primaryColor: string;
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
  setTheme: (theme: 'light' | 'dark') => void;
  setPrimaryColor: (color: string) => void;
}

interface DataState {
  riskOverview: any | null; // Replace any with proper type if available
  userRisks: UserRisk[];
  algorithms: RiskAlgorithm[];
  sensitiveTables: SensitiveTable[];
  setRiskOverview: (data: any) => void;
  setUserRisks: (data: UserRisk[]) => void;
  setAlgorithms: (data: RiskAlgorithm[]) => void;
  setSensitiveTables: (data: SensitiveTable[]) => void;
}

type AppState = AuthState & UIState & DataState;

export const useStore = create<AppState>((set) => ({
  // Auth
  user: null,
  token: localStorage.getItem('token'),
  isAuthenticated: !!localStorage.getItem('token'),
  login: (user, token) => {
    localStorage.setItem('token', token);
    set({ user, token, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem('token');
    set({ user: null, token: null, isAuthenticated: false });
  },

  // UI
  theme: 'light',
  primaryColor: '#1E3A8A', // Default Blue
  sidebarCollapsed: false,
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
  setTheme: (theme) => set({ theme }),
  setPrimaryColor: (color) => set({ primaryColor: color }),

  // Data
  riskOverview: null,
  userRisks: [],
  algorithms: [],
  sensitiveTables: [],
  setRiskOverview: (data) => set({ riskOverview: data }),
  setUserRisks: (data) => set({ userRisks: data }),
  setAlgorithms: (data) => set({ algorithms: data }),
  setSensitiveTables: (data) => set({ sensitiveTables: data }),
}));
