import React, { createContext, useContext, useState, useEffect } from 'react';
import { User, Role, AuthResponse } from '../types';
import { authApi } from '../api';
import { wsManager } from '../services/websocket';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<AuthResponse>;
  register: (data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    phoneNumber?: string;
  }) => Promise<any>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    try {
      const storedToken = localStorage.getItem('smartpark_token');
      const storedUser = localStorage.getItem('smartpark_user');

      if (storedToken && storedUser) {
        const parsedUser = JSON.parse(storedUser);
        setToken(storedToken);
        setUser(parsedUser);
        wsManager.connect(storedToken);
      }
    } catch (e) {
      console.error('Failed to restore session:', e);
      localStorage.removeItem('smartpark_token');
      localStorage.removeItem('smartpark_user');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = async (email: string, password: string): Promise<AuthResponse> => {
    const res = await authApi.login({ email, password });
    const authUser: User = {
      id: res.userId,
      email: res.email,
      role: res.role as Role,
      firstName: res.firstName,
      lastName: res.lastName,
    };

    localStorage.setItem('smartpark_token', res.accessToken);
    localStorage.setItem('smartpark_user', JSON.stringify(authUser));

    setToken(res.accessToken);
    setUser(authUser);

    wsManager.connect(res.accessToken);
    return res;
  };

  const register = async (data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    phoneNumber?: string;
  }) => {
    return authApi.register(data);
  };

  const logout = () => {
    wsManager.disconnect();
    localStorage.removeItem('smartpark_token');
    localStorage.removeItem('smartpark_user');
    setUser(null);
    setToken(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
