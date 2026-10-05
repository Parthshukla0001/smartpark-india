import React, { useState, useEffect } from 'react';
import { vehicleApi } from '../../api';
import { Vehicle, VehicleType } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { useToast } from '../../context/ToastContext';
import { Car, Bike, Truck, Plus, Trash2, ShieldCheck } from 'lucide-react';

export const VehiclesPage: React.FC = () => {
  const toast = useToast();
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Add vehicle modal
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [regNumber, setRegNumber] = useState<string>('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('CAR');
  const [addLoading, setAddLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const fetchVehicles = async () => {
    try {
      setLoading(true);
      const data = await vehicleApi.getMyVehicles();
      setVehicles(data);
    } catch (err: any) {
      toast.error('Failed to load your vehicles');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVehicles();
  }, []);

  const handleRegister = async () => {
    if (!regNumber.trim()) {
      setErrorMsg('Registration number is required');
      return;
    }

    try {
      setAddLoading(true);
      setErrorMsg(null);
      const newVeh = await vehicleApi.registerVehicle({
        registrationNumber: regNumber.trim().toUpperCase(),
        vehicleType,
      });
      setVehicles((prev) => [...prev, newVeh]);
      setRegNumber('');
      setIsModalOpen(false);
      toast.success(`Vehicle ${newVeh.registrationNumber} added successfully!`);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to register vehicle');
    } finally {
      setAddLoading(false);
    }
  };

  const handleDelete = async (id: number, reg: string) => {
    if (!window.confirm(`Are you sure you want to remove vehicle ${reg}?`)) {
      return;
    }
    try {
      await vehicleApi.deleteVehicle(id);
      setVehicles((prev) => prev.filter((v) => v.id !== id));
      toast.success(`Vehicle ${reg} removed.`);
    } catch (err: any) {
      toast.error(err.message || 'Failed to delete vehicle');
    }
  };

  const getVehicleIcon = (type: VehicleType) => {
    switch (type) {
      case 'MOTORCYCLE':
        return <Bike size={24} color="#38BDF8" />;
      case 'SUV':
        return <Truck size={24} color="#F59E0B" />;
      default:
        return <Car size={24} color="#10B981" />;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            My Registered Vehicles
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Manage the vehicles associated with your parking reservations.
          </p>
        </div>

        <Button
          variant="primary"
          icon={<Plus size={16} />}
          onClick={() => {
            setErrorMsg(null);
            setIsModalOpen(true);
          }}
        >
          Register New Vehicle
        </Button>
      </div>

      {loading ? (
        <LoadingSpinner message="Loading your vehicles..." />
      ) : vehicles.length === 0 ? (
        <EmptyState
          icon={<Car size={32} />}
          title="No Vehicles Registered"
          description="You haven't added any vehicles yet. Register your vehicle now to reserve parking bays."
          actionText="Register Vehicle"
          onAction={() => setIsModalOpen(true)}
        />
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
            gap: '1.25rem',
          }}
        >
          {vehicles.map((v) => (
            <Card key={v.id}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div
                    style={{
                      width: '46px',
                      height: '46px',
                      borderRadius: '10px',
                      backgroundColor: 'rgba(255, 255, 255, 0.05)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {getVehicleIcon(v.vehicleType)}
                  </div>
                  <div>
                    <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#F8FAFC', letterSpacing: '0.05em' }}>
                      {v.registrationNumber}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                      {v.vehicleType}
                    </div>
                  </div>
                </div>

                <button
                  onClick={() => handleDelete(v.id, v.registrationNumber)}
                  title="Remove vehicle"
                  style={{
                    background: 'transparent',
                    border: 'none',
                    color: '#64748B',
                    padding: '6px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    display: 'flex',
                    transition: 'color 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = '#EF4444')}
                  onMouseLeave={(e) => (e.currentTarget.style.color = '#64748B')}
                >
                  <Trash2 size={16} />
                </button>
              </div>

              <div
                style={{
                  marginTop: '1.25rem',
                  paddingTop: '0.75rem',
                  borderTop: '1px solid rgba(255, 255, 255, 0.06)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  fontSize: '0.8rem',
                  color: '#94A3B8',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <ShieldCheck size={14} color="#10B981" />
                  <span>Verified & Active</span>
                </div>
                <span>Added {new Date(v.createdAt).toLocaleDateString()}</span>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Add Vehicle Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Register Vehicle"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <Input
            label="Vehicle Registration Number"
            placeholder="e.g. KA05MB9999, MH12AB1234, DL01XY5678"
            value={regNumber}
            onChange={(e) => setRegNumber(e.target.value.toUpperCase())}
            helperText="Indian registration number format without special characters"
            autoFocus
          />

          <Select
            label="Vehicle Type"
            value={vehicleType}
            onChange={(e) => setVehicleType(e.target.value as VehicleType)}
            options={[
              { value: 'CAR', label: 'Car / Hatchback / Sedan' },
              { value: 'SUV', label: 'SUV / Compact SUV' },
              { value: 'MOTORCYCLE', label: 'Motorcycle / Scooter' },
              { value: 'OTHER', label: 'Other Commercial Vehicle' },
            ]}
          />

          {errorMsg && (
            <div
              style={{
                padding: '0.75rem 1rem',
                backgroundColor: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid rgba(239, 68, 68, 0.35)',
                borderRadius: '8px',
                color: '#F87171',
                fontSize: '0.85rem',
              }}
            >
              {errorMsg}
            </div>
          )}

          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
            <Button variant="outline" onClick={() => setIsModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              isLoading={addLoading}
              onClick={handleRegister}
            >
              Register Vehicle
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
