import React, { useState, useEffect } from 'react';
import { parkingApi } from '../../api';
import { ParkingLot, ParkingFloor, ParkingSlot, SlotType } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { SlotGrid } from '../../components/parking/SlotGrid';
import { useToast } from '../../context/ToastContext';
import {
  Building2,
  Plus,
  Layers,
  MapPin,
  ChevronDown,
  ChevronUp,
  Zap,
} from 'lucide-react';

export const AdminFacilitiesPage: React.FC = () => {
  const toast = useToast();

  const [lots, setLots] = useState<ParkingLot[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [expandedLotId, setExpandedLotId] = useState<number | null>(null);

  // Active lot floors & slots
  const [floors, setFloors] = useState<ParkingFloor[]>([]);
  const [selectedFloorId, setSelectedFloorId] = useState<number | null>(null);
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [slotsLoading, setSlotsLoading] = useState<boolean>(false);

  // Create Lot Modal
  const [isLotModalOpen, setIsLotModalOpen] = useState<boolean>(false);
  const [lotForm, setLotForm] = useState({
    name: '',
    code: '',
    address: '',
    city: 'Bengaluru',
    state: 'Karnataka',
    postalCode: '560001',
    openingTime: '06:00:00',
    closingTime: '23:00:00',
    hourlyRate: 50,
  });
  const [lotSubmitting, setLotSubmitting] = useState<boolean>(false);

  // Add Floor Modal
  const [isFloorModalOpen, setIsFloorModalOpen] = useState<boolean>(false);
  const [floorNumber, setFloorNumber] = useState<number>(1);
  const [floorName, setFloorName] = useState<string>('Ground Floor');
  const [floorSubmitting, setFloorSubmitting] = useState<boolean>(false);

  // Add Slot Modal
  const [isSlotModalOpen, setIsSlotModalOpen] = useState<boolean>(false);
  const [slotNumber, setSlotNumber] = useState<string>('');
  const [slotType, setSlotType] = useState<SlotType>('REGULAR');
  const [rateOverride, setRateOverride] = useState<string>('');
  const [slotSubmitting, setSlotSubmitting] = useState<boolean>(false);

  const fetchLots = async () => {
    try {
      setLoading(true);
      const res = await parkingApi.getParkingLots({ page: 0, size: 50 });
      setLots(res.content);
      if (res.content.length > 0 && !expandedLotId) {
        setExpandedLotId(res.content[0].id);
      }
    } catch (err: any) {
      toast.error('Failed to load facilities');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLots();
  }, []);

  // Load floors when expandedLotId changes
  useEffect(() => {
    if (!expandedLotId) return;

    const loadFloors = async () => {
      try {
        const floorsData = await parkingApi.getFloors(expandedLotId);
        setFloors(floorsData);
        if (floorsData.length > 0) {
          setSelectedFloorId(floorsData[0].id);
        } else {
          setSelectedFloorId(null);
          setSlots([]);
        }
      } catch (err: any) {
        toast.error('Failed to load floors');
      }
    };

    loadFloors();
  }, [expandedLotId]);

  // Load slots when selectedFloorId changes
  useEffect(() => {
    if (!selectedFloorId) return;

    const loadSlots = async () => {
      try {
        setSlotsLoading(true);
        const slotsData = await parkingApi.getSlots(selectedFloorId);
        setSlots(slotsData);
      } catch (err: any) {
        toast.error('Failed to load slots');
      } finally {
        setSlotsLoading(false);
      }
    };

    loadSlots();
  }, [selectedFloorId]);

  const handleCreateLot = async () => {
    if (!lotForm.name || !lotForm.code || !lotForm.address) {
      toast.error('Please fill in all mandatory facility fields');
      return;
    }

    try {
      setLotSubmitting(true);
      const newLot = await parkingApi.createParkingLot(lotForm);
      toast.success(`Facility "${newLot.name}" created!`);
      setLots((prev) => [newLot, ...prev]);
      setExpandedLotId(newLot.id);
      setIsLotModalOpen(false);
    } catch (err: any) {
      toast.error(err.message || 'Failed to create facility');
    } finally {
      setLotSubmitting(false);
    }
  };

  const handleAddFloor = async () => {
    if (!expandedLotId) return;
    try {
      setFloorSubmitting(true);
      const newFloor = await parkingApi.createFloor(expandedLotId, {
        floorNumber: Number(floorNumber),
        floorName,
      });
      toast.success(`Floor "${newFloor.floorName}" added!`);
      setFloors((prev) => [...prev, newFloor]);
      setSelectedFloorId(newFloor.id);
      setIsFloorModalOpen(false);
    } catch (err: any) {
      toast.error(err.message || 'Failed to add floor');
    } finally {
      setFloorSubmitting(false);
    }
  };

  const handleAddSlot = async () => {
    if (!selectedFloorId || !slotNumber.trim()) {
      toast.error('Slot number is required');
      return;
    }

    try {
      setSlotSubmitting(true);
      const newSlot = await parkingApi.createSlot(selectedFloorId, {
        slotNumber: slotNumber.trim().toUpperCase(),
        slotType,
        hourlyRateOverride: rateOverride ? Number(rateOverride) : null,
      });
      toast.success(`Slot "${newSlot.slotNumber}" created!`);
      setSlots((prev) => [...prev, newSlot]);
      setSlotNumber('');
      setRateOverride('');
      setIsSlotModalOpen(false);
    } catch (err: any) {
      toast.error(err.message || 'Failed to create slot');
    } finally {
      setSlotSubmitting(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Loading parking facilities infrastructure..." />;
  }

  const activeLot = lots.find((l) => l.id === expandedLotId);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            Facility & Slot Configuration
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Manage station topography, multi-tier floor decks, and sensor-mapped parking bays.
          </p>
        </div>

        <Button
          variant="primary"
          icon={<Plus size={16} />}
          onClick={() => setIsLotModalOpen(true)}
        >
          Add New Facility
        </Button>
      </div>

      {/* Facilities List & Floor Configuration Panel */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 380px) 1fr', gap: '1.5rem', alignItems: 'start' }}>
        {/* Left: Facilities List */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <div style={{ fontSize: '0.9rem', fontWeight: 600, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Stations ({lots.length})
          </div>

          {lots.map((lot) => {
            const isSelected = lot.id === expandedLotId;
            return (
              <div
                key={lot.id}
                onClick={() => setExpandedLotId(lot.id)}
                style={{
                  padding: '14px 16px',
                  borderRadius: '10px',
                  backgroundColor: isSelected ? 'rgba(245, 158, 11, 0.1)' : '#131B2E',
                  border: isSelected
                    ? '1px solid #F59E0B'
                    : '1px solid rgba(255, 255, 255, 0.08)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <div style={{ fontWeight: 700, color: '#F8FAFC', fontSize: '1rem' }}>
                      {lot.name}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#94A3B8', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <MapPin size={12} color="#F59E0B" /> {lot.city}, {lot.state}
                    </div>
                  </div>
                  <Badge variant={lot.active ? 'success' : 'neutral'} size="sm">
                    {lot.code}
                  </Badge>
                </div>

                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    marginTop: '10px',
                    fontSize: '0.8rem',
                    color: '#CBD5E1',
                    borderTop: '1px solid rgba(255, 255, 255, 0.06)',
                    paddingTop: '8px',
                  }}
                >
                  <span>₹{lot.hourlyRate}/hr</span>
                  <span>{lot.totalCapacity} Slots</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right: Selected Facility Deck & Slot Grid */}
        {activeLot ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <Building2 size={20} color="#F59E0B" />
                  <span>{activeLot.name}</span>
                </div>
              }
              action={
                <Button
                  variant="outline"
                  size="sm"
                  icon={<Plus size={14} />}
                  onClick={() => {
                    setFloorNumber(floors.length + 1);
                    setFloorName(`Floor ${floors.length + 1}`);
                    setIsFloorModalOpen(true);
                  }}
                >
                  Add Floor
                </Button>
              }
            >
              {/* Floor tabs */}
              <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '8px', marginBottom: '1rem' }}>
                {floors.map((fl) => (
                  <button
                    key={fl.id}
                    onClick={() => setSelectedFloorId(fl.id)}
                    style={{
                      padding: '8px 16px',
                      borderRadius: '8px',
                      backgroundColor: selectedFloorId === fl.id ? '#F59E0B' : '#1E293B',
                      color: selectedFloorId === fl.id ? '#0B0F19' : '#CBD5E1',
                      fontWeight: 600,
                      fontSize: '0.85rem',
                      border:
                        selectedFloorId === fl.id
                          ? '1px solid #F59E0B'
                          : '1px solid rgba(255, 255, 255, 0.08)',
                      cursor: 'pointer',
                    }}
                  >
                    {fl.floorName} ({fl.totalSlots} Slots)
                  </button>
                ))}
              </div>

              {/* Slot Management inside Floor */}
              {selectedFloorId ? (
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#F8FAFC' }}>
                      Configured Bays ({slots.length})
                    </div>
                    <Button
                      variant="primary"
                      size="sm"
                      icon={<Plus size={14} />}
                      onClick={() => {
                        setSlotNumber('');
                        setIsSlotModalOpen(true);
                      }}
                    >
                      Add Slot to Floor
                    </Button>
                  </div>

                  {slotsLoading ? (
                    <LoadingSpinner message="Loading floor slots..." />
                  ) : (
                    <SlotGrid
                      slots={slots}
                      baseHourlyRate={activeLot.hourlyRate}
                      interactive={false}
                    />
                  )}
                </div>
              ) : (
                <div style={{ padding: '2rem', textAlign: 'center', color: '#64748B' }}>
                  No floors created for this facility yet. Click "Add Floor" above.
                </div>
              )}
            </Card>
          </div>
        ) : null}
      </div>

      {/* Create Facility Modal */}
      <Modal
        isOpen={isLotModalOpen}
        onClose={() => setIsLotModalOpen(false)}
        title="Add Smart Parking Facility"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input
            label="Facility Name"
            placeholder="e.g. Phoenix Marketcity South Deck"
            value={lotForm.name}
            onChange={(e) => setLotForm({ ...lotForm, name: e.target.value })}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <Input
              label="Station Code"
              placeholder="e.g. BLR-WFD-01"
              value={lotForm.code}
              onChange={(e) => setLotForm({ ...lotForm, code: e.target.value.toUpperCase() })}
            />
            <Input
              label="City"
              value={lotForm.city}
              onChange={(e) => setLotForm({ ...lotForm, city: e.target.value })}
            />
          </div>

          <Input
            label="Street Address"
            placeholder="e.g. Whitefield Main Road, Mahadevapura"
            value={lotForm.address}
            onChange={(e) => setLotForm({ ...lotForm, address: e.target.value })}
          />

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <Input
              label="State"
              value={lotForm.state}
              onChange={(e) => setLotForm({ ...lotForm, state: e.target.value })}
            />
            <Input
              label="Postal Code"
              value={lotForm.postalCode}
              onChange={(e) => setLotForm({ ...lotForm, postalCode: e.target.value })}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
            <Input
              label="Opens (HH:MM:SS)"
              value={lotForm.openingTime}
              onChange={(e) => setLotForm({ ...lotForm, openingTime: e.target.value })}
            />
            <Input
              label="Closes (HH:MM:SS)"
              value={lotForm.closingTime}
              onChange={(e) => setLotForm({ ...lotForm, closingTime: e.target.value })}
            />
            <Input
              label="Rate (₹/hr)"
              type="number"
              value={lotForm.hourlyRate}
              onChange={(e) => setLotForm({ ...lotForm, hourlyRate: Number(e.target.value) })}
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
            <Button variant="outline" onClick={() => setIsLotModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" isLoading={lotSubmitting} onClick={handleCreateLot}>
              Save Facility
            </Button>
          </div>
        </div>
      </Modal>

      {/* Add Floor Modal */}
      <Modal
        isOpen={isFloorModalOpen}
        onClose={() => setIsFloorModalOpen(false)}
        title="Add Floor to Facility"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input
            label="Floor Number"
            type="number"
            value={floorNumber}
            onChange={(e) => setFloorNumber(Number(e.target.value))}
          />
          <Input
            label="Floor Name"
            placeholder="e.g. Lower Basement 1, Ground Floor"
            value={floorName}
            onChange={(e) => setFloorName(e.target.value)}
          />

          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
            <Button variant="outline" onClick={() => setIsFloorModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" isLoading={floorSubmitting} onClick={handleAddFloor}>
              Add Floor
            </Button>
          </div>
        </div>
      </Modal>

      {/* Add Slot Modal */}
      <Modal
        isOpen={isSlotModalOpen}
        onClose={() => setIsSlotModalOpen(false)}
        title="Create Parking Bay"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Input
            label="Slot Number"
            placeholder="e.g. G-101 or B1-04"
            value={slotNumber}
            onChange={(e) => setSlotNumber(e.target.value.toUpperCase())}
            autoFocus
          />

          <Select
            label="Slot Classification"
            value={slotType}
            onChange={(e) => setSlotType(e.target.value as SlotType)}
            options={[
              { value: 'REGULAR', label: 'Regular Bay' },
              { value: 'EV_CHARGING', label: 'EV Fast Charging Bay' },
              { value: 'HANDICAPPED', label: 'Handicapped Accessible Bay' },
              { value: 'COMPACT', label: 'Compact / Two-Wheeler' },
              { value: 'LARGE', label: 'Large SUV / Commercial' },
            ]}
          />

          <Input
            label="Hourly Rate Override (Optional ₹)"
            placeholder="Leave empty to inherit standard rate"
            type="number"
            value={rateOverride}
            onChange={(e) => setRateOverride(e.target.value)}
          />

          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
            <Button variant="outline" onClick={() => setIsSlotModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" isLoading={slotSubmitting} onClick={handleAddSlot}>
              Create Bay
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
