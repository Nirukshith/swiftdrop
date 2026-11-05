export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  phoneNumber: string;
  role: 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER' | 'ADMIN';
  enabled: boolean;
  createdAt: string;
  updatedAt?: string;
}

export type StaffUser = UserSummary;

export interface UserListPage {
  content: UserSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface CreateStaffUserRequest {
  email: string;
  password: string;
  fullName: string;
  phoneNumber: string;
  role: 'COURIER' | 'DISPATCHER';
}

export type CreateStaffPayload = CreateStaffUserRequest;

export interface UpdateUserStatusRequest {
  enabled: boolean;
}

export interface AdminUserFilterParams {
  role?: string;
  enabled?: boolean;
  page?: number;
  size?: number;
}

