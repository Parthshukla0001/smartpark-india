import React from 'react';
import { Button } from './Button';

interface EmptyStateProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  actionText?: string;
  onAction?: () => void;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon,
  title,
  description,
  actionText,
  onAction,
}) => {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '3rem 1.5rem',
        textAlign: 'center',
        backgroundColor: 'rgba(19, 27, 46, 0.4)',
        borderRadius: '12px',
        border: '1px dashed rgba(255, 255, 255, 0.1)',
      }}
    >
      <div
        style={{
          width: '56px',
          height: '56px',
          borderRadius: '28px',
          background: 'rgba(245, 158, 11, 0.1)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#F59E0B',
          marginBottom: '1rem',
        }}
      >
        {icon}
      </div>
      <h3 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#F8FAFC', marginBottom: '0.5rem' }}>
        {title}
      </h3>
      <p style={{ fontSize: '0.9rem', color: '#94A3B8', maxWidth: '400px', marginBottom: actionText ? '1.5rem' : '0' }}>
        {description}
      </p>
      {actionText && onAction && (
        <Button variant="primary" onClick={onAction}>
          {actionText}
        </Button>
      )}
    </div>
  );
};
