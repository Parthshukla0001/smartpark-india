export type Role = 'DRIVER' | 'OPERATOR' | 'ADMIN';

export type VehicleType = 'CAR' | 'MOTORCYCLE' | 'SUV' | 'OTHER';

export type SlotType = 'REGULAR' | 'COMPACT' | 'LARGE' | 'EV_CHARGING' | 'HANDICAPPED';

export type SlotStatus = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'OUT_OF_SERVICE';

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';

export interface User {
  id: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
}

export interface StaffMember {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
}

export interface Vehicle {
  id: number;
  userId: number;
  registrationNumber: string;
  vehicleType: VehicleType;
  active: boolean;
  createdAt: string;
}

export interface ParkingLot {
  id: number;
  name: string;
  code: string;
  address: string;
  city: string;
  state: string;
  postalCode: string;
  totalFloors: number;
  totalCapacity: number;
  active: boolean;
  openingTime: string;
  closingTime: string;
  hourlyRate: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ParkingFloor {
  id: number;
  parkingLotId: number;
  floorNumber: number;
  floorName: string;
  totalSlots: number;
  active: boolean;
}

export interface ParkingSlot {
  id: number;
  floorId: number;
  slotNumber: string;
  slotType: SlotType;
  status: SlotStatus;
  active: boolean;
  hourlyRateOverride?: number | null;
}

export interface Booking {
  id: number;
  bookingReference: string;
  userId: number;
  userEmail: string;
  parkingSlotId: number;
  slotNumber: string;
  floorName: string;
  parkingLotName: string;
  city: string;
  vehicleId: number;
  vehicleRegistrationNumber: string;
  startTime: string;
  endTime: string;
  totalAmount: number;
  status: BookingStatus;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

export interface Notification {
  id: number;
  eventId: string;
  type: string;
  message: string;
  createdAt: string;
  readAt: string | null;
}

export interface ParkingEventPayload {
  eventType: string;
  lotId: number;
  floorId?: number;
  slotId?: number;
  slotNumber?: string;
  slotType?: SlotType;
  slotStatus?: SlotStatus;
  bookingId?: number;
  bookingStatus?: BookingStatus;
  startTime?: string;
  endTime?: string;
  timestamp: string;
}

export interface ActuatorHealthComponent {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
  details?: Record<string, unknown>;
}

export interface ActuatorHealthResponse {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
  components?: {
    db?: ActuatorHealthComponent;
    redis?: ActuatorHealthComponent;
    kafka?: ActuatorHealthComponent;
    diskSpace?: ActuatorHealthComponent;
    ping?: ActuatorHealthComponent;
    [key: string]: ActuatorHealthComponent | undefined;
  };
}

export interface ActuatorMetricMeasurement {
  statistic: string;
  value: number;
}

export interface ActuatorMetricResponse {
  name: string;
  description?: string;
  baseUnit?: string;
  measurements: ActuatorMetricMeasurement[];
  availableTags?: Array<{ tag: string; values: string[] }>;
}
