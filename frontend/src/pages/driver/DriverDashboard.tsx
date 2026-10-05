import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { bookingApi, vehicleApi, parkingApi } from '../../api';
import { Booking, Vehicle, ParkingLot } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import {
  Search,
  CalendarCheck,
  Car,
  Clock,
  MapPin,
  ArrowRight,
  ShieldCheck,
  Sparkles,
} from 'lucide-react';

export const DriverDashboard: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [activeBooking, setActiveBooking] = useState<Booking | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [popularLots, setPopularLots] = useState<ParkingLot[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const loadDashboardData = async () => {
      try {
        setLoading(true);
        const [bookingsRes, vehiclesRes, lotsRes] = await Promise.all([
          bookingApi.getMyBookings({ page: 0, size: 5 }),
          vehicleApi.getMyVehicles(),
          parkingApi.getParkingLots({ active: true, page: 0, size: 4 }),
        ]);

        const ongoing = bookingsRes.content.find(
          (b) => b.status === 'CONFIRMED' || b.status === 'PENDING'
        );
        setActiveBooking(ongoing || null);
        setVehicles(vehiclesRes);
        setPopularLots(lotsRes.content);
      } catch (err) {
        console.error('Failed to load dashboard:', err);
      } finally {
        setLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  if (loading) {
    return <LoadingSpinner message="Loading your parking dashboard..." />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Welcome Banner */}
      <div
        style={{
          background: 'linear-gradient(135deg, #1E293B 0%, #0F172A 100%)',
          borderRadius: '16px',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          padding: '2rem',
          display: 'flex',
          flexDirection: 'column',
          gap: '1rem',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div style={{ position: 'relative', zIndex: 1, maxWidth: '640px' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              borderRadius: '9999px',
              backgroundColor: 'rgba(245, 158, 11, 0.12)',
              border: '1px solid rgba(245, 158, 11, 0.25)',
              color: '#F59E0B',
              fontSize: '0.8rem',
              fontWeight: 600,
              marginBottom: '0.75rem',
            }}
          >
            <Sparkles size={14} /> Smart Urban Mobility
          </div>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 700, color: '#F8FAFC', lineHeight: '1.2' }}>
            Namaste, {user?.firstName}!
          </h1>
          <p style={{ color: '#94A3B8', marginTop: '0.5rem', fontSize: '0.975rem' }}>
            Find real-time parking spaces across Indian metro centers. Reserved instantly, guaranteed double-booking free.
          </p>

          <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem', flexWrap: 'wrap' }}>
            <Button
              variant="primary"
              size="lg"
              icon={<Search size={18} />}
              onClick={() => navigate('/driver/search')}
            >
              Find & Reserve Parking
            </Button>
            <Button
              variant="secondary"
              size="lg"
              icon={<Car size={18} />}
              onClick={() => navigate('/driver/vehicles')}
            >
              Manage Vehicles ({vehicles.length})
            </Button>
          </div>
        </div>
      </div>

      {/* Active Booking Banner */}
      {activeBooking && (
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Clock size={18} color="#10B981" />
              <span>Current Active Reservation</span>
            </div>
          }
          action={
            <Badge variant={activeBooking.status === 'CONFIRMED' ? 'success' : 'warning'}>
              {activeBooking.status}
            </Badge>
          }
          style={{ borderColor: 'rgba(16, 185, 129, 0.3)' }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '1.25rem',
              alignItems: 'center',
            }}
          >
            <div>
              <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                Facility
              </div>
              <div style={{ fontSize: '1.1rem', fontWeight: 600, color: '#F8FAFC', marginTop: '2px' }}>
                {activeBooking.parkingLotName}
              </div>
              <div style={{ fontSize: '0.85rem', color: '#CBD5E1', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '2px' }}>
                <MapPin size={14} color="#F59E0B" /> {activeBooking.city}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                Slot Assigned
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#F59E0B', marginTop: '2px' }}>
                {activeBooking.slotNumber}
              </div>
              <div style={{ fontSize: '0.85rem', color: '#94A3B8' }}>{activeBooking.floorName}</div>
            </div>

            <div>
              <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                Vehicle
              </div>
              <div style={{ fontSize: '1rem', fontWeight: 600, color: '#F8FAFC', marginTop: '2px' }}>
                {activeBooking.vehicleRegistrationNumber}
              </div>
              <div style={{ fontSize: '0.85rem', color: '#94A3B8' }}>
                Ref: {activeBooking.bookingReference}
              </div>
            </div>

            <div>
              <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                Time Window
              </div>
              <div style={{ fontSize: '0.85rem', color: '#F8FAFC', marginTop: '2px' }}>
                {new Date(activeBooking.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                {' - '}
                {new Date(activeBooking.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </div>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#10B981', marginTop: '2px' }}>
                ₹{activeBooking.totalAmount}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/driver/bookings')}
              >
                View Details
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* Popular Parking Lots */}
      <div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '1rem',
          }}
        >
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#F8FAFC' }}>
              Available Parking Facilities
            </h2>
            <p style={{ fontSize: '0.875rem', color: '#94A3B8' }}>
              Explore certified smart parking lots with real-time slot telemetry
            </p>
          </div>
          <Link
            to="/driver/search"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
              fontSize: '0.875rem',
              color: '#F59E0B',
              fontWeight: 600,
            }}
          >
            View All <ArrowRight size={16} />
          </Link>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
            gap: '1.25rem',
          }}
        >
          {popularLots.map((lot) => (
            <Card
              key={lot.id}
              onClick={() => navigate(`/driver/lots/${lot.id}`)}
              style={{ cursor: 'pointer' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 600, color: '#F8FAFC' }}>
                    {lot.name}
                  </h3>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#94A3B8', fontSize: '0.85rem', marginTop: '4px' }}>
                    <MapPin size={14} color="#F59E0B" /> {lot.city}, {lot.state}
                  </div>
                </div>
                <Badge variant={lot.active ? 'success' : 'neutral'}>
                  {lot.active ? 'OPEN' : 'CLOSED'}
                </Badge>
              </div>

              <div
                style={{
                  marginTop: '1.25rem',
                  paddingTop: '1rem',
                  borderTop: '1px solid rgba(255, 255, 255, 0.06)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <div>
                  <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Rate</div>
                  <div style={{ fontSize: '1.05rem', fontWeight: 700, color: '#F59E0B' }}>
                    ₹{lot.hourlyRate}<span style={{ fontSize: '0.75rem', fontWeight: 400, color: '#94A3B8' }}>/hr</span>
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Capacity</div>
                  <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#F8FAFC' }}>
                    {lot.totalCapacity} Slots ({lot.totalFloors} Floors)
                  </div>
                </div>

                <Button variant="primary" size="sm">
                  View Slots
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  );
};
