import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Input } from '../../components/common/Input';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { Lock, Mail, ShieldCheck, Sparkles, UserCheck } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const toast = useToast();

  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg('Please enter both email and password');
      return;
    }

    try {
      setLoading(true);
      setErrorMsg(null);
      const res = await login(email, password);
      toast.success(`Welcome back, ${res.firstName}!`);

      if (res.role === 'ADMIN') {
        navigate('/admin/dashboard');
      } else if (res.role === 'OPERATOR') {
        navigate('/operator/dashboard');
      } else {
        navigate('/driver/dashboard');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Invalid credentials');
    } finally {
      setLoading(false);
    }
  };

  const setDemoCredentials = (role: 'driver' | 'operator' | 'admin') => {
    if (role === 'driver') {
      setEmail('driver@test.com');
      setPassword('password123');
    } else if (role === 'operator') {
      setEmail('operator@test.com');
      setPassword('password123');
    } else {
      setEmail('admin@test.com');
      setPassword('password123');
    }
    setErrorMsg(null);
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1.5rem',
        backgroundColor: '#0B0F19',
        position: 'relative',
      }}
    >
      <div style={{ width: '100%', maxWidth: '440px' }}>
        {/* Brand header */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div
            style={{
              width: '48px',
              height: '48px',
              borderRadius: '12px',
              background: 'linear-gradient(135deg, #F59E0B 0%, #D97706 100%)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: '1.75rem',
              color: '#0B0F19',
              boxShadow: '0 4px 14px rgba(245, 158, 11, 0.4)',
              marginBottom: '1rem',
            }}
          >
            P
          </div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#F8FAFC', letterSpacing: '-0.02em' }}>
            SmartPark India
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Smart Parking Management & Telemetry Platform
          </p>
        </div>

        {/* Login Card */}
        <Card style={{ padding: '2rem' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <Input
              label="Email Address"
              type="email"
              placeholder="e.g. driver@test.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail size={18} />}
              required
            />

            <Input
              label="Password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              leftIcon={<Lock size={18} />}
              required
            />

            {errorMsg && (
              <div
                style={{
                  padding: '0.75rem',
                  backgroundColor: 'rgba(239, 68, 68, 0.15)',
                  border: '1px solid rgba(239, 68, 68, 0.3)',
                  borderRadius: '8px',
                  color: '#F87171',
                  fontSize: '0.85rem',
                  textAlign: 'center',
                }}
              >
                {errorMsg}
              </div>
            )}

            <Button
              type="submit"
              variant="primary"
              size="lg"
              isLoading={loading}
              style={{ width: '100%', marginTop: '0.5rem' }}
            >
              Sign In to SmartPark
            </Button>
          </form>

          {/* Demo User Fast Fill */}
          <div
            style={{
              marginTop: '1.75rem',
              paddingTop: '1.25rem',
              borderTop: '1px solid rgba(255, 255, 255, 0.08)',
            }}
          >
            <div style={{ fontSize: '0.775rem', color: '#94A3B8', textAlign: 'center', marginBottom: '8px' }}>
              ⚡ Quick Demo Login
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '6px' }}>
              <button
                type="button"
                onClick={() => setDemoCredentials('driver')}
                style={{
                  padding: '6px 10px',
                  borderRadius: '6px',
                  backgroundColor: '#1E293B',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  color: '#34D399',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                🚗 Driver
              </button>
              <button
                type="button"
                onClick={() => setDemoCredentials('operator')}
                style={{
                  padding: '6px 10px',
                  borderRadius: '6px',
                  backgroundColor: '#1E293B',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  color: '#38BDF8',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                🛠️ Operator
              </button>
              <button
                type="button"
                onClick={() => setDemoCredentials('admin')}
                style={{
                  padding: '6px 10px',
                  borderRadius: '6px',
                  backgroundColor: '#1E293B',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  color: '#A78BFA',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                👑 Admin
              </button>
            </div>
          </div>
        </Card>

        {/* Footer link to register */}
        <div style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.875rem', color: '#94A3B8' }}>
          Don't have an account?{' '}
          <Link to="/register" style={{ color: '#F59E0B', fontWeight: 600 }}>
            Register Now
          </Link>
        </div>
      </div>
    </div>
  );
};
