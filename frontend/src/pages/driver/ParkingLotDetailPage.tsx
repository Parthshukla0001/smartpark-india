import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { parkingApi, bookingApi, vehicleApi } from '../../api';
import { ParkingLot, ParkingFloor, ParkingSlot, Vehicle, ParkingEventPayload, SlotType } from '../../types';
import { SlotGrid } from '../../components/parking/SlotGrid';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { useToast } from '../../context/ToastContext';
import { useParkingLotLiveUpdates } from '../../services/websocket';
import {
  MapPin,
  Clock,
  Layers,
  Zap,
  Radio,
  ArrowLeft,
  Calendar,
  AlertCircle,
  PlusCircle,
  CheckCircle,
} from 'lucide-react';

export const ParkingLotDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const lotId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();

  const [lot, setLot] = useState<ParkingLot | null>(null);
  const [floors, setFloors] = useState<ParkingFloor[]>([]);
  const [selectedFloorId, setSelectedFloorId] = useState<number | null>(null);
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [slotsLoading, setSlotsLoading] = useState<boolean>(false);

  // Selected slot for reservation
  const [selectedSlot, setSelectedSlot] = useState<ParkingSlot | null>(null);
  const [isBookingModalOpen, setIsBookingModalOpen] = useState<boolean>(false);
  const [selectedVehicleId, setSelectedVehicleId] = useState<string>('');
  
  // Date time inputs
  const [startTime, setStartTime] = useState<string>('');
  const [endTime, setEndTime] = useState<string>('');
  const [bookingLoading, setBookingLoading] = useState<boolean>(false);
  const [bookingError, setBookingError] = useState<string | null>(null);

  // Add vehicle modal
  const [isAddVehicleOpen, setIsAddVehicleOpen] = useState<boolean>(false);
  const [regNumber, setRegNumber] = useState<string>('');
  const [vehicleType, setVehicleType] = useState<string>('CAR');
  const [addVehicleLoading, setAddVehicleLoading] = useState<boolean>(false);

  // STOMP WebSocket Live updates handler
  const handleWebSocketEvent = useCallback((event: ParkingEventPayload) => {
    toast.info(`Live Update: Slot ${event.slotNumber || ''} is now ${event.slotStatus || event.bookingStatus}`, 'Telemetry Feed');

    // Update slots in state immediately
    if (event.slotId) {
      setSlots((prev) =>
        prev.map((s) => {
          if (s.id === event.slotId) {
            return {
              ...s,
              status: (event.slotStatus as any) || (event.bookingStatus === 'CONFIRMED' || event.bookingStatus === 'PENDING' ? 'RESERVED' : 'AVAILABLE'),
            };
          }
          return s;
        })
      );
    }
  }, [toast]);

  const { isConnected } = useParkingLotLiveUpdates(lotId, handleWebSocketEvent);

  // Load Lot, Floors, Vehicles
  useEffect(() => {
    const loadLotData = async () => {
      try {
        setLoading(true);
        const [lotData, floorsData, vehiclesData] = await Promise.all([
          parkingApi.getParkingLot(lotId),
          parkingApi.getFloors(lotId),
          vehicleApi.getMyVehicles(),
        ]);
        setLot(lotData);
        setFloors(floorsData);
        setVehicles(vehiclesData);
        if (vehiclesData.length > 0) {
          setSelectedVehicleId(String(vehiclesData[0].id));
        }
        if (floorsData.length > 0) {
          setSelectedFloorId(floorsData[0].id);
        }
      } catch (err: any) {
        toast.error(err.message || 'Failed to load parking lot details');
      } finally {
        setLoading(false);
      }
    };

    if (lotId) {
      loadLotData();
    }
  }, [lotId]);

  // Load slots when selected floor changes
  useEffect(() => {
    if (!selectedFloorId) return;

    const loadSlots = async () => {
      try {
        setSlotsLoading(true);
        const slotData = await parkingApi.getSlots(selectedFloorId);
        setSlots(slotData);
      } catch (err: any) {
        toast.error('Failed to load slots for selected floor');
      } finally {
        setSlotsLoading(false);
      }
    };

    loadSlots();
  }, [selectedFloorId]);

  // Setup default times when booking modal opens
  const openBookingForSlot = (slot: ParkingSlot) => {
    setSelectedSlot(slot);
    setBookingError(null);

    // Default start time: rounded up to next 15 mins
    const now = new Date();
    const minutes = now.getMinutes();
    const roundedMinutes = Math.ceil(minutes / 15) * 15;
    const start = new Date(now.getTime() + (roundedMinutes - minutes) * 60000);
    // Default end time: 2 hours after start
    const end = new Date(start.getTime() + 2 * 60 * 60000);

    const formatForInput = (d: Date) => {
      const pad = (n: number) => n.toString().padStart(2, '0');
      const year = d.getFullYear();
      const month = pad(d.getMonth() + 1);
      const day = pad(d.getDate());
      const hours = pad(d.getHours());
      const mins = pad(d.getMinutes());
      return `${year}-${month}-${day}T${hours}:${mins}`;
    };

    setStartTime(formatForInput(start));
    setEndTime(formatForInput(end));
    setIsBookingModalOpen(true);
  };

  // Calculate estimated total
  const calculateTotal = (): { hours: number; amount: number } => {
    if (!startTime || !endTime || !selectedSlot || !lot) return { hours: 0, amount: 0 };
    const start = new Date(startTime).getTime();
    const end = new Date(endTime).getTime();
    if (end <= start) return { hours: 0, amount: 0 };
    const diffHours = (end - start) / (1000 * 60 * 60);
    const rate = selectedSlot.hourlyRateOverride ?? lot.hourlyRate;
    return {
      hours: Math.round(diffHours * 10) / 10,
      amount: Math.ceil(diffHours * rate),
    };
  };

  const handleCreateBooking = async () => {
    if (!selectedSlot || !selectedVehicleId || !startTime || !endTime) {
      setBookingError('Please select a vehicle and valid start/end times');
      return;
    }

    const startInstant = new Date(startTime).toISOString();
    const endInstant = new Date(endTime).toISOString();

    if (new Date(endInstant) <= new Date(startInstant)) {
      setBookingError('End time must be strictly after start time');
      return;
    }

    try {
      setBookingLoading(true);
      setBookingError(null);

      const booking = await bookingApi.createBooking({
        parkingSlotId: selectedSlot.id,
        vehicleId: Number(selectedVehicleId),
        startTime: startInstant,
        endTime: endInstant,
      });

      toast.success(
        `Reservation confirmed! Ref: ${booking.bookingReference}`,
        'Booking Created'
      );
      setIsBookingModalOpen(false);
      navigate('/driver/bookings');
    } catch (err: any) {
      if (err.status === 409) {
        setBookingError(
          'Double-Booking Conflict: This slot was already reserved for the selected time window. Please select another slot or adjust your time.'
        );
      } else {
        setBookingError(err.message || 'Failed to complete reservation');
      }
    } finally {
      setBookingLoading(false);
    }
  };

  const handleAddVehicle = async () => {
    if (!regNumber.trim()) return;
    try {
      setAddVehicleLoading(true);
      const newVeh = await vehicleApi.registerVehicle({
        registrationNumber: regNumber.trim().toUpperCase(),
        vehicleType: vehicleType as any,
      });
      setVehicles((prev) => [...prev, newVeh]);
      setSelectedVehicleId(String(newVeh.id));
      setRegNumber('');
      setIsAddVehicleOpen(false);
      toast.success(`Vehicle ${newVeh.registrationNumber} registered successfully`);
    } catch (err: any) {
      toast.error(err.message || 'Failed to register vehicle');
    } finally {
      setAddVehicleLoading(false);
    }
  };

  if (loading || !lot) {
    return <LoadingSpinner message="Loading parking facility & live slots..." />;
  }

  const { hours: estHours, amount: estAmount } = calculateTotal();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      {/* Top Breadcrumb & Live Indicator */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <button
          onClick={() => navigate('/driver/search')}
          style={{
            background: 'transparent',
            border: 'none',
            color: '#94A3B8',
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '0.875rem',
            cursor: 'pointer',
          }}
        >
          <ArrowLeft size={16} /> Back to Search
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              backgroundColor: isConnected ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
              border: `1px solid ${isConnected ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.25)'}`,
              padding: '4px 12px',
              borderRadius: '9999px',
              fontSize: '0.775rem',
              color: isConnected ? '#34D399' : '#F87171',
              fontWeight: 600,
            }}
          >
            <Radio size={12} className={isConnected ? 'live-pulse' : undefined} />
            <span>{isConnected ? 'LIVE STOMP TELEMETRY' : 'CONNECTING...'}</span>
          </div>
        </div>
      </div>

      {/* Facility Header Card */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#F8FAFC' }}>
                {lot.name}
              </h1>
              <Badge variant={lot.active ? 'success' : 'neutral'}>
                {lot.active ? 'OPEN NOW' : 'CLOSED'}
              </Badge>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: '#CBD5E1', fontSize: '0.9rem', marginTop: '6px' }}>
              <MapPin size={16} color="#F59E0B" />
              <span>{lot.address}, {lot.city}, {lot.state} - {lot.postalCode}</span>
            </div>

            <div style={{ display: 'flex', gap: '1.5rem', marginTop: '1rem', color: '#94A3B8', fontSize: '0.85rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Clock size={16} color="#CBD5E1" />
                <span>Operating Hours: <strong>{lot.openingTime?.substring(0, 5)} - {lot.closingTime?.substring(0, 5)} IST</strong></span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Layers size={16} color="#CBD5E1" />
                <span><strong>{lot.totalCapacity}</strong> Total Slots across <strong>{lot.totalFloors}</strong> Floors</span>
              </div>
            </div>
          </div>

          <div
            style={{
              backgroundColor: '#0B0F19',
              padding: '1rem 1.5rem',
              borderRadius: '12px',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              textAlign: 'center',
            }}
          >
            <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Standard Rate
            </div>
            <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#F59E0B' }}>
              ₹{lot.hourlyRate}
              <span style={{ fontSize: '0.85rem', fontWeight: 400, color: '#94A3B8' }}>/hr</span>
            </div>
          </div>
        </div>
      </Card>

      {/* Floor Selection Tabs */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#F8FAFC' }}>
            Select Floor
          </h2>
          <span style={{ fontSize: '0.8rem', color: '#94A3B8' }}>
            Click an available bay below to begin booking
          </span>
        </div>

        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px' }}>
          {floors.map((floor) => (
            <button
              key={floor.id}
              onClick={() => setSelectedFloorId(floor.id)}
              style={{
                padding: '8px 18px',
                borderRadius: '8px',
                backgroundColor: selectedFloorId === floor.id ? '#F59E0B' : '#1E293B',
                color: selectedFloorId === floor.id ? '#0B0F19' : '#CBD5E1',
                fontWeight: 600,
                fontSize: '0.875rem',
                border: selectedFloorId === floor.id ? '1px solid #F59E0B' : '1px solid rgba(255, 255, 255, 0.08)',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              {floor.floorName} ({floor.totalSlots} Slots)
            </button>
          ))}
        </div>
      </div>

      {/* Interactive Slot Grid */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>Parking Bays Grid</span>
            <span style={{ fontSize: '0.8rem', fontWeight: 400, color: '#94A3B8' }}>
              ({slots.filter((s) => s.status === 'AVAILABLE').length} Available)
            </span>
          </div>
        }
      >
        {slotsLoading ? (
          <LoadingSpinner message="Refreshing slot telemetry..." />
        ) : (
          <SlotGrid
            slots={slots}
            selectedSlotId={selectedSlot?.id}
            onSelectSlot={openBookingForSlot}
            baseHourlyRate={lot.hourlyRate}
            interactive={true}
          />
        )}
      </Card>

      {/* Booking Drawer / Modal */}
      <Modal
        isOpen={isBookingModalOpen}
        onClose={() => setIsBookingModalOpen(false)}
        title="Reserve Parking Slot"
      >
        {selectedSlot && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            {/* Slot Summary */}
            <div
              style={{
                backgroundColor: '#0B0F19',
                padding: '1rem',
                borderRadius: '8px',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Selected Bay</div>
                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#F59E0B' }}>
                  {selectedSlot.slotNumber}
                </div>
                <div style={{ fontSize: '0.8rem', color: '#CBD5E1' }}>
                  Type: {selectedSlot.slotType.replace('_', ' ')}
                </div>
              </div>

              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Rate</div>
                <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#F8FAFC' }}>
                  ₹{selectedSlot.hourlyRateOverride ?? lot.hourlyRate}/hr
                </div>
              </div>
            </div>

            {/* Vehicle Selection */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <label style={{ fontSize: '0.875rem', fontWeight: 500, color: '#CBD5E1' }}>
                  Select Vehicle
                </label>
                <button
                  type="button"
                  onClick={() => setIsAddVehicleOpen(true)}
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#F59E0B',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                  }}
                >
                  <PlusCircle size={14} /> Add New
                </button>
              </div>

              {vehicles.length === 0 ? (
                <div
                  style={{
                    padding: '1rem',
                    backgroundColor: 'rgba(239, 68, 68, 0.1)',
                    borderRadius: '8px',
                    border: '1px solid rgba(239, 68, 68, 0.25)',
                    fontSize: '0.85rem',
                    color: '#F87171',
                  }}
                >
                  No vehicles registered yet. Please add a vehicle before booking.
                </div>
              ) : (
                <Select
                  options={vehicles.map((v) => ({
                    value: v.id,
                    label: `${v.registrationNumber} (${v.vehicleType})`,
                  }))}
                  value={selectedVehicleId}
                  onChange={(e) => setSelectedVehicleId(e.target.value)}
                />
              )}
            </div>

            {/* Start & End Time Pickers */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <Input
                label="Start Time"
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
              <Input
                label="End Time"
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>

            {/* Price Estimation */}
            <div
              style={{
                backgroundColor: 'rgba(245, 158, 11, 0.08)',
                padding: '1rem',
                borderRadius: '8px',
                border: '1px solid rgba(245, 158, 11, 0.25)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div>
                <div style={{ fontSize: '0.8rem', color: '#F59E0B', fontWeight: 600 }}>
                  Estimated Duration
                </div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#F8FAFC' }}>
                  {estHours} Hours
                </div>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.8rem', color: '#F59E0B', fontWeight: 600 }}>
                  Total Amount
                </div>
                <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#F8FAFC' }}>
                  ₹{estAmount}
                </div>
              </div>
            </div>

            {/* Error Message */}
            {bookingError && (
              <div
                style={{
                  padding: '0.75rem 1rem',
                  backgroundColor: 'rgba(239, 68, 68, 0.15)',
                  border: '1px solid rgba(239, 68, 68, 0.35)',
                  borderRadius: '8px',
                  color: '#F87171',
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '8px',
                }}
              >
                <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                <span>{bookingError}</span>
              </div>
            )}

            {/* Submit Actions */}
            <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem' }}>
              <Button
                variant="outline"
                style={{ flex: 1 }}
                onClick={() => setIsBookingModalOpen(false)}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                style={{ flex: 2 }}
                disabled={vehicles.length === 0 || bookingLoading}
                isLoading={bookingLoading}
                onClick={handleCreateBooking}
              >
                Confirm & Reserve Slot
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Quick Add Vehicle Modal */}
      <Modal
        isOpen={isAddVehicleOpen}
        onClose={() => setIsAddVehicleOpen(false)}
        title="Register Vehicle"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input
            label="Registration Number"
            placeholder="e.g. MH12AB1234 or DL01XY5678"
            value={regNumber}
            onChange={(e) => setRegNumber(e.target.value.toUpperCase())}
            helperText="Standard Indian vehicle registration format"
          />
          <Select
            label="Vehicle Type"
            value={vehicleType}
            onChange={(e) => setVehicleType(e.target.value)}
            options={[
              { value: 'CAR', label: 'Car / Hatchback / Sedan' },
              { value: 'SUV', label: 'SUV' },
              { value: 'MOTORCYCLE', label: 'Motorcycle / Two-Wheeler' },
              { value: 'OTHER', label: 'Other Commercial' },
            ]}
          />
          <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem' }}>
            <Button variant="outline" style={{ flex: 1 }} onClick={() => setIsAddVehicleOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              style={{ flex: 2 }}
              isLoading={addVehicleLoading}
              onClick={handleAddVehicle}
            >
              Save Vehicle
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
