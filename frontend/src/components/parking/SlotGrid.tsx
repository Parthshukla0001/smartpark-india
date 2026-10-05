import React from 'react';
import { ParkingSlot, SlotStatus, SlotType } from '../../types';
import { Zap, Accessibility, ShieldAlert, Check } from 'lucide-react';

interface SlotGridProps {
  slots: ParkingSlot[];
  selectedSlotId?: number | null;
  onSelectSlot?: (slot: ParkingSlot) => void;
  baseHourlyRate?: number;
  interactive?: boolean;
}

export const SlotGrid: React.FC<SlotGridProps> = ({
  slots,
  selectedSlotId,
  onSelectSlot,
  baseHourlyRate = 50,
  interactive = true,
}) => {
  if (slots.length === 0) {
    return (
      <div
        style={{
          padding: '3rem',
          textAlign: 'center',
          color: '#64748B',
          background: 'rgba(11, 15, 25, 0.4)',
          borderRadius: '12px',
          border: '1px dashed rgba(255, 255, 255, 0.08)',
        }}
      >
        No parking slots configured for this floor.
      </div>
    );
  }

  return (
    <div>
      {/* Legend */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          gap: '1rem',
          marginBottom: '1rem',
          padding: '0.75rem 1rem',
          backgroundColor: '#0F172A',
          borderRadius: '8px',
          border: '1px solid rgba(255, 255, 255, 0.06)',
          fontSize: '0.8rem',
        }}
      >
        <LegendItem color="#10B981" bg="rgba(16, 185, 129, 0.15)" label="Available" />
        <LegendItem color="#EF4444" bg="rgba(239, 68, 68, 0.15)" label="Occupied" />
        <LegendItem color="#F59E0B" bg="rgba(245, 158, 11, 0.15)" label="Reserved" />
        <LegendItem color="#64748B" bg="rgba(100, 116, 139, 0.15)" label="Out of Service" />
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px', color: '#94A3B8' }}>
          <Zap size={14} color="#8B5CF6" />
          <span>EV Charging Ready</span>
        </div>
      </div>

      {/* Grid of Bays */}
      <div className="slot-bay-container">
        {slots.map((slot) => {
          const isSelected = selectedSlotId === slot.id;
          const isAvailable = slot.status === 'AVAILABLE';
          const rate = slot.hourlyRateOverride ?? baseHourlyRate;

          let statusClass = 'available';
          if (slot.status === 'OCCUPIED') statusClass = 'occupied';
          else if (slot.status === 'RESERVED') statusClass = 'reserved';
          else if (slot.status === 'OUT_OF_SERVICE') statusClass = 'out-of-service';

          return (
            <div
              key={slot.id}
              onClick={() => {
                if (interactive && isAvailable && onSelectSlot) {
                  onSelectSlot(slot);
                }
              }}
              className={`parking-bay ${statusClass} ${isSelected ? 'selected' : ''}`}
              title={`Slot ${slot.slotNumber} - ${slot.status} (${slot.slotType})`}
            >
              {/* Top row: Number and EV/Handicapped icons */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span
                  style={{
                    fontWeight: 700,
                    fontSize: '1rem',
                    letterSpacing: '-0.02em',
                  }}
                >
                  {slot.slotNumber}
                </span>

                <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                  {slot.slotType === 'EV_CHARGING' && (
                    <span
                      title="EV Charging"
                      style={{
                        background: 'rgba(139, 92, 246, 0.25)',
                        padding: '2px 4px',
                        borderRadius: '4px',
                        display: 'flex',
                      }}
                    >
                      <Zap size={12} color="#A78BFA" />
                    </span>
                  )}
                  {slot.slotType === 'HANDICAPPED' && (
                    <span
                      title="Handicapped Accessible"
                      style={{
                        background: 'rgba(2, 132, 199, 0.25)',
                        padding: '2px 4px',
                        borderRadius: '4px',
                        display: 'flex',
                      }}
                    >
                      <Accessibility size={12} color="#38BDF8" />
                    </span>
                  )}
                </div>
              </div>

              {/* Middle row: Type badge */}
              <div style={{ fontSize: '0.675rem', fontWeight: 600, opacity: 0.85, textTransform: 'uppercase' }}>
                {slot.slotType.replace('_', ' ')}
              </div>

              {/* Bottom row: Rate & Status indicator */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  borderTop: '1px dashed rgba(255, 255, 255, 0.1)',
                  paddingTop: '4px',
                  fontSize: '0.75rem',
                }}
              >
                <span style={{ fontWeight: 600 }}>₹{rate}/hr</span>

                {isSelected ? (
                  <span
                    style={{
                      background: '#F59E0B',
                      color: '#0B0F19',
                      borderRadius: '50%',
                      width: '18px',
                      height: '18px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Check size={12} strokeWidth={3} />
                  </span>
                ) : (
                  <span
                    style={{
                      fontSize: '0.675rem',
                      fontWeight: 600,
                      textTransform: 'uppercase',
                    }}
                  >
                    {slot.status === 'AVAILABLE'
                      ? 'FREE'
                      : slot.status === 'OCCUPIED'
                      ? 'OCC'
                      : slot.status === 'RESERVED'
                      ? 'RES'
                      : 'OFF'}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

const LegendItem: React.FC<{ color: string; bg: string; label: string }> = ({ color, bg, label }) => {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <div
        style={{
          width: '12px',
          height: '12px',
          borderRadius: '3px',
          backgroundColor: bg,
          border: `1px solid ${color}`,
        }}
      />
      <span style={{ color: '#CBD5E1' }}>{label}</span>
    </div>
  );
};
