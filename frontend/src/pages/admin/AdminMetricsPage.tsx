import React, { useState, useEffect } from 'react';
import { actuatorApi } from '../../api';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Badge } from '../../components/common/Badge';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { useToast } from '../../context/ToastContext';
import {
  BarChart3,
  RefreshCw,
  Zap,
  ShieldAlert,
  Clock,
  Cpu,
  Database,
  CalendarCheck,
} from 'lucide-react';

interface MetricDisplayItem {
  metricName: string;
  label: string;
  category: string;
  value: string | number;
  unit?: string;
  description: string;
}

export const AdminMetricsPage: React.FC = () => {
  const toast = useToast();

  const [metrics, setMetrics] = useState<MetricDisplayItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [lastUpdated, setLastUpdated] = useState<string>('');

  const metricDefinitions = [
    {
      name: 'smartpark.bookings.created',
      label: 'Bookings Created',
      category: 'Business Counters',
      description: 'Total successful booking creations',
    },
    {
      name: 'smartpark.bookings.completed',
      label: 'Bookings Completed',
      category: 'Business Counters',
      description: 'Successful check-out completions',
    },
    {
      name: 'smartpark.bookings.cancelled',
      label: 'Bookings Cancelled',
      category: 'Business Counters',
      description: 'Reservations cancelled before start time',
    },
    {
      name: 'smartpark.bookings.expired',
      label: 'Bookings Expired',
      category: 'Business Counters',
      description: 'Automated scheduler & manual expiries',
    },
    {
      name: 'smartpark.bookings.conflicts',
      label: 'Double-Booking Conflicts',
      category: 'Concurrency Protection',
      description: 'Race conditions prevented via locks & exclusion',
    },
    {
      name: 'smartpark.rate_limit.rejected',
      label: 'Rate-Limit Rejections',
      category: 'DDoS & Traffic Guard',
      description: 'Requests throttled via Redis sliding rate limiter',
    },
    {
      name: 'smartpark.bookings.create_time',
      label: 'Booking Creation Latency',
      category: 'Performance Timers',
      description: 'Total duration spent inside transactional booking boundary',
    },
    {
      name: 'jvm.memory.used',
      label: 'JVM Memory Used',
      category: 'Runtime Telemetry',
      description: 'Current memory allocated to Java 21 process',
    },
    {
      name: 'system.cpu.usage',
      label: 'System CPU Usage',
      category: 'Runtime Telemetry',
      description: 'System CPU utilization load factor',
    },
  ];

  const fetchMetrics = async (isManual = false) => {
    try {
      if (isManual) setRefreshing(true);

      const metricPromises = metricDefinitions.map(async (def) => {
        try {
          const res = await actuatorApi.getMetric(def.name);
          const firstMeasurement = res.measurements?.[0];
          let formattedVal: string | number = firstMeasurement ? firstMeasurement.value : 0;

          if (def.name.includes('memory')) {
            const mb = Math.round(Number(formattedVal) / (1024 * 1024));
            formattedVal = `${mb} MB`;
          } else if (def.name.includes('cpu')) {
            formattedVal = `${Math.round(Number(formattedVal) * 100)}%`;
          } else if (def.name.includes('time')) {
            const count = res.measurements?.find((m) => m.statistic === 'COUNT')?.value || 0;
            const max = res.measurements?.find((m) => m.statistic === 'MAX')?.value || 0;
            formattedVal = `${count} calls (max ${(max * 1000).toFixed(1)}ms)`;
          }

          return {
            metricName: def.name,
            label: def.label,
            category: def.category,
            value: formattedVal,
            unit: res.baseUnit,
            description: def.description,
          };
        } catch {
          return {
            metricName: def.name,
            label: def.label,
            category: def.category,
            value: 0,
            description: def.description,
          };
        }
      });

      const results = await Promise.all(metricPromises);
      setMetrics(results);
      setLastUpdated(new Date().toLocaleTimeString());
      if (isManual) toast.success('Micrometer metrics refreshed');
    } catch (err: any) {
      toast.error('Failed to load Actuator metrics');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(() => fetchMetrics(false), 12000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return <LoadingSpinner message="Polling Micrometer Prometheus metric registry..." />;
  }

  const categories = Array.from(new Set(metrics.map((m) => m.category)));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 700, color: '#F8FAFC' }}>
            Live Micrometer Telemetry & Metrics
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Real-time measurements from `/actuator/metrics` for booking throughput, concurrency conflicts, and rate limiting.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ fontSize: '0.8rem', color: '#64748B' }}>
            Last updated: {lastUpdated}
          </span>
          <Button
            variant="outline"
            size="sm"
            icon={<RefreshCw size={14} className={refreshing ? 'live-pulse' : undefined} />}
            isLoading={refreshing}
            onClick={() => fetchMetrics(true)}
          >
            Refresh Metrics
          </Button>
        </div>
      </div>

      {/* Metrics by Category */}
      {categories.map((cat) => {
        const catMetrics = metrics.filter((m) => m.category === cat);
        return (
          <div key={cat} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <h2 style={{ fontSize: '1.15rem', fontWeight: 600, color: '#F8FAFC' }}>
              {cat}
            </h2>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                gap: '1.25rem',
              }}
            >
              {catMetrics.map((m) => (
                <Card key={m.metricName}>
                  <div style={{ fontSize: '0.75rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    {m.label}
                  </div>
                  <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#F8FAFC', marginTop: '6px' }}>
                    {m.value}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#64748B', marginTop: '4px' }}>
                    {m.description}
                  </div>
                  <div
                    style={{
                      marginTop: '10px',
                      paddingTop: '8px',
                      borderTop: '1px solid rgba(255, 255, 255, 0.04)',
                      fontSize: '0.7rem',
                      color: '#475569',
                      fontFamily: 'monospace',
                    }}
                  >
                    {m.metricName}
                  </div>
                </Card>
              ))}
            </div>
          </div>
        );
      })}
    </div>
  );
};
