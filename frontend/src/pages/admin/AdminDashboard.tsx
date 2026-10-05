import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { parkingApi, bookingApi, actuatorApi } from '../../api';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import {
  Building2,
  Layers,
  CalendarCheck,
  Activity,
  BarChart3,
  Server,
  Database,
  Radio,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';

export const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();

  const [lotsCount, setLotsCount] = useState<number>(0);
  const [bookingsCount, setBookingsCount] = useState<number>(0);
  const [healthStatus, setHealthStatus] = useState<any>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const loadAdminData = async () => {
      try {
        setLoading(true);
        const [lotsRes, bookingsRes, healthRes] = await Promise.all([
          parkingApi.getParkingLots({ page: 0, size: 1 }),
          bookingApi.getAllBookings({ page: 0, size: 1 }),
          actuatorApi.getHealth().catch(() => null),
        ]);

        setLotsCount(lotsRes.totalElements);
        setBookingsCount(bookingsRes.totalElements);
        setHealthStatus(healthRes);
      } catch (err) {
        console.error('Failed to load admin stats:', err);
      } finally {
        setLoading(false);
      }
    };

    loadAdminData();
  }, []);

  if (loading) {
    return <LoadingSpinner message="Loading platform control telemetry..." />;
  }

  const isDbUp = healthStatus?.components?.db?.status === 'UP';
  const isRedisUp = healthStatus?.components?.redis?.status === 'UP';
  const isKafkaUp = healthStatus?.components?.kafka?.status === 'UP';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 700, color: '#F8FAFC' }}>
          SmartPark Platform Control Center
        </h1>
        <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
          National infrastructure oversight, configuration management, and cluster observability.
        </p>
      </div>

      {/* Top Stat Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: '1.25rem',
        }}
      >
        <StatCard
          icon={<Building2 size={24} color="#F59E0B" />}
          label="Active Facilities"
          value={lotsCount}
          subtext="Certified parking stations"
        />
        <StatCard
          icon={<CalendarCheck size={24} color="#10B981" />}
          label="Total Bookings"
          value={bookingsCount}
          subtext="All-time reservations processed"
        />
        <StatCard
          icon={<Activity size={24} color="#38BDF8" />}
          label="Platform Status"
          value={healthStatus?.status || 'UP'}
          subtext="Spring Boot Actuator Health"
        />
      </div>

      {/* Infrastructure Telemetry Snapshot */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Server size={18} color="#A78BFA" />
            <span>Infrastructure Health Cluster</span>
          </div>
        }
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/admin/health')}
            icon={<ArrowRight size={14} />}
          >
            Detailed Health
          </Button>
        }
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: '1.25rem',
          }}
        >
          <HealthPill
            name="PostgreSQL Database"
            status={isDbUp ? 'UP' : 'UNKNOWN'}
            details="Primary Source of Truth (Port 5433)"
          />
          <HealthPill
            name="Redis Cluster"
            status={isRedisUp ? 'UP' : 'UNKNOWN'}
            details="Cache-aside & Sliding Rate Limiter"
          />
          <HealthPill
            name="Apache Kafka Broker"
            status={isKafkaUp ? 'UP' : 'UNKNOWN'}
            details="Transactional Outbox & Event Stream"
          />
        </div>
      </Card>

      {/* Fast Administrative Actions */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: '1.25rem',
        }}
      >
        <Card
          title="Facility & Slot Configuration"
          subtitle="Add new parking complexes, floors, and telemetry bays"
          action={
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate('/admin/facilities')}
            >
              Manage
            </Button>
          }
        >
          <p style={{ fontSize: '0.875rem', color: '#94A3B8', lineHeight: '1.5' }}>
            Configure multi-level parking lots, designate EV charging bays, define hourly rates, and toggle floor availability.
          </p>
        </Card>

        <Card
          title="Live Micrometer Telemetry"
          subtitle="Real-time latency timers, booking counters, and conflict metrics"
          action={
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/admin/metrics')}
            >
              View Metrics
            </Button>
          }
        >
          <p style={{ fontSize: '0.875rem', color: '#94A3B8', lineHeight: '1.5' }}>
            Inspect real-time Micrometer meters for double-booking concurrency conflicts, rate-limiting rejections, and p95 latency.
          </p>
        </Card>
      </div>
    </div>
  );
};

const StatCard: React.FC<{ icon: React.ReactNode; label: string; value: any; subtext: string }> = ({
  icon,
  label,
  value,
  subtext,
}) => {
  return (
    <div
      style={{
        backgroundColor: '#131B2E',
        borderRadius: '12px',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        padding: '1.5rem',
        display: 'flex',
        alignItems: 'center',
        gap: '1.25rem',
      }}
    >
      <div
        style={{
          width: '52px',
          height: '52px',
          borderRadius: '12px',
          backgroundColor: 'rgba(255, 255, 255, 0.04)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {icon}
      </div>
      <div>
        <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>
          {label}
        </div>
        <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#F8FAFC', marginTop: '2px' }}>
          {value}
        </div>
        <div style={{ fontSize: '0.8rem', color: '#64748B' }}>{subtext}</div>
      </div>
    </div>
  );
};

const HealthPill: React.FC<{ name: string; status: string; details: string }> = ({
  name,
  status,
  details,
}) => {
  const isUp = status === 'UP';
  return (
    <div
      style={{
        backgroundColor: '#0B0F19',
        borderRadius: '8px',
        border: '1px solid rgba(255, 255, 255, 0.06)',
        padding: '1rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontWeight: 600, color: '#F8FAFC', fontSize: '0.95rem' }}>{name}</span>
        <Badge variant={isUp ? 'success' : 'danger'}>{status}</Badge>
      </div>
      <div style={{ fontSize: '0.8rem', color: '#94A3B8' }}>{details}</div>
    </div>
  );
};
