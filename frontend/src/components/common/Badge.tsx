import React from 'react';

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'neutral' | 'purple';

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  size?: 'sm' | 'md';
  style?: React.CSSProperties;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'neutral',
  size = 'md',
  style,
}) => {
  let bg = 'rgba(148, 163, 184, 0.15)';
  let color = '#94A3B8';
  let border = 'rgba(148, 163, 184, 0.25)';

  if (variant === 'success') {
    bg = 'rgba(16, 185, 129, 0.15)';
    color = '#34D399';
    border = 'rgba(16, 185, 129, 0.3)';
  } else if (variant === 'danger') {
    bg = 'rgba(239, 68, 68, 0.15)';
    color = '#F87171';
    border = 'rgba(239, 68, 68, 0.3)';
  } else if (variant === 'warning') {
    bg = 'rgba(245, 158, 11, 0.15)';
    color = '#FBBF24';
    border = 'rgba(245, 158, 11, 0.3)';
  } else if (variant === 'info') {
    bg = 'rgba(2, 132, 199, 0.15)';
    color = '#38BDF8';
    border = 'rgba(2, 132, 199, 0.3)';
  } else if (variant === 'purple') {
    bg = 'rgba(139, 92, 246, 0.15)';
    color = '#A78BFA';
    border = 'rgba(139, 92, 246, 0.3)';
  }

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        fontSize: size === 'sm' ? '0.7rem' : '0.785rem',
        fontWeight: 600,
        padding: size === 'sm' ? '2px 6px' : '3px 10px',
        borderRadius: '9999px',
        backgroundColor: bg,
        color: color,
        border: `1px solid ${border}`,
        letterSpacing: '0.025em',
        textTransform: 'uppercase',
        ...style,
      }}
    >
      {children}
    </span>
  );
};
