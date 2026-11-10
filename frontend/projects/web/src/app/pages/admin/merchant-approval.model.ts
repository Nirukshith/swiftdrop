export type MerchantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';

export interface MerchantSummary {
  id: string;
  businessName: string;
  contactName: string;
  email: string;
  phoneNumber: string;
  status: MerchantStatus;
  rejectionReason: string | null;
  createdAt: string;
}

export interface MerchantPage {
  content: MerchantSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface MerchantRejectionRequest {
  reason: string;
}

export interface MerchantApprovalResponse {
  merchant: MerchantSummary;
  clientId: string;
  clientSecret?: string;
}
