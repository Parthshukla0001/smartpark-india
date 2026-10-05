import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { parkingApi, bookingApi } from '../../api';
import { ParkingLot, ParkingFloor, ParkingSlot, Booking } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Select } from '../../components/common/Select';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { useToast } from '../../context/ToastContext';
import {
  Layers,
  Car,
  CalendarCheck,
  CheckCircle,
  AlertCircle,
  Radio,
  Clock,
  ArrowRight,
  TrendingUp,
} from 'lucide-react';

export const OperatorDashboard: React.FC = () => {
  const navigate = useNavigate();
  const toast = useToast();

  const [lots, setLots] = useState<ParkingLot[]>([]);
  const [selectedLotId, setSelectedLotId] = useState<number | null>(null);
  const [selectedLot, setSelectedLot] = useState<ParkingLot | null>(null);

  const [floors, setFloors] = useState<ParkingFloor[]>([]);
  const [allSlots, setAllSlots] = useState<ParkingSlot[]>([]);
  const [pendingBookings, setPendingBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Load facilities
  useEffect(() => {
    const loadLots = async () => {
      try {
        setLoading(true);
        const res = await parkingApi.getParkingLots({ page: 0, size: 50 });
        setLots(res.content);
        if (res.content.length > 0) {
          setSelectedLotId(res.content[0].id);
          setSelectedLot(res.content[0]);
        }
      } catch (err: any) {
        toast.error('Failed to load facilities');
      } finally {
        setLoading(false);
      }
    };

    loadLots();
  }, []);

  // Load facility details & bookings when selectedLotId changes
  useEffect(() => {
    if (!selectedLotId) return;

    const loadFacilityData = async () => {
      try {
        const foundLot = lots.find((l) => l.id === selectedLotId);
        if (foundLot) setSelectedLot(foundLot);

        const floorsData = await parkingApi.getFloors(selectedLotId);
        setFloors(floorsData);

        // Fetch slots for all floors
        const slotPromises = floorsData.map((f) => parkingApi.getSlots(f.id));
        const slotResults = await Promise.all(slotPromises);
        const combined = slotResults.flat();
        setAllSlots(combined);

        // Fetch pending bookings
        const bookingsRes = await bookingApi.getAllBookings({ status: 'PENDING', page: 0, size: 10 });
        setPendingBookings(bookingsRes.content);
      } catch (err: any) {
        console.error('Failed to load facility data:', err);
      }
    };

    loadFacilityData();
  }, [selectedLotId, lots]);

  const handleConfirm = async (id: number) => {
    try {
      await bookingApi.confirmBooking(id);
      toast.success(`Booking #${id} confirmed.`);
      setPendingBookings((prev) => prev.filter((b) => b.id !== id));
    } catch (err: any) {
      toast.error(err.message || 'Failed to confirm booking');
    }
  };

  if (loading || !selectedLot) {
    return <LoadingSpinner message="Loading operator facility operations..." />;
  }

  // Calculate live stats
  const totalSlots = allSlots.length;
  const availableSlots = allSlots.filter((s) => s.status === 'AVAILABLE').length;
  const occupiedSlots = allSlots.filter((s) => s.status === 'OCCUPIED').length;
  const reservedSlots = allSlots.filter((s) => s.status === 'RESERVED').length;
  const maintenanceSlots = allSlots.filter((s) => s.status === 'OUT_OF_SERVICE').length;
  const occupancyRate = totalSlots > 0 ? Math.round(((occupiedSlots + reservedSlots) / totalSlots) * 100) : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Header and Facility Selector */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            Facility Operations Center
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Real-time occupancy management and active booking control.
          </p>
        </div>

        <div style={{ minWidth: '260px' }}>
          <Select
            options={lots.map((l) => ({ value: l.id, label: `${l.name} (${l.city})` }))}
            value={selectedLotId || ''}
            onChange={(e) => setSelectedLotId(Number(e.target.value))}
          />
        </div>
      </div>

      {/* Live Occupancy Metric Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
          gap: '1.25rem',
        }}
      >
        <StatCard
          label="Total Capacity"
          value={totalSlots}
          subtext={`${floors.length} active floors`}
          color="#F8FAFC"
        />
        <StatCard
          label="Available Bays"
          value={availableSlots}
          subtext="Ready for booking"
          color="#10B981"
        />
        <StatCard
          label="Occupied"
          value={occupiedSlots}
          subtext="Vehicles parked on site"
          color="#EF4444"
        />
        <StatCard
          label="Reserved / Pending"
          value={reservedSlots}
          subtext="Incoming reservations"
          color="#F59E0B"
        />
        <StatCard
          label="Out of Service"
          value={maintenanceSlots}
          subtext="Under maintenance"
          color="#64748B"
        />
      </div>

      {/* Occupancy Progress Bar */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <TrendingUp size={18} color="#F59E0B" />
            <span style={{ fontSize: '1rem', fontWeight: 600, color: '#F8FAFC' }}>
              Facility Occupancy Load
            </span>
          </div>
          <span style={{ fontSize: '1.25rem', fontWeight: 800, color: occupancyRate > 85 ? '#EF4444' : '#F59E0B' }}>
            {occupancyRate}%
          </span>
        </div>

        <div
          style={{
            height: '12px',
            backgroundColor: '#0B0F19',
            borderRadius: '6px',
            overflow: 'hidden',
            display: 'flex',
          }}
        >
          <div style={{ width: `${(occupiedSlots / (totalSlots || 1)) * 100}%`, backgroundColor: '#EF4444' }} />
          <div style={{ width: `${(reservedSlots / (totalSlots || 1)) * 100}%`, backgroundColor: '#F59E0B' }} />
          <div style={{ width: `${(maintenanceSlots / (totalSlots || 1)) * 100}%`, backgroundColor: '#64748B' }} />
        </div>

        <div style={{ display: 'flex', gap: '1.5rem', marginTop: '12px', fontSize: '0.8rem', color: '#94A3B8' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#EF4444' }} />
            Occupied ({occupiedSlots})
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#F59E0B' }} />
            Reserved ({reservedSlots})
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#10B981' }} />
            Available ({availableSlots})
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#64748B' }} />
            Maintenance ({maintenanceSlots})
          </div>
        </div>
      </Card>

      {/* Pending Check-ins Queue */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <CalendarCheck size={18} color="#F59E0B" />
            <span>Pending Check-ins Queue ({pendingBookings.length})</span>
          </div>
        }
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/operator/bookings')}
            icon={<ArrowRight size={14} />}
          >
            All Bookings
          </Button>
        }
      >
        {pendingBookings.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: '#64748B', fontSize: '0.9rem' }}>
            No pending reservations awaiting confirmation at this moment.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pendingBookings.map((b) => (
              <div
                key={b.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 16px',
                  backgroundColor: '#0B0F19',
                  borderRadius: '8px',
                  border: '1px solid rgba(255, 255, 255, 0.06)',
                  flexWrap: 'wrap',
                  gap: '10px',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontWeight: 700, color: '#F8FAFC' }}>
                      Slot {b.slotNumber}
                    </span>
                    <Badge variant="warning">PENDING</Badge>
                    <span style={{ fontSize: '0.8rem', color: '#94A3B8' }}>{b.bookingReference}</span>
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#CBD5E1', marginTop: '3px' }}>
                    Vehicle: <strong>{b.vehicleRegistrationNumber}</strong> ({b.userEmail})
                  </div>
                </div>

                <div style={{ fontSize: '0.85rem', color: '#94A3B8' }}>
                  {new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                  {new Date(b.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>

                <Button
                  variant="primary"
                  size="sm"
                  icon={<CheckCircle size={14} />}
                  onClick={() => handleConfirm(b.id)}
                >
                  Confirm Arrival
                </Button>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};

const StatCard: React.FC<{ label: string; value: number; subtext: string; color: string }> = ({
  label,
  value,
  subtext,
  color,
}) => {
  return (
    <div
      style={{
        backgroundColor: '#131B2E',
        borderRadius: '12px',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        padding: '1.25rem',
      }}
    >
      <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </div>
      <div style={{ fontSize: '2rem', fontWeight: 800, color, marginTop: '4px' }}>
        {value}
      </div>
      <div style={{ fontSize: '0.8rem', color: '#64748B', marginTop: '2px' }}>
        {subtext}
      </div>
    </div>
  );
};
