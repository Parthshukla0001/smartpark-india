import React from 'react';

interface CardProps {
  children: React.ReactNode;
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  action?: React.ReactNode;
  footer?: React.ReactNode;
  style?: React.CSSProperties;
  className?: string;
  onClick?: () => void;
}

export const Card: React.FC<CardProps> = ({
  children,
  title,
  subtitle,
  action,
  footer,
  style,
  onClick,
}) => {
  return (
    <div
      onClick={onClick}
      style={{
        background: '#131B2E',
        borderRadius: '12px',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        boxShadow: '0 4px 20px -2px rgba(0, 0, 0, 0.4)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        transition: onClick ? 'transform 0.15s ease, border-color 0.15s ease' : undefined,
        cursor: onClick ? 'pointer' : undefined,
        ...style,
      }}
    >
      {(title || action) && (
        <div
          style={{
            padding: '1.25rem 1.5rem',
            borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
          }}
        >
          <div>
            <div style={{ fontSize: '1.05rem', fontWeight: 600, color: '#F8FAFC' }}>
              {title}
            </div>
            {subtitle && (
              <div style={{ fontSize: '0.85rem', color: '#94A3B8', marginTop: '2px' }}>
                {subtitle}
              </div>
            )}
          </div>
          {action && <div>{action}</div>}
        </div>
      )}

      <div style={{ padding: '1.5rem', flex: 1 }}>{children}</div>

      {footer && (
        <div
          style={{
            padding: '1rem 1.5rem',
            background: 'rgba(0, 0, 0, 0.2)',
            borderTop: '1px solid rgba(255, 255, 255, 0.06)',
          }}
        >
          {footer}
        </div>
      )}
    </div>
  );
};
