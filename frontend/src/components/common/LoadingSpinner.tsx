import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  message?: string;
  size?: number;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ message, size = 32 }) => {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '3rem 1.5rem',
        gap: '12px',
        color: '#F59E0B',
      }}
    >
      <Loader2 size={size} className="live-pulse" />
      {message && <span style={{ fontSize: '0.9rem', color: '#94A3B8' }}>{message}</span>}
    </div>
  );
};
