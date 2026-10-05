import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { ProtectedRoute } from './routes/ProtectedRoute';

// Auth Pages
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';

// Driver Pages
import { DriverDashboard } from './pages/driver/DriverDashboard';
import { ParkingSearchPage } from './pages/driver/ParkingSearchPage';
import { ParkingLotDetailPage } from './pages/driver/ParkingLotDetailPage';
import { MyBookingsPage } from './pages/driver/MyBookingsPage';
import { VehiclesPage } from './pages/driver/VehiclesPage';

// Operator Pages
import { OperatorDashboard } from './pages/operator/OperatorDashboard';
import { OperatorFloorsPage } from './pages/operator/OperatorFloorsPage';
import { OperatorBookingsPage } from './pages/operator/OperatorBookingsPage';

// Admin Pages
import { AdminDashboard } from './pages/admin/AdminDashboard';
import { AdminFacilitiesPage } from './pages/admin/AdminFacilitiesPage';
import { AdminHealthPage } from './pages/admin/AdminHealthPage';
import { AdminMetricsPage } from './pages/admin/AdminMetricsPage';
import { AdminStaffPage } from './pages/admin/AdminStaffPage';

const RootRedirect: React.FC = () => {
  const { user, isAuthenticated, isLoading } = useAuth();
  if (isLoading) return null;
  if (!isAuthenticated || !user) return <Navigate to="/login" replace />;
  if (user.role === 'ADMIN') return <Navigate to="/admin/dashboard" replace />;
  if (user.role === 'OPERATOR') return <Navigate to="/operator/dashboard" replace />;
  return <Navigate to="/driver/dashboard" replace />;
};

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            {/* Public Auth Routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/" element={<RootRedirect />} />

            {/* Driver Routes */}
            <Route element={<ProtectedRoute allowedRoles={['DRIVER', 'ADMIN']} />}>
              <Route path="/driver/dashboard" element={<DriverDashboard />} />
              <Route path="/driver/search" element={<ParkingSearchPage />} />
              <Route path="/driver/lots/:id" element={<ParkingLotDetailPage />} />
              <Route path="/driver/bookings" element={<MyBookingsPage />} />
              <Route path="/driver/vehicles" element={<VehiclesPage />} />
            </Route>

            {/* Operator Routes */}
            <Route element={<ProtectedRoute allowedRoles={['OPERATOR', 'ADMIN']} />}>
              <Route path="/operator/dashboard" element={<OperatorDashboard />} />
              <Route path="/operator/floors" element={<OperatorFloorsPage />} />
              <Route path="/operator/bookings" element={<OperatorBookingsPage />} />
            </Route>

            {/* Admin Routes */}
            <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
              <Route path="/admin/dashboard" element={<AdminDashboard />} />
              <Route path="/admin/facilities" element={<AdminFacilitiesPage />} />
              <Route path="/admin/staff" element={<AdminStaffPage />} />
              <Route path="/admin/health" element={<AdminHealthPage />} />
              <Route path="/admin/metrics" element={<AdminMetricsPage />} />
            </Route>

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
