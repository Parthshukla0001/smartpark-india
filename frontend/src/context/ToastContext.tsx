import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, AlertTriangle, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastMessage {
  id: string;
  type: ToastType;
  message: string;
  title?: string;
}

interface ToastContextType {
  toasts: ToastMessage[];
  addToast: (type: ToastType, message: string, title?: string) => void;
  removeToast: (id: string) => void;
  success: (message: string, title?: string) => void;
  error: (message: string, title?: string) => void;
  info: (message: string, title?: string) => void;
  warning: (message: string, title?: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback(
    (type: ToastType, message: string, title?: string) => {
      const id = 'toast-' + Math.random().toString(36).substring(2, 9);
      const newToast: ToastMessage = { id, type, message, title };

      setToasts((prev) => [...prev, newToast]);

      setTimeout(() => {
        removeToast(id);
      }, 4500);
    },
    [removeToast]
  );

  const success = useCallback((message: string, title?: string) => addToast('success', message, title), [addToast]);
  const error = useCallback((message: string, title?: string) => addToast('error', message, title), [addToast]);
  const info = useCallback((message: string, title?: string) => addToast('info', message, title), [addToast]);
  const warning = useCallback((message: string, title?: string) => addToast('warning', message, title), [addToast]);

  return (
    <ToastContext.Provider value={{ toasts, addToast, removeToast, success, error, info, warning }}>
      {children}
      <div className="toast-viewport">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="toast-item"
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '12px',
              borderLeft: `4px solid ${
                t.type === 'success'
                  ? '#10B981'
                  : t.type === 'error'
                  ? '#EF4444'
                  : t.type === 'warning'
                  ? '#F59E0B'
                  : '#0284C7'
              }`,
            }}
          >
            <div style={{ flexShrink: 0, marginTop: '2px' }}>
              {t.type === 'success' && <CheckCircle2 size={18} color="#10B981" />}
              {t.type === 'error' && <AlertCircle size={18} color="#EF4444" />}
              {t.type === 'warning' && <AlertTriangle size={18} color="#F59E0B" />}
              {t.type === 'info' && <Info size={18} color="#0284C7" />}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              {t.title && (
                <div style={{ fontSize: '0.875rem', fontWeight: 600, color: '#F8FAFC', marginBottom: '2px' }}>
                  {t.title}
                </div>
              )}
              <div style={{ fontSize: '0.85rem', color: '#CBD5E1', lineHeight: '1.4' }}>
                {t.message}
              </div>
            </div>
            <button
              onClick={() => removeToast(t.id)}
              style={{
                background: 'transparent',
                border: 'none',
                color: '#64748B',
                cursor: 'pointer',
                padding: '2px',
                display: 'flex',
              }}
            >
              <X size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
