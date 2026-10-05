import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Button } from '../../components/common/Button';
import { Card } from '../../components/common/Card';
import { Lock, Mail, User, Phone } from 'lucide-react';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { register } = useAuth();
  const toast = useToast();

  const [firstName, setFirstName] = useState<string>('');
  const [lastName, setLastName] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [phoneNumber, setPhoneNumber] = useState<string>('');
  const [role, setRole] = useState<string>('DRIVER');
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firstName || !lastName || !email || !password) {
      setErrorMsg('Please complete all mandatory fields');
      return;
    }

    try {
      setLoading(true);
      setErrorMsg(null);
      await register({
        firstName,
        lastName,
        email,
        password,
        phoneNumber: phoneNumber || undefined,
        role,
      });

      toast.success('Registration successful! Please sign in.');
      navigate('/login');
    } catch (err: any) {
      setErrorMsg(err.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
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
      }}
    >
      <div style={{ width: '100%', maxWidth: '480px' }}>
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
            Create SmartPark Account
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Join India's next-generation smart parking network
          </p>
        </div>

        <Card style={{ padding: '2rem' }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <Input
                label="First Name"
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                required
              />
              <Input
                label="Last Name"
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                required
              />
            </div>

            <Input
              label="Email Address"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              leftIcon={<Mail size={18} />}
              required
            />

            <Input
              label="Password"
              type="password"
              placeholder="Minimum 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              leftIcon={<Lock size={18} />}
              required
            />

            <Input
              label="Phone Number (Optional)"
              placeholder="+91 9876543210"
              value={phoneNumber}
              onChange={(e) => setPhoneNumber(e.target.value)}
              leftIcon={<Phone size={18} />}
            />

            <Select
              label="Account Role"
              value={role}
              onChange={(e) => setRole(e.target.value)}
              options={[
                { value: 'DRIVER', label: 'Driver / Customer (Book & Reserve Slots)' },
                { value: 'OPERATOR', label: 'Facility Operator (Manage Floor & Check-ins)' },
                { value: 'ADMIN', label: 'Platform Administrator (Full Oversight)' },
              ]}
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
              Register Account
            </Button>
          </form>
        </Card>

        <div style={{ textAlign: 'center', marginTop: '1.5rem', fontSize: '0.875rem', color: '#94A3B8' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: '#F59E0B', fontWeight: 600 }}>
            Sign In
          </Link>
        </div>
      </div>
    </div>
  );
};
