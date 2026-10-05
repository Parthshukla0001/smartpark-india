import React from 'react';

interface SelectOption {
  value: string | number;
  label: string;
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: SelectOption[];
}

export const Select: React.FC<SelectProps> = ({
  label,
  error,
  options,
  id,
  style,
  ...props
}) => {
  const selectId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%' }}>
      {label && (
        <label
          htmlFor={selectId}
          style={{
            fontSize: '0.875rem',
            fontWeight: 500,
            color: '#CBD5E1',
          }}
        >
          {label}
        </label>
      )}

      <select
        id={selectId}
        style={{
          width: '100%',
          backgroundColor: '#0B0F19',
          border: error
            ? '1px solid #EF4444'
            : '1px solid rgba(255, 255, 255, 0.12)',
          borderRadius: '8px',
          padding: '10px 14px',
          color: '#F8FAFC',
          fontSize: '0.925rem',
          outline: 'none',
          cursor: 'pointer',
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
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value} style={{ backgroundColor: '#131B2E', color: '#F8FAFC' }}>
            {opt.label}
          </option>
        ))}
      </select>

      {error && (
        <span style={{ fontSize: '0.8rem', color: '#EF4444', marginTop: '2px' }}>
          {error}
        </span>
      )}
    </div>
  );
};
