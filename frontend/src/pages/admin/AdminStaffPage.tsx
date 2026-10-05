import React, { useState, useEffect } from 'react';
import { staffApi } from '../../api';
import { StaffMember, Role } from '../../types';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Badge } from '../../components/common/Badge';
import { Modal } from '../../components/common/Modal';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import {
  Users,
  UserPlus,
  Shield,
  Search,
  Mail,
  Phone,
  Lock,
  User,
  CheckCircle,
  XCircle,
  Info,
  Calendar,
} from 'lucide-react';

export const AdminStaffPage: React.FC = () => {
  const { user: currentUser } = useAuth();
  const toast = useToast();

  const [staffList, setStaffList] = useState<StaffMember[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<boolean>(false);

  // New staff form state
  const [firstName, setFirstName] = useState<string>('');
  const [lastName, setLastName] = useState<string>('');
  const [email, setEmail] = useState<string>('');
  const [phoneNumber, setPhoneNumber] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [role, setRole] = useState<Role>('OPERATOR');
  const [formError, setFormError] = useState<string | null>(null);

  const fetchStaff = async () => {
    try {
      setLoading(true);
      const data = await staffApi.getAllStaff();
      setStaffList(data);
    } catch (err: any) {
      toast.error(err.message || 'Failed to load staff list');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStaff();
  }, []);

  const handleCreateStaff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!firstName.trim() || !lastName.trim() || !email.trim() || !password) {
      setFormError('Please fill in all mandatory fields');
      return;
    }

    if (password.length < 8) {
      setFormError('Password must be at least 8 characters');
      return;
    }

    try {
      setActionLoading(true);
      setFormError(null);
      await staffApi.createStaff({
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        email: email.trim(),
        phoneNumber: phoneNumber.trim() || undefined,
        password,
        role,
      });

      toast.success(`${role} account created successfully!`);
      setIsModalOpen(false);
      resetForm();
      fetchStaff();
    } catch (err: any) {
      setFormError(err.message || 'Failed to create staff member');
    } finally {
      setActionLoading(false);
    }
  };

  const handleToggleStatus = async (staff: StaffMember) => {
    if (staff.id === currentUser?.id) {
      toast.error('You cannot deactivate your own administrative account');
      return;
    }

    const nextStatus = !staff.active;
    const actionName = nextStatus ? 'activate' : 'deactivate';

    if (!window.confirm(`Are you sure you want to ${actionName} ${staff.firstName} ${staff.lastName}'s account?`)) {
      return;
    }

    try {
      await staffApi.updateStaffStatus(staff.id, nextStatus);
      toast.success(`Account ${nextStatus ? 'activated' : 'deactivated'} successfully`);
      setStaffList((prev) =>
        prev.map((s) => (s.id === staff.id ? { ...s, active: nextStatus } : s))
      );
    } catch (err: any) {
      toast.error(err.message || `Failed to ${actionName} account`);
    }
  };

  const resetForm = () => {
    setFirstName('');
    setLastName('');
    setEmail('');
    setPhoneNumber('');
    setPassword('');
    setRole('OPERATOR');
    setFormError(null);
  };

  const filteredStaff = staffList.filter((s) => {
    const fullName = `${s.firstName} ${s.lastName}`.toLowerCase();
    const query = searchTerm.toLowerCase();
    return fullName.includes(query) || s.email.toLowerCase().includes(query) || s.role.toLowerCase().includes(query);
  });

  const totalStaff = staffList.length;
  const activeOperators = staffList.filter((s) => s.role === 'OPERATOR' && s.active).length;
  const totalAdmins = staffList.filter((s) => s.role === 'ADMIN').length;
  const inactiveStaff = staffList.filter((s) => !s.active).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#F8FAFC', letterSpacing: '-0.02em' }}>
            Staff & Operator Management
          </h1>
          <p style={{ fontSize: '0.9rem', color: '#94A3B8', marginTop: '4px' }}>
            Controlled administrative provisioning for facility operators and system administrators
          </p>
        </div>

        <Button
          variant="primary"
          size="md"
          icon={<UserPlus size={18} />}
          onClick={() => {
            resetForm();
            setIsModalOpen(true);
          }}
        >
          Add Staff Member
        </Button>
      </div>

      {/* Facility Scope Note */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '1rem 1.25rem',
          backgroundColor: 'rgba(56, 189, 248, 0.08)',
          border: '1px solid rgba(56, 189, 248, 0.2)',
          borderRadius: '10px',
          color: '#BAE6FD',
          fontSize: '0.85rem',
          lineHeight: '1.5',
        }}
      >
        <Info size={20} color="#38BDF8" style={{ flexShrink: 0 }} />
        <div>
          <strong style={{ color: '#F0F9FF' }}>Facility Assignment Scope:</strong> Operators operate with platform-wide facility management authority. Specific facility-to-operator binding is not partitioned in the current database schema.
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
        <div style={{ backgroundColor: '#131B2E', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '12px', padding: '1.25rem' }}>
          <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>Total Staff Accounts</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#F8FAFC', marginTop: '4px' }}>{totalStaff}</div>
          <div style={{ fontSize: '0.75rem', color: '#64748B', marginTop: '2px' }}>Operators & Administrators</div>
        </div>

        <div style={{ backgroundColor: '#131B2E', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '12px', padding: '1.25rem' }}>
          <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>Active Operators</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#38BDF8', marginTop: '4px' }}>{activeOperators}</div>
          <div style={{ fontSize: '0.75rem', color: '#64748B', marginTop: '2px' }}>Operational floor controllers</div>
        </div>

        <div style={{ backgroundColor: '#131B2E', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '12px', padding: '1.25rem' }}>
          <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>Platform Admins</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: '#A78BFA', marginTop: '4px' }}>{totalAdmins}</div>
          <div style={{ fontSize: '0.75rem', color: '#64748B', marginTop: '2px' }}>Full root oversight</div>
        </div>

        <div style={{ backgroundColor: '#131B2E', border: '1px solid rgba(255, 255, 255, 0.08)', borderRadius: '12px', padding: '1.25rem' }}>
          <div style={{ fontSize: '0.8rem', color: '#94A3B8', textTransform: 'uppercase' }}>Deactivated Accounts</div>
          <div style={{ fontSize: '1.75rem', fontWeight: 800, color: inactiveStaff > 0 ? '#EF4444' : '#10B981', marginTop: '4px' }}>{inactiveStaff}</div>
          <div style={{ fontSize: '0.75rem', color: '#64748B', marginTop: '2px' }}>Suspended credentials</div>
        </div>
      </div>

      {/* Staff Table Card */}
      <Card
        title="Authorized Staff Directory"
        subtitle="Manage active credentials and administrative role assignments"
        action={
          <div style={{ width: '260px' }}>
            <Input
              placeholder="Search by name, email, or role..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              leftIcon={<Search size={16} />}
            />
          </div>
        }
      >
        {loading ? (
          <LoadingSpinner message="Loading staff directory..." />
        ) : filteredStaff.length === 0 ? (
          <EmptyState
            icon={<Users size={28} />}
            title="No staff members found"
            description={searchTerm ? 'No accounts match your search filter' : 'No staff accounts exist yet. Click Add Staff Member to provision the first operator.'}
          />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.08)', color: '#94A3B8', fontSize: '0.75rem', textTransform: 'uppercase' }}>
                  <th style={{ padding: '12px 16px' }}>Staff Member</th>
                  <th style={{ padding: '12px 16px' }}>Role</th>
                  <th style={{ padding: '12px 16px' }}>Phone Number</th>
                  <th style={{ padding: '12px 16px' }}>Status</th>
                  <th style={{ padding: '12px 16px' }}>Created</th>
                  <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredStaff.map((staff) => {
                  const isSelf = staff.id === currentUser?.id;
                  return (
                    <tr
                      key={staff.id}
                      style={{
                        borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                        backgroundColor: staff.active ? 'transparent' : 'rgba(239, 68, 68, 0.03)',
                      }}
                    >
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontWeight: 600, color: '#F8FAFC' }}>
                          {staff.firstName} {staff.lastName}
                          {isSelf && (
                            <span style={{ marginLeft: '6px', fontSize: '0.7rem', color: '#F59E0B', fontWeight: 700 }}>
                              (You)
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: '0.8rem', color: '#94A3B8', marginTop: '2px' }}>
                          {staff.email}
                        </div>
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <Badge variant={staff.role === 'ADMIN' ? 'warning' : 'info'}>
                          {staff.role}
                        </Badge>
                      </td>

                      <td style={{ padding: '14px 16px', color: '#CBD5E1' }}>
                        {staff.phoneNumber || <span style={{ color: '#64748B' }}>None</span>}
                      </td>

                      <td style={{ padding: '14px 16px' }}>
                        <Badge variant={staff.active ? 'success' : 'danger'}>
                          {staff.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>

                      <td style={{ padding: '14px 16px', color: '#94A3B8', fontSize: '0.8rem' }}>
                        {new Date(staff.createdAt).toLocaleDateString()}
                      </td>

                      <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                        {!isSelf ? (
                          <Button
                            variant={staff.active ? 'danger' : 'outline'}
                            size="sm"
                            onClick={() => handleToggleStatus(staff)}
                          >
                            {staff.active ? 'Deactivate' : 'Activate'}
                          </Button>
                        ) : (
                          <span style={{ fontSize: '0.75rem', color: '#64748B' }}>Current session</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Add Staff Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Provision New Staff Account"
      >
        <form onSubmit={handleCreateStaff} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <Input
              label="First Name"
              placeholder="e.g. Kavita"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
            />
            <Input
              label="Last Name"
              placeholder="e.g. Sharma"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
            />
          </div>

          <Input
            label="Staff Email Address"
            type="email"
            placeholder="e.g. kavita.operator@smartpark.in"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            leftIcon={<Mail size={18} />}
            required
          />

          <Input
            label="Mobile Number (Optional)"
            placeholder="+91 98765 43210"
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            leftIcon={<Phone size={18} />}
          />

          <Input
            label="Temporary Password"
            type="password"
            placeholder="Minimum 8 characters"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            leftIcon={<Lock size={18} />}
            required
          />

          <Select
            label="Assigned Staff Role"
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            options={[
              { value: 'OPERATOR', label: 'Facility Operator (Manage Floor & Check-ins)' },
              { value: 'ADMIN', label: 'Platform Administrator (Full Oversight)' },
            ]}
          />

          {formError && (
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
              {formError}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '0.5rem' }}>
            <Button
              type="button"
              variant="outline"
              size="md"
              onClick={() => setIsModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="md"
              isLoading={actionLoading}
            >
              Provision Account
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
