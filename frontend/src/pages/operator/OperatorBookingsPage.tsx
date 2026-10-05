import React, { useState, useEffect } from 'react';
import { bookingApi } from '../../api';
import { Booking, BookingStatus } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { Input } from '../../components/common/Input';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { useToast } from '../../context/ToastContext';
import {
  CalendarCheck,
  Search,
  CheckCircle,
  LogOut,
  XCircle,
  Clock,
  Car,
  Filter,
} from 'lucide-react';

const STATUS_OPTIONS: { label: string; value?: BookingStatus }[] = [
  { label: 'All Statuses' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Cancelled', value: 'CANCELLED' },
  { label: 'Expired', value: 'EXPIRED' },
];

export const OperatorBookingsPage: React.FC = () => {
  const toast = useToast();

  const [bookings, setBookings] = useState<Booking[]>([]);
  const [selectedStatus, setSelectedStatus] = useState<string>('All Statuses');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

  const fetchBookings = async (statusVal?: BookingStatus) => {
    try {
      setLoading(true);
      const res = await bookingApi.getAllBookings({
        status: statusVal,
        page: 0,
        size: 50,
      });
      setBookings(res.content);
    } catch (err: any) {
      toast.error('Failed to load system bookings');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const opt = STATUS_OPTIONS.find((s) => s.label === selectedStatus);
    fetchBookings(opt?.value);
  }, [selectedStatus]);

  const handleAction = async (id: number, action: 'confirm' | 'complete' | 'cancel' | 'expire') => {
    try {
      setActionLoadingId(id);
      let updated: Booking;
      if (action === 'confirm') updated = await bookingApi.confirmBooking(id);
      else if (action === 'complete') updated = await bookingApi.completeBooking(id);
      else if (action === 'expire') updated = await bookingApi.expireBooking(id);
      else updated = await bookingApi.cancelBooking(id);

      toast.success(`Booking #${id} updated to ${updated.status}`);
      setBookings((prev) => prev.map((b) => (b.id === id ? updated : b)));
    } catch (err: any) {
      toast.error(err.message || `Failed to execute ${action}`);
    } finally {
      setActionLoadingId(null);
    }
  };

  const filteredBookings = bookings.filter((b) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      b.bookingReference.toLowerCase().includes(q) ||
      b.vehicleRegistrationNumber.toLowerCase().includes(q) ||
      b.userEmail.toLowerCase().includes(q) ||
      b.slotNumber.toLowerCase().includes(q) ||
      b.parkingLotName.toLowerCase().includes(q)
    );
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      <div>
        <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
          Booking Lifecycle & Operations Queue
        </h1>
        <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
          Real-time check-in confirmation, vehicle checkout completion, and reservation management.
        </p>
      </div>

      {/* Filter and Search controls */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '1rem',
          alignItems: 'center',
          backgroundColor: '#131B2E',
          padding: '1.25rem',
          borderRadius: '12px',
          border: '1px solid rgba(255, 255, 255, 0.08)',
        }}
      >
        <div style={{ flex: '1 1 300px' }}>
          <Input
            placeholder="Search by Booking Ref, Vehicle Reg, Email, Slot..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            leftIcon={<Search size={18} />}
          />
        </div>

        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto' }}>
          {STATUS_OPTIONS.map((opt) => (
            <button
              key={opt.label}
              onClick={() => setSelectedStatus(opt.label)}
              style={{
                padding: '6px 14px',
                borderRadius: '8px',
                backgroundColor: selectedStatus === opt.label ? '#F59E0B' : '#1E293B',
                color: selectedStatus === opt.label ? '#0B0F19' : '#CBD5E1',
                fontSize: '0.825rem',
                fontWeight: 600,
                border:
                  selectedStatus === opt.label
                    ? '1px solid #F59E0B'
                    : '1px solid rgba(255, 255, 255, 0.08)',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Table / List */}
      {loading ? (
        <LoadingSpinner message="Loading operational bookings..." />
      ) : filteredBookings.length === 0 ? (
        <EmptyState
          icon={<CalendarCheck size={28} />}
          title="No Bookings Found"
          description="There are no reservations matching your filter criteria."
        />
      ) : (
        <Card style={{ padding: 0 }}>
          <div style={{ overflowX: 'auto' }}>
            <table
              style={{
                width: '100%',
                borderCollapse: 'collapse',
                textAlign: 'left',
                fontSize: '0.875rem',
              }}
            >
              <thead>
                <tr
                  style={{
                    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
                    backgroundColor: 'rgba(255, 255, 255, 0.02)',
                    color: '#94A3B8',
                    textTransform: 'uppercase',
                    fontSize: '0.75rem',
                    letterSpacing: '0.05em',
                  }}
                >
                  <th style={{ padding: '14px 18px' }}>Reference</th>
                  <th style={{ padding: '14px 18px' }}>Facility / Slot</th>
                  <th style={{ padding: '14px 18px' }}>Vehicle / User</th>
                  <th style={{ padding: '14px 18px' }}>Time Schedule</th>
                  <th style={{ padding: '14px 18px' }}>Status</th>
                  <th style={{ padding: '14px 18px' }}>Total</th>
                  <th style={{ padding: '14px 18px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredBookings.map((b) => (
                  <tr
                    key={b.id}
                    style={{
                      borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                    }}
                  >
                    <td style={{ padding: '14px 18px', fontWeight: 600, color: '#F8FAFC' }}>
                      {b.bookingReference}
                    </td>

                    <td style={{ padding: '14px 18px' }}>
                      <div style={{ fontWeight: 600, color: '#F8FAFC' }}>{b.parkingLotName}</div>
                      <div style={{ fontSize: '0.8rem', color: '#F59E0B' }}>
                        Slot <strong>{b.slotNumber}</strong> ({b.floorName})
                      </div>
                    </td>

                    <td style={{ padding: '14px 18px' }}>
                      <div style={{ fontWeight: 700, color: '#F8FAFC', letterSpacing: '0.025em' }}>
                        {b.vehicleRegistrationNumber}
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#94A3B8' }}>{b.userEmail}</div>
                    </td>

                    <td style={{ padding: '14px 18px', fontSize: '0.8rem', color: '#CBD5E1' }}>
                      <div>{new Date(b.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
                      <div style={{ color: '#64748B' }}>
                        → {new Date(b.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </td>

                    <td style={{ padding: '14px 18px' }}>
                      <Badge
                        variant={
                          b.status === 'CONFIRMED'
                            ? 'success'
                            : b.status === 'PENDING'
                            ? 'warning'
                            : b.status === 'COMPLETED'
                            ? 'info'
                            : b.status === 'CANCELLED'
                            ? 'danger'
                            : 'neutral'
                        }
                      >
                        {b.status}
                      </Badge>
                    </td>

                    <td style={{ padding: '14px 18px', fontWeight: 700, color: '#10B981' }}>
                      ₹{b.totalAmount}
                    </td>

                    <td style={{ padding: '14px 18px', textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
                        {b.status === 'PENDING' && (
                          <Button
                            variant="primary"
                            size="sm"
                            isLoading={actionLoadingId === b.id}
                            onClick={() => handleAction(b.id, 'confirm')}
                          >
                            Confirm Check-in
                          </Button>
                        )}

                        {b.status === 'CONFIRMED' && (
                          <Button
                            variant="primary"
                            size="sm"
                            isLoading={actionLoadingId === b.id}
                            onClick={() => handleAction(b.id, 'complete')}
                          >
                            Complete Checkout
                          </Button>
                        )}

                        {(b.status === 'PENDING' || b.status === 'CONFIRMED') && (
                          <>
                            <Button
                              variant="danger"
                              size="sm"
                              isLoading={actionLoadingId === b.id}
                              onClick={() => handleAction(b.id, 'cancel')}
                            >
                              Cancel
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              isLoading={actionLoadingId === b.id}
                              onClick={() => handleAction(b.id, 'expire')}
                            >
                              Expire
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
};
