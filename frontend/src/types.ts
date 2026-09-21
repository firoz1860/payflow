export interface User {
  id: string;
  email: string;
  fullName: string;
  merchantId: string | null;
  status: string;
  emailVerified: boolean;
  roles: string[];
  permissions: string[];
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}

export interface Merchant {
  id: string;
  merchantCode: string;
  businessName: string;
  email: string;
  phone: string;
  status: string;
  country: string;
  defaultCurrency: string;
  feePercentage: number;
  fixedFee: number;
  settlementDelayDays: number;
  liveModeEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiKey {
  keyId: string;
  maskedKey: string;
  environment: string;
  keyType: string;
  status: string;
  label: string;
  scopes: string[];
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
}

export interface CreateApiKeyResponse {
  keyId: string;
  secret: string;
  maskedKey: string;
  environment: string;
  keyType: string;
  scopes: string[];
  expiresAt: string | null;
  warning: string;
}

export interface Attempt {
  attemptNumber: number;
  provider: string;
  paymentMethod: string;
  status: string;
  amount: number;
  cardLast4: string | null;
  cardNetwork: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  createdAt: string;
}

export interface QrCode {
  data: string;
  image: string;
}

export interface Payment {
  paymentReference: string;
  merchantOrderId: string | null;
  amount: number;
  currency: string;
  refundedAmount: number;
  refundableAmount: number;
  status: string;
  environment: string;
  description: string | null;
  provider: string | null;
  checkoutUrl: string | null;
  qrCode: QrCode | null;
  failureCode: string | null;
  failureMessage: string | null;
  metadata: Record<string, string> | null;
  attempts: Attempt[];
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
}

export interface PageResponse<T> {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  code: string;
  message: string;
  correlationId: string;
  timestamp: string;
}

export type PaymentMethod = 'CARD' | 'UPI' | 'QR' | 'NET_BANKING' | 'WALLET';
export type RoleName = 'PAYFLOW_ADMIN' | 'MERCHANT_OWNER' | 'MERCHANT_DEVELOPER' | 'MERCHANT_FINANCE' | 'MERCHANT_SUPPORT';
export type MerchantStatus = 'ACTIVE' | 'SUSPENDED' | 'BLOCKED' | 'PENDING';
