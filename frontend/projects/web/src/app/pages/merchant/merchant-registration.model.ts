export interface MerchantRegistrationRequest {
  businessName: string;
  contactName: string;
  email: string;
  phoneNumber: string;
  businessRegistrationNumber?: string;
}

export interface MerchantRegistrationResponse extends MerchantRegistrationRequest {
  id: string;
  status: 'PENDING';
  createdAt: string;
  message: string;
}
