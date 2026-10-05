import React, { useState, useEffect, useCallback } from 'react';
import { parkingApi } from '../../api';
import { ParkingLot, ParkingFloor, ParkingSlot, SlotStatus, ParkingEventPayload } from '../../types';
import { SlotGrid } from '../../components/parking/SlotGrid';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { Select } from '../../components/common/Select';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { useToast } from '../../context/ToastContext';
import { useParkingLotLiveUpdates } from '../../services/websocket';
import { Layers, Radio, Wrench, CheckCircle, AlertTriangle } from 'lucide-react';

export const OperatorFloorsPage: React.FC = () => {
  const toast = useToast();

  const [lots, setLots] = useState<ParkingLot[]>([]);
  const [selectedLotId, setSelectedLotId] = useState<number | null>(null);
  const [floors, setFloors] = useState<ParkingFloor[]>([]);
  const [selectedFloorId, setSelectedFloorId] = useState<number | null>(null);
  const [slots, setSlots] = useState<ParkingSlot[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [slotsLoading, setSlotsLoading] = useState<boolean>(false);

  // Slot management modal
  const [inspectSlot, setInspectSlot] = useState<ParkingSlot | null>(null);
  const [statusUpdateLoading, setStatusUpdateLoading] = useState<boolean>(false);

  // Live WebSocket updates
  const handleWsEvent = useCallback((event: ParkingEventPayload) => {
    toast.info(
      `Slot ${event.slotNumber || ''}: Status shifted to ${event.slotStatus || event.bookingStatus}`,
      'Live Telemetry'
    );
    if (event.slotId) {
      setSlots((prev) =>
        prev.map((s) => (s.id === event.slotId ? { ...s, status: (event.slotStatus as any) || s.status } : s))
      );
    }
  }, [toast]);

  const { isConnected } = useParkingLotLiveUpdates(selectedLotId || undefined, handleWsEvent);

  useEffect(() => {
    const loadFacilities = async () => {
      try {
        setLoading(true);
        const res = await parkingApi.getParkingLots({ page: 0, size: 50 });
        setLots(res.content);
        if (res.content.length > 0) {
          setSelectedLotId(res.content[0].id);
        }
      } catch (err: any) {
        toast.error('Failed to load facilities');
      } finally {
        setLoading(false);
      }
    };

    loadFacilities();
  }, []);

  useEffect(() => {
    if (!selectedLotId) return;

    const loadFloors = async () => {
      try {
        const floorsData = await parkingApi.getFloors(selectedLotId);
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
  }, [selectedLotId]);

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

  const handleToggleStatus = async (newStatus: SlotStatus) => {
    if (!inspectSlot) return;
    try {
      setStatusUpdateLoading(true);
      const updated = await parkingApi.updateSlotStatus(inspectSlot.id, newStatus);
      toast.success(`Slot ${updated.slotNumber} updated to ${newStatus}`);
      setSlots((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
      setInspectSlot(null);
    } catch (err: any) {
      toast.error(err.message || 'Failed to update slot status');
    } finally {
      setStatusUpdateLoading(false);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Loading live floor layout..." />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            Live Floor Plan & Slot Control
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Interactive floor-by-floor slot status map with real-time hardware telemetry.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 14px',
              borderRadius: '9999px',
              backgroundColor: isConnected ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
              border: `1px solid ${isConnected ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.25)'}`,
              fontSize: '0.8rem',
              fontWeight: 600,
              color: isConnected ? '#34D399' : '#F87171',
            }}
          >
            <Radio size={14} className={isConnected ? 'live-pulse' : undefined} />
            <span>{isConnected ? 'LIVE STOMP ACTIVE' : 'CONNECTING...'}</span>
          </div>

          <div style={{ minWidth: '240px' }}>
            <Select
              options={lots.map((l) => ({ value: l.id, label: `${l.name} (${l.city})` }))}
              value={selectedLotId || ''}
              onChange={(e) => setSelectedLotId(Number(e.target.value))}
            />
          </div>
        </div>
      </div>

      {/* Floor Selection */}
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
              border:
                selectedFloorId === floor.id
                  ? '1px solid #F59E0B'
                  : '1px solid rgba(255, 255, 255, 0.08)',
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            {floor.floorName} ({floor.totalSlots} Slots)
          </button>
        ))}
      </div>

      {/* Floor Map Card */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Layers size={18} color="#F59E0B" />
            <span>
              {floors.find((f) => f.id === selectedFloorId)?.floorName || 'Floor'} Layout
            </span>
            <span style={{ fontSize: '0.8rem', color: '#94A3B8', fontWeight: 400 }}>
              (Click any bay to inspect or modify operational status)
            </span>
          </div>
        }
      >
        {slotsLoading ? (
          <LoadingSpinner message="Refreshing floor telemetry..." />
        ) : (
          <SlotGrid
            slots={slots}
            onSelectSlot={(slot) => setInspectSlot(slot)}
            interactive={true}
          />
        )}
      </Card>

      {/* Operator Slot Inspection & Maintenance Modal */}
      <Modal
        isOpen={!!inspectSlot}
        onClose={() => setInspectSlot(null)}
        title="Slot Inspection & Operational Override"
      >
        {inspectSlot && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div
              style={{
                backgroundColor: '#0B0F19',
                padding: '1.25rem',
                borderRadius: '8px',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '1rem',
              }}
            >
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Slot Number</div>
                <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#F59E0B' }}>
                  {inspectSlot.slotNumber}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Current Status</div>
                <div style={{ marginTop: '4px' }}>
                  <Badge
                    variant={
                      inspectSlot.status === 'AVAILABLE'
                        ? 'success'
                        : inspectSlot.status === 'OCCUPIED'
                        ? 'danger'
                        : inspectSlot.status === 'RESERVED'
                        ? 'warning'
                        : 'neutral'
                    }
                  >
                    {inspectSlot.status}
                  </Badge>
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Slot Classification</div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#F8FAFC', marginTop: '2px' }}>
                  {inspectSlot.slotType.replace('_', ' ')}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Hardware Status</div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#10B981', marginTop: '2px' }}>
                  Online (Telemetry OK)
                </div>
              </div>
            </div>

            {/* Maintenance Action Buttons */}
            <div>
              <div style={{ fontSize: '0.875rem', fontWeight: 600, color: '#CBD5E1', marginBottom: '8px' }}>
                Operational State Changes
              </div>

              <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                {inspectSlot.status !== 'AVAILABLE' && (
                  <Button
                    variant="primary"
                    size="sm"
                    icon={<CheckCircle size={14} />}
                    isLoading={statusUpdateLoading}
                    onClick={() => handleToggleStatus('AVAILABLE')}
                  >
                    Set Available
                  </Button>
                )}

                {inspectSlot.status !== 'OUT_OF_SERVICE' && (
                  <Button
                    variant="danger"
                    size="sm"
                    icon={<Wrench size={14} />}
                    isLoading={statusUpdateLoading}
                    onClick={() => handleToggleStatus('OUT_OF_SERVICE')}
                  >
                    Take Out of Service (Maintenance)
                  </Button>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
              <Button variant="outline" onClick={() => setInspectSlot(null)}>
                Close
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
