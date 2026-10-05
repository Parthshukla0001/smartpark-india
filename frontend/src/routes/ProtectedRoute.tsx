import React from 'react';
import { Navigate, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Role } from '../types';
import { Navbar } from '../components/common/Navbar';
import { LoadingSpinner } from '../components/common/LoadingSpinner';
import { Button } from '../components/common/Button';
import { Card } from '../components/common/Card';
import { ShieldAlert, ArrowLeft } from 'lucide-react';

interface ProtectedRouteProps {
  allowedRoles?: Role[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ allowedRoles }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <LoadingSpinner message="Authenticating session..." />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    const defaultDashboard =
      user.role === 'ADMIN'
        ? '/admin/dashboard'
        : user.role === 'OPERATOR'
        ? '/operator/dashboard'
        : '/driver/dashboard';

    return (
      <div className="app-container">
        <Navbar />
        <main className="main-content" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 'calc(100vh - 120px)' }}>
          <Card style={{ maxWidth: '520px', width: '100%', padding: '2.5rem', textAlign: 'center' }}>
            <div
              style={{
                width: '64px',
                height: '64px',
                borderRadius: '16px',
                backgroundColor: 'rgba(239, 68, 68, 0.12)',
                border: '1px solid rgba(239, 68, 68, 0.25)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: '1.25rem',
              }}
            >
              <ShieldAlert size={32} color="#EF4444" />
            </div>
            <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#F8FAFC', marginBottom: '0.5rem' }}>
              Access Denied (403)
            </h2>
            <p style={{ color: '#94A3B8', fontSize: '0.9rem', lineHeight: '1.6', marginBottom: '1.5rem' }}>
              Your account has the <strong style={{ color: '#FCD34D' }}>{user.role}</strong> role, which does not have authorization to access this administrative or operational area.
            </p>
            <Button
              variant="primary"
              size="md"
              icon={<ArrowLeft size={16} />}
              onClick={() => navigate(defaultDashboard)}
              style={{ margin: '0 auto' }}
            >
              Return to My Dashboard
            </Button>
          </Card>
        </main>
      </div>
    );
  }

  return (
    <div className="app-container">
      <Navbar />
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
};
