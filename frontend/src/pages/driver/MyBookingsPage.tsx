import React, { useState, useEffect } from 'react';
import { bookingApi } from '../../api';
import { Booking, BookingStatus } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { useToast } from '../../context/ToastContext';
import {
  CalendarCheck,
  Clock,
  MapPin,
  Car,
  AlertTriangle,
  XCircle,
  CheckCircle2,
  Calendar,
} from 'lucide-react';

const TABS: { label: string; status?: BookingStatus }[] = [
  { label: 'All Bookings' },
  { label: 'Confirmed', status: 'CONFIRMED' },
  { label: 'Pending', status: 'PENDING' },
  { label: 'Completed', status: 'COMPLETED' },
  { label: 'Cancelled', status: 'CANCELLED' },
  { label: 'Expired', status: 'EXPIRED' },
];

export const MyBookingsPage: React.FC = () => {
  const toast = useToast();
  const [selectedTab, setSelectedTab] = useState<number>(0);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  // Cancellation modal state
  const [cancelModalBooking, setCancelModalBooking] = useState<Booking | null>(null);
  const [cancelLoading, setCancelLoading] = useState<boolean>(false);

  const fetchBookings = async (status?: BookingStatus) => {
    try {
      setLoading(true);
      const res = await bookingApi.getMyBookings({ status, page: 0, size: 50 });
      setBookings(res.content);
    } catch (err: any) {
      toast.error('Failed to load bookings');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBookings(TABS[selectedTab].status);
  }, [selectedTab]);

  const handleCancel = async () => {
    if (!cancelModalBooking) return;
    try {
      setCancelLoading(true);
      const updated = await bookingApi.cancelBooking(cancelModalBooking.id);
      toast.success(`Booking ${updated.bookingReference} has been cancelled.`);
      setBookings((prev) =>
        prev.map((b) => (b.id === updated.id ? updated : b))
      );
      setCancelModalBooking(null);
    } catch (err: any) {
      toast.error(err.message || 'Failed to cancel booking');
    } finally {
      setCancelLoading(false);
    }
  };

  const getStatusBadge = (status: BookingStatus) => {
    switch (status) {
      case 'CONFIRMED':
        return <Badge variant="success">CONFIRMED</Badge>;
      case 'PENDING':
        return <Badge variant="warning">PENDING</Badge>;
      case 'COMPLETED':
        return <Badge variant="info">COMPLETED</Badge>;
      case 'CANCELLED':
        return <Badge variant="danger">CANCELLED</Badge>;
      case 'EXPIRED':
        return <Badge variant="neutral">EXPIRED</Badge>;
      default:
        return <Badge>{status}</Badge>;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      <div>
        <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
          My Bookings & Reservations
        </h1>
        <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
          Track active parking passes, upcoming slots, and past reservation history.
        </p>
      </div>

      {/* Filter Tabs */}
      <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px' }}>
        {TABS.map((tab, idx) => (
          <button
            key={tab.label}
            onClick={() => setSelectedTab(idx)}
            style={{
              padding: '8px 16px',
              borderRadius: '8px',
              backgroundColor: selectedTab === idx ? '#F59E0B' : '#1E293B',
              color: selectedTab === idx ? '#0B0F19' : '#CBD5E1',
              fontWeight: 600,
              fontSize: '0.85rem',
              border:
                selectedTab === idx
                  ? '1px solid #F59E0B'
                  : '1px solid rgba(255, 255, 255, 0.08)',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              transition: 'all 0.15s ease',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Bookings List */}
      {loading ? (
        <LoadingSpinner message="Retrieving your bookings..." />
      ) : bookings.length === 0 ? (
        <EmptyState
          icon={<CalendarCheck size={28} />}
          title="No Bookings Found"
          description="You don't have any bookings matching this status filter."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {bookings.map((booking) => {
            const isCancellable =
              booking.status === 'CONFIRMED' || booking.status === 'PENDING';

            return (
              <Card key={booking.id}>
                <div
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '1rem',
                  }}
                >
                  {/* Facility & Reference */}
                  <div style={{ minWidth: '220px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <span style={{ fontSize: '0.75rem', color: '#94A3B8', fontWeight: 600 }}>
                        REF: {booking.bookingReference}
                      </span>
                      {getStatusBadge(booking.status)}
                    </div>
                    <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#F8FAFC', marginTop: '4px' }}>
                      {booking.parkingLotName}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#CBD5E1', fontSize: '0.85rem', marginTop: '2px' }}>
                      <MapPin size={14} color="#F59E0B" /> {booking.city}
                    </div>
                  </div>

                  {/* Slot & Floor */}
                  <div style={{ minWidth: '130px' }}>
                    <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                      Assigned Slot
                    </div>
                    <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#F59E0B' }}>
                      {booking.slotNumber}
                    </div>
                    <div style={{ fontSize: '0.825rem', color: '#94A3B8' }}>{booking.floorName}</div>
                  </div>

                  {/* Vehicle */}
                  <div style={{ minWidth: '140px' }}>
                    <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                      Vehicle
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.95rem', fontWeight: 600, color: '#F8FAFC', marginTop: '2px' }}>
                      <Car size={16} color="#CBD5E1" />
                      <span>{booking.vehicleRegistrationNumber}</span>
                    </div>
                  </div>

                  {/* Time Window */}
                  <div style={{ minWidth: '200px' }}>
                    <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                      Schedule (IST)
                    </div>
                    <div style={{ fontSize: '0.85rem', color: '#F8FAFC', marginTop: '2px' }}>
                      {new Date(booking.startTime).toLocaleDateString([], { month: 'short', day: 'numeric' })},{' '}
                      {new Date(booking.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      {' → '}
                      {new Date(booking.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>

                  {/* Total & Action */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', marginLeft: 'auto' }}>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '0.75rem', color: '#94A3B8' }}>Total Paid</div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#10B981' }}>
                        ₹{booking.totalAmount}
                      </div>
                    </div>

                    {isCancellable && (
                      <Button
                        variant="danger"
                        size="sm"
                        icon={<XCircle size={14} />}
                        onClick={() => setCancelModalBooking(booking)}
                      >
                        Cancel Booking
                      </Button>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Cancellation Confirmation Modal */}
      <Modal
        isOpen={!!cancelModalBooking}
        onClose={() => setCancelModalBooking(null)}
        title="Cancel Parking Reservation"
      >
        {cancelModalBooking && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div
              style={{
                padding: '1rem',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.25)',
                borderRadius: '8px',
                display: 'flex',
                alignItems: 'flex-start',
                gap: '10px',
              }}
            >
              <AlertTriangle size={20} color="#EF4444" style={{ flexShrink: 0, marginTop: '2px' }} />
              <div style={{ fontSize: '0.875rem', color: '#CBD5E1', lineHeight: '1.4' }}>
                Are you sure you want to cancel booking{' '}
                <strong style={{ color: '#F8FAFC' }}>{cancelModalBooking.bookingReference}</strong> for slot{' '}
                <strong style={{ color: '#F59E0B' }}>{cancelModalBooking.slotNumber}</strong>?
                <div style={{ marginTop: '6px', fontSize: '0.8rem', color: '#F87171' }}>
                  Notice: Cancellations must be made at least 15 minutes prior to start time.
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
              <Button
                variant="outline"
                onClick={() => setCancelModalBooking(null)}
              >
                Keep Reservation
              </Button>
              <Button
                variant="danger"
                isLoading={cancelLoading}
                onClick={handleCancel}
              >
                Confirm Cancellation
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
