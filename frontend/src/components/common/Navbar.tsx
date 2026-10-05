import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { notificationApi } from '../../api';
import { Notification } from '../../types';
import {
  Bell,
  LogOut,
  Car,
  ShieldAlert,
  Radio,
  Check,
  Building2,
  Activity,
  Layers,
  Search,
  CalendarCheck,
  BarChart3,
  Sliders,
} from 'lucide-react';
import { wsManager } from '../../services/websocket';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [showNotifications, setShowNotifications] = useState<boolean>(false);
  const [isWsConnected, setIsWsConnected] = useState<boolean>(wsManager.isConnected());
  const notifRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const unsub = wsManager.onConnectionChange(setIsWsConnected);
    return unsub;
  }, []);

  const fetchNotifications = async () => {
    if (!user) return;
    try {
      const data = await notificationApi.getMyNotifications();
      setNotifications(data);
      setUnreadCount(data.filter((n) => !n.readAt).length);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 15000);
    return () => clearInterval(interval);
  }, [user]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(event.target as Node)) {
        setShowNotifications(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleMarkAsRead = async (id: number) => {
    try {
      await notificationApi.markAsRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {
      // ignore
    }
  };

  if (!user) return null;

  const role = user.role;

  return (
    <header
      style={{
        backgroundColor: '#0F172A',
        borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
        position: 'sticky',
        top: 0,
        zIndex: 40,
      }}
    >
      <div
        style={{
          maxWidth: '1400px',
          margin: '0 auto',
          padding: '0 1.5rem',
          height: '68px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        {/* Brand & Role */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
          <Link
            to={
              role === 'DRIVER'
                ? '/driver/dashboard'
                : role === 'OPERATOR'
                ? '/operator/dashboard'
                : '/admin/dashboard'
            }
            style={{ display: 'flex', alignItems: 'center', gap: '10px' }}
          >
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '8px',
                background: 'linear-gradient(135deg, #F59E0B 0%, #D97706 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 800,
                fontSize: '1.25rem',
                color: '#0B0F19',
                boxShadow: '0 2px 8px rgba(245, 158, 11, 0.3)',
              }}
            >
              P
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontWeight: 700, fontSize: '1.15rem', color: '#F8FAFC', letterSpacing: '-0.02em' }}>
                  SmartPark
                </span>
                <span
                  style={{
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    padding: '1px 6px',
                    borderRadius: '4px',
                    backgroundColor: '#1E293B',
                    color: '#F59E0B',
                    border: '1px solid rgba(245, 158, 11, 0.3)',
                  }}
                >
                  INDIA
                </span>
              </div>
            </div>
          </Link>

          {/* Navigation Links */}
          <nav style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            {role === 'DRIVER' && (
              <>
                <NavLink to="/driver/dashboard" active={location.pathname === '/driver/dashboard'}>
                  Dashboard
                </NavLink>
                <NavLink
                  to="/driver/search"
                  active={location.pathname.startsWith('/driver/search') || location.pathname.startsWith('/driver/lots')}
                  icon={<Search size={16} />}
                >
                  Find Parking
                </NavLink>
                <NavLink
                  to="/driver/bookings"
                  active={location.pathname === '/driver/bookings'}
                  icon={<CalendarCheck size={16} />}
                >
                  My Bookings
                </NavLink>
                <NavLink
                  to="/driver/vehicles"
                  active={location.pathname === '/driver/vehicles'}
                  icon={<Car size={16} />}
                >
                  My Vehicles
                </NavLink>
              </>
            )}

            {role === 'OPERATOR' && (
              <>
                <NavLink to="/operator/dashboard" active={location.pathname === '/operator/dashboard'}>
                  Facility Overview
                </NavLink>
                <NavLink
                  to="/operator/floors"
                  active={location.pathname === '/operator/floors'}
                  icon={<Layers size={16} />}
                >
                  Live Floor Map
                </NavLink>
                <NavLink
                  to="/operator/bookings"
                  active={location.pathname === '/operator/bookings'}
                  icon={<CalendarCheck size={16} />}
                >
                  Manage Bookings
                </NavLink>
              </>
            )}

            {role === 'ADMIN' && (
              <>
                <NavLink to="/admin/dashboard" active={location.pathname === '/admin/dashboard'}>
                  Control Center
                </NavLink>
                <NavLink
                  to="/admin/facilities"
                  active={location.pathname.startsWith('/admin/facilities')}
                  icon={<Building2 size={16} />}
                >
                  Facilities & Slots
                </NavLink>
                <NavLink
                  to="/admin/health"
                  active={location.pathname === '/admin/health'}
                  icon={<Activity size={16} />}
                >
                  System Health
                </NavLink>
                <NavLink
                  to="/admin/metrics"
                  active={location.pathname === '/admin/metrics'}
                  icon={<BarChart3 size={16} />}
                >
                  Live Metrics
                </NavLink>
              </>
            )}
          </nav>
        </div>

        {/* Right side controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          {/* WebSocket Status Indicator */}
          <div
            title={isWsConnected ? 'STOMP WebSocket Connected' : 'WebSocket Reconnecting...'}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              borderRadius: '9999px',
              backgroundColor: isWsConnected ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
              border: `1px solid ${isWsConnected ? 'rgba(16, 185, 129, 0.25)' : 'rgba(239, 68, 68, 0.25)'}`,
              fontSize: '0.75rem',
              fontWeight: 500,
              color: isWsConnected ? '#34D399' : '#F87171',
            }}
          >
            <div
              style={{
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                backgroundColor: isWsConnected ? '#10B981' : '#EF4444',
              }}
              className={isWsConnected ? 'live-pulse' : undefined}
            />
            <span>{isWsConnected ? 'LIVE WS' : 'DISCONNECTED'}</span>
          </div>

          {/* Notifications Popover */}
          <div style={{ position: 'relative' }} ref={notifRef}>
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              style={{
                position: 'relative',
                background: '#1E293B',
                border: '1px solid rgba(255, 255, 255, 0.1)',
                borderRadius: '8px',
                padding: '8px',
                color: '#CBD5E1',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Bell size={18} />
              {unreadCount > 0 && (
                <span
                  style={{
                    position: 'absolute',
                    top: '-4px',
                    right: '-4px',
                    width: '18px',
                    height: '18px',
                    borderRadius: '50%',
                    backgroundColor: '#EF4444',
                    color: '#FFF',
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>

            {showNotifications && (
              <div
                style={{
                  position: 'absolute',
                  right: 0,
                  top: '46px',
                  width: '360px',
                  backgroundColor: '#131B2E',
                  borderRadius: '12px',
                  border: '1px solid rgba(255, 255, 255, 0.12)',
                  boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.6)',
                  zIndex: 60,
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    padding: '12px 16px',
                    borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <span style={{ fontWeight: 600, fontSize: '0.9rem', color: '#F8FAFC' }}>
                    Notifications
                  </span>
                  <span style={{ fontSize: '0.75rem', color: '#94A3B8' }}>
                    {unreadCount} unread
                  </span>
                </div>

                <div style={{ maxHeight: '340px', overflowY: 'auto' }}>
                  {notifications.length === 0 ? (
                    <div style={{ padding: '2rem 1rem', textAlign: 'center', color: '#64748B', fontSize: '0.85rem' }}>
                      No notifications yet
                    </div>
                  ) : (
                    notifications.map((n) => (
                      <div
                        key={n.id}
                        style={{
                          padding: '12px 16px',
                          borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                          backgroundColor: n.readAt ? 'transparent' : 'rgba(245, 158, 11, 0.05)',
                          display: 'flex',
                          alignItems: 'flex-start',
                          justifyContent: 'space-between',
                          gap: '10px',
                        }}
                      >
                        <div style={{ flex: 1 }}>
                          <div style={{ fontSize: '0.75rem', color: '#F59E0B', fontWeight: 600, textTransform: 'uppercase' }}>
                            {n.type}
                          </div>
                          <div style={{ fontSize: '0.825rem', color: '#E2E8F0', marginTop: '2px', lineHeight: '1.4' }}>
                            {n.message}
                          </div>
                          <div style={{ fontSize: '0.7rem', color: '#64748B', marginTop: '4px' }}>
                            {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        </div>
                        {!n.readAt && (
                          <button
                            onClick={() => handleMarkAsRead(n.id)}
                            title="Mark as read"
                            style={{
                              background: 'transparent',
                              border: 'none',
                              color: '#10B981',
                              padding: '4px',
                              cursor: 'pointer',
                            }}
                          >
                            <Check size={16} />
                          </button>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>

          {/* User profile & Logout */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-end',
              }}
            >
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#F8FAFC' }}>
                {user.firstName} {user.lastName}
              </span>
              <span
                style={{
                  fontSize: '0.7rem',
                  fontWeight: 600,
                  color:
                    role === 'ADMIN'
                      ? '#A78BFA'
                      : role === 'OPERATOR'
                      ? '#38BDF8'
                      : '#34D399',
                }}
              >
                {role}
              </span>
            </div>

            <button
              onClick={() => {
                logout();
                navigate('/login');
              }}
              title="Logout"
              style={{
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.25)',
                color: '#EF4444',
                borderRadius: '8px',
                padding: '8px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};

interface NavLinkProps {
  to: string;
  active: boolean;
  children: React.ReactNode;
  icon?: React.ReactNode;
}

const NavLink: React.FC<NavLinkProps> = ({ to, active, children, icon }) => {
  return (
    <Link
      to={to}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '6px 12px',
        borderRadius: '6px',
        fontSize: '0.875rem',
        fontWeight: active ? 600 : 500,
        color: active ? '#F59E0B' : '#94A3B8',
        backgroundColor: active ? 'rgba(245, 158, 11, 0.1)' : 'transparent',
        border: active ? '1px solid rgba(245, 158, 11, 0.25)' : '1px solid transparent',
        transition: 'all 0.15s ease',
      }}
    >
      {icon}
      {children}
    </Link>
  );
};
