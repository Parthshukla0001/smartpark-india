import { apiClient } from './client';
import {
  AuthResponse,
  Booking,
  BookingStatus,
  Notification,
  PageResponse,
  ParkingFloor,
  ParkingLot,
  ParkingSlot,
  Role,
  SlotStatus,
  SlotType,
  StaffMember,
  Vehicle,
  VehicleType,
  ActuatorHealthResponse,
  ActuatorMetricResponse,
} from '../types';

export const authApi = {
  login: (data: { email: string; password: string }) =>
    apiClient.post<AuthResponse>('/api/auth/login', data),

  register: (data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    phoneNumber?: string;
  }) => apiClient.post<any>('/api/auth/register', data),
};

export const staffApi = {
  getAllStaff: () => apiClient.get<StaffMember[]>('/api/admin/staff'),

  createStaff: (data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    phoneNumber?: string;
    role?: Role;
  }) => apiClient.post<StaffMember>('/api/admin/staff', data),

  updateStaffStatus: (id: number, active: boolean) =>
    apiClient.patch<StaffMember>(`/api/admin/staff/${id}/status?active=${active}`),
};

export const parkingApi = {
  getParkingLots: (params?: { city?: string; active?: boolean; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (params?.city) query.append('city', params.city);
    if (params?.active !== undefined) query.append('active', String(params.active));
    if (params?.page !== undefined) query.append('page', String(params.page));
    if (params?.size !== undefined) query.append('size', String(params.size));
    const qs = query.toString();
    return apiClient.get<PageResponse<ParkingLot>>(`/api/parking-lots${qs ? `?${qs}` : ''}`);
  },

  getParkingLot: (id: number) => apiClient.get<ParkingLot>(`/api/parking-lots/${id}`),

  createParkingLot: (data: Partial<ParkingLot>) =>
    apiClient.post<ParkingLot>('/api/parking-lots', data),

  updateParkingLot: (id: number, data: Partial<ParkingLot>) =>
    apiClient.put<ParkingLot>(`/api/parking-lots/${id}`, data),

  getFloors: (lotId: number) => apiClient.get<ParkingFloor[]>(`/api/parking-lots/${lotId}/floors`),

  createFloor: (lotId: number, data: { floorNumber: number; floorName: string }) =>
    apiClient.post<ParkingFloor>(`/api/parking-lots/${lotId}/floors`, data),

  getSlots: (floorId: number) => apiClient.get<ParkingSlot[]>(`/api/floors/${floorId}/slots`),

  createSlot: (floorId: number, data: { slotNumber: string; slotType: SlotType; hourlyRateOverride?: number | null }) =>
    apiClient.post<ParkingSlot>(`/api/floors/${floorId}/slots`, data),

  updateSlotStatus: (slotId: number, status: SlotStatus) =>
    apiClient.patch<ParkingSlot>(`/api/slots/${slotId}/status`, { status }),
};

export const bookingApi = {
  createBooking: (data: {
    vehicleId: number;
    parkingSlotId: number;
    startTime: string;
    endTime: string;
  }) => apiClient.post<Booking>('/api/bookings', data),

  getMyBookings: (params?: { status?: BookingStatus; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (params?.status) query.append('status', params.status);
    if (params?.page !== undefined) query.append('page', String(params.page));
    if (params?.size !== undefined) query.append('size', String(params.size));
    const qs = query.toString();
    return apiClient.get<PageResponse<Booking>>(`/api/bookings${qs ? `?${qs}` : ''}`);
  },

  getAllBookings: (params?: { status?: BookingStatus; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (params?.status) query.append('status', params.status);
    if (params?.page !== undefined) query.append('page', String(params.page));
    if (params?.size !== undefined) query.append('size', String(params.size));
    const qs = query.toString();
    return apiClient.get<PageResponse<Booking>>(`/api/bookings/all${qs ? `?${qs}` : ''}`);
  },

  getBookingById: (id: number) => apiClient.get<Booking>(`/api/bookings/${id}`),

  cancelBooking: (id: number) => apiClient.patch<Booking>(`/api/bookings/${id}/cancel`),

  confirmBooking: (id: number) => apiClient.post<Booking>(`/api/bookings/${id}/confirm`),

  completeBooking: (id: number) => apiClient.post<Booking>(`/api/bookings/${id}/complete`),

  expireBooking: (id: number) => apiClient.post<Booking>(`/api/bookings/${id}/expire`),
};

export const vehicleApi = {
  getMyVehicles: () => apiClient.get<Vehicle[]>('/api/vehicles'),

  registerVehicle: (data: { registrationNumber: string; vehicleType: VehicleType }) =>
    apiClient.post<Vehicle>('/api/vehicles', data),

  deleteVehicle: (id: number) => apiClient.delete<void>(`/api/vehicles/${id}`),
};

export const notificationApi = {
  getMyNotifications: () => apiClient.get<Notification[]>('/api/notifications'),

  markAsRead: (id: number) => apiClient.patch<Notification>(`/api/notifications/${id}/read`),
};

export const actuatorApi = {
  getHealth: () => apiClient.get<ActuatorHealthResponse>('/actuator/health'),

  getMetric: (name: string) => apiClient.get<ActuatorMetricResponse>(`/actuator/metrics/${name}`),

  getMetricNames: () => apiClient.get<{ names: string[] }>('/actuator/metrics'),
};
