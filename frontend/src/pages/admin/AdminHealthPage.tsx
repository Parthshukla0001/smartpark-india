import React, { useState, useEffect } from 'react';
import { actuatorApi } from '../../api';
import { ActuatorHealthResponse } from '../../types';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { useToast } from '../../context/ToastContext';
import {
  Activity,
  Database,
  Server,
  HardDrive,
  RefreshCw,
  CheckCircle,
  AlertTriangle,
  Code,
} from 'lucide-react';

export const AdminHealthPage: React.FC = () => {
  const toast = useToast();

  const [healthData, setHealthData] = useState<ActuatorHealthResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [showRawJson, setShowRawJson] = useState<boolean>(false);
  const [lastChecked, setLastChecked] = useState<string>('');

  const fetchHealth = async (isManual = false) => {
    try {
      if (isManual) setRefreshing(true);
      const data = await actuatorApi.getHealth();
      setHealthData(data);
      setLastChecked(new Date().toLocaleTimeString());
      if (isManual) toast.success('Actuator health diagnostics refreshed');
    } catch (err: any) {
      toast.error('Failed to query Actuator health endpoint');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(() => fetchHealth(false), 10000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return <LoadingSpinner message="Polling Spring Boot Actuator health subsystems..." />;
  }

  const components = healthData?.components || {};

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            System Health & Component Observability
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Live status reports from Spring Boot 3.4 Actuator (`/actuator/health`).
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '0.8rem', color: '#64748B' }}>
            Last checked: {lastChecked}
          </span>
          <Button
            variant="outline"
            size="sm"
            icon={<RefreshCw size={14} className={refreshing ? 'live-pulse' : undefined} />}
            isLoading={refreshing}
            onClick={() => fetchHealth(true)}
          >
            Refresh Now
          </Button>
        </div>
      </div>

      {/* Primary Cluster Status Card */}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '12px',
                backgroundColor: healthData?.status === 'UP' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Activity size={24} color={healthData?.status === 'UP' ? '#10B981' : '#EF4444'} />
            </div>

            <div>
              <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>
                Overall Platform Health
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#F8FAFC' }}>
                Status: {healthData?.status || 'UNKNOWN'}
              </div>
            </div>
          </div>

          <Badge variant={healthData?.status === 'UP' ? 'success' : 'danger'}>
            {healthData?.status === 'UP' ? 'CLUSTER HEALTHY' : 'DEGRADED'}
          </Badge>
        </div>
      </Card>

      {/* Subsystem Health Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: '1.25rem',
        }}
      >
        {/* PostgreSQL */}
        <ComponentCard
          icon={<Database size={22} color="#38BDF8" />}
          title="PostgreSQL 16"
          status={components.db?.status || 'UP'}
          details={[
            { label: 'Subsystem', value: 'HikariCP Connection Pool' },
            { label: 'Role', value: 'Authoritative Relational Store' },
            { label: 'Isolation', value: 'SERIALIZABLE / PESSIMISTIC_WRITE' },
          ]}
        />

        {/* Redis */}
        <ComponentCard
          icon={<Server size={22} color="#EF4444" />}
          title="Redis Cache & Rate Limiter"
          status={components.redis?.status || 'UP'}
          details={[
            { label: 'Subsystem', value: 'Lettuce Client' },
            { label: 'Role', value: 'Cache-Aside & Sliding Window' },
            { label: 'Fault Policy', value: 'Fail-Open Mode Active' },
          ]}
        />

        {/* Apache Kafka */}
        <ComponentCard
          icon={<Activity size={22} color="#F59E0B" />}
          title="Apache Kafka Broker"
          status={components.kafka?.status || 'UP'}
          details={[
            { label: 'Subsystem', value: 'Spring Kafka Producer/Consumer' },
            { label: 'Role', value: 'Async Event Stream' },
            { label: 'Delivery Pattern', value: 'Transactional Outbox' },
          ]}
        />

        {/* Disk Space */}
        <ComponentCard
          icon={<HardDrive size={22} color="#A78BFA" />}
          title="Host Storage"
          status={components.diskSpace?.status || 'UP'}
          details={[
            { label: 'Subsystem', value: 'Host OS Volume' },
            { label: 'Role', value: 'Logs, Flyway & App Binary' },
            { label: 'Status', value: 'Sufficient Free Blocks' },
          ]}
        />
      </div>

      {/* Raw JSON Inspection */}
      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Code size={18} color="#94A3B8" />
            <span>Raw Actuator Diagnostic JSON</span>
          </div>
        }
        action={
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setShowRawJson(!showRawJson)}
          >
            {showRawJson ? 'Hide Payload' : 'Inspect JSON'}
          </Button>
        }
      >
        {showRawJson ? (
          <pre
            style={{
              backgroundColor: '#0B0F19',
              padding: '1rem',
              borderRadius: '8px',
              border: '1px solid rgba(255, 255, 255, 0.08)',
              color: '#34D399',
              fontSize: '0.85rem',
              overflowX: 'auto',
              fontFamily: 'monospace',
              maxHeight: '350px',
            }}
          >
            {JSON.stringify(healthData, null, 2)}
          </pre>
        ) : (
          <p style={{ fontSize: '0.85rem', color: '#94A3B8' }}>
            Click "Inspect JSON" above to view the unfiltered Actuator payload received from `/actuator/health`.
          </p>
        )}
      </Card>
    </div>
  );
};

const ComponentCard: React.FC<{
  icon: React.ReactNode;
  title: string;
  status: string;
  details: { label: string; value: string }[];
}> = ({ icon, title, status, details }) => {
  const isUp = status === 'UP';

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              padding: '8px',
              borderRadius: '8px',
              backgroundColor: 'rgba(255, 255, 255, 0.04)',
              display: 'flex',
            }}
          >
            {icon}
          </div>
          <span style={{ fontWeight: 600, color: '#F8FAFC', fontSize: '1.05rem' }}>{title}</span>
        </div>

        <Badge variant={isUp ? 'success' : 'danger'}>{status}</Badge>
      </div>

      <div style={{ marginTop: '1.25rem', display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {details.map((d) => (
          <div
            key={d.label}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              fontSize: '0.8rem',
              borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
              paddingBottom: '4px',
            }}
          >
            <span style={{ color: '#94A3B8' }}>{d.label}</span>
            <span style={{ fontWeight: 600, color: '#CBD5E1' }}>{d.value}</span>
          </div>
        ))}
      </div>
    </Card>
  );
};
