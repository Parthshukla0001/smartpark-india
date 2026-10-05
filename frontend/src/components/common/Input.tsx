import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  leftIcon?: React.ReactNode;
}

export const Input: React.FC<InputProps> = ({
  label,
  error,
  helperText,
  leftIcon,
  id,
  style,
  ...props
}) => {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%' }}>
      {label && (
        <label
          htmlFor={inputId}
          style={{
            fontSize: '0.875rem',
            fontWeight: 500,
            color: '#CBD5E1',
          }}
        >
          {label}
        </label>
      )}

      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        {leftIcon && (
          <div
            style={{
              position: 'absolute',
              left: '12px',
              color: '#64748B',
              display: 'flex',
              alignItems: 'center',
              pointerEvents: 'none',
            }}
          >
            {leftIcon}
          </div>
        )}
        <input
          id={inputId}
          style={{
            width: '100%',
            backgroundColor: '#0B0F19',
            border: error
              ? '1px solid #EF4444'
              : '1px solid rgba(255, 255, 255, 0.12)',
            borderRadius: '8px',
            padding: leftIcon ? '10px 12px 10px 38px' : '10px 14px',
            color: '#F8FAFC',
            fontSize: '0.925rem',
            outline: 'none',
            transition: 'border-color 0.15s ease',
            ...style,
          }}
          onFocus={(e) => {
            if (!error) e.target.style.borderColor = '#F59E0B';
            props.onFocus?.(e);
          }}
          onBlur={(e) => {
            if (!error) e.target.style.borderColor = 'rgba(255, 255, 255, 0.12)';
            props.onBlur?.(e);
          }}
          {...props}
        />
      </div>

      {error && (
        <span style={{ fontSize: '0.8rem', color: '#EF4444', marginTop: '2px' }}>
          {error}
        </span>
      )}
      {!error && helperText && (
        <span style={{ fontSize: '0.8rem', color: '#64748B', marginTop: '2px' }}>
          {helperText}
        </span>
      )}
    </div>
  );
};
