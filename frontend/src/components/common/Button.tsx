import React from 'react';
import { Loader2 } from 'lucide-react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  icon?: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  icon,
  disabled,
  style,
  ...props
}) => {
  const baseStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '8px',
    borderRadius: '8px',
    fontWeight: 600,
    fontSize: size === 'sm' ? '0.8125rem' : size === 'lg' ? '1rem' : '0.875rem',
    padding: size === 'sm' ? '6px 12px' : size === 'lg' ? '12px 24px' : '8px 16px',
    transition: 'all 0.15s ease',
    border: '1px solid transparent',
    outline: 'none',
    cursor: disabled || isLoading ? 'not-allowed' : 'pointer',
    opacity: disabled || isLoading ? 0.6 : 1,
    whiteSpace: 'nowrap',
    ...style,
  };

  let variantStyle: React.CSSProperties = {};

  if (variant === 'primary') {
    variantStyle = {
      backgroundColor: '#F59E0B',
      color: '#0B0F19',
      boxShadow: '0 1px 2px rgba(0, 0, 0, 0.2)',
    };
  } else if (variant === 'secondary') {
    variantStyle = {
      backgroundColor: '#1E293B',
      color: '#F8FAFC',
      border: '1px solid rgba(255, 255, 255, 0.12)',
    };
  } else if (variant === 'outline') {
    variantStyle = {
      backgroundColor: 'transparent',
      color: '#F8FAFC',
      border: '1px solid rgba(255, 255, 255, 0.2)',
    };
  } else if (variant === 'danger') {
    variantStyle = {
      backgroundColor: 'rgba(239, 68, 68, 0.15)',
      color: '#EF4444',
      border: '1px solid rgba(239, 68, 68, 0.35)',
    };
  } else if (variant === 'ghost') {
    variantStyle = {
      backgroundColor: 'transparent',
      color: '#94A3B8',
    };
  }

  return (
    <button
      disabled={disabled || isLoading}
      style={{ ...baseStyle, ...variantStyle }}
      {...props}
    >
      {isLoading ? <Loader2 size={16} className="live-pulse" /> : icon}
      {children}
    </button>
  );
};
