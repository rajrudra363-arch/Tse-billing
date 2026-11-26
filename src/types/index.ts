// TS – TOTAL SOLUTION (TOTAL SOLUTION) Data Models & TypeScript Types

export const AppIdentity = {
  OFFICIAL_APP_NAME: 'TS – TOTAL SOLUTION',
  OFFICIAL_COMPANY_NAME: 'TOTAL SOLUTION',
  TAGLINE: 'CCTV Security • Sales • Installation • Support',
  OFFICIAL_MOBILE: '7083805120',
  OFFICIAL_EMAIL: 'totalsolution7020@gmail.com',
  OFFICIAL_ADDRESS: 'B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516.',
  DEFAULT_PAYEE_NAME: 'TOTAL SOLUTION',
} as const;

export type DocumentType = 'BILL' | 'QUOTATION';
export type PaymentMode = 'ONLINE' | 'CASH';
export type BillPaymentStatus = 'PAID' | 'PARTIAL' | 'UNPAID';
export type PaymentGatewayStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'EXPIRED';

export type ChargeType =
  | 'NONE'
  | 'SERVICE'
  | 'INSTALLATION'
  | 'INSTALLATION_AND_SERVICE'
  | 'CUSTOMER_CARE_AND_SUPPORT';

export const ChargeTypeLabels: Record<ChargeType, string> = {
  NONE: 'None',
  SERVICE: 'Service Charges',
  INSTALLATION: 'Installation Charges',
  INSTALLATION_AND_SERVICE: 'Installation & Service Charges',
  CUSTOMER_CARE_AND_SUPPORT: 'Customer Care & Support Charges',
};

export interface InvoiceItem {
  localId: number;
  folder: string;
  description: string;
  qty: number;
  unit: string;
  rate: number;
  amount: number;
}

export interface BillEntity {
  id: string;
  invoiceNo: string;
  date: string;
  customerName: string;
  customerDetails: string;
  itemsJson: string; // Serialized InvoiceItem[]
  subtotal: number;
  discount: number;
  grandTotal: number;
  paidAmount: number;
  dueAmount: number;
  paymentMode: PaymentMode;
  signatureBase64: string;
  customQrText: string;
  createdBy: string;
  updatedAt: number;
  extraChargeType: ChargeType;
  extraChargeAmount: number;
  secondaryChargeType: ChargeType;
  secondaryChargeAmount: number;
  staffRating: number;
  staffReviewNote: string;
  documentType: DocumentType;
  isHelpBill: boolean;
  helpRequestId: number;
  quotationNote: string;
  paymentStatus: BillPaymentStatus;
  linkedPaymentId: string;
  stockDeducted: boolean;
}

export interface CustomerEntity {
  id: number;
  name: string;
  details: string;
  phone: string;
  address: string;
  locationUrl: string;
}

export interface ProductEntity {
  id: number;
  name: string;
  unit: string;
  defaultRate: number;
  folder: string;
  stockQty: number;
  minStockAlert: number;
  purchasePrice: number;
  sku: string;
  barcode: string;
}

export interface ProductComboItem {
  productId: number;
  productName: string;
  unit: string;
  qty: number;
  catalogRate: number;
  folder: string;
}

export interface ProductComboFile {
  comboFileId: string;
  companyId: string;
  name: string;
  description: string;
  items: ProductComboItem[];
  comboPrice: number;
  createdAt: number;
  updatedAt: number;
}

export interface ComboPackage {
  id: string;
  name: string;
  description: string;
  items: {
    productId: number;
    productName: string;
    unit: string;
    qty: number;
    rate: number;
    folder: string;
  }[];
  comboPrice: number;
  createdAt: number;
}

export type UserRole = 'admin' | 'super_admin' | 'technician' | 'staff';

export interface UserAccountEntity {
  username: string;
  displayName: string;
  passwordHash: string;
  role: UserRole;
  phone: string;
  photoBase64: string;
  email: string;
  uid: string;
  techId: string;
  companyId: string;
  loginEnabled: boolean;
}

export interface UserSession {
  username: string;
  displayName: string;
  role: UserRole;
  phone: string;
  photoBase64: string;
  email: string;
  uid: string;
  techId: string;
  companyId: string;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  isTechnician: boolean;
}

export interface CompanySettings {
  companyName: string;
  mobile: string;
  email: string;
  address: string;
  authorisedSignatureBase64: string;
  logoBase64: string;
}

export interface UpiSettings {
  upiId: string;
  payeeName: string;
  fallbackQrBase64: string;
}

export interface VoiceAndNoteSettings {
  headerNote: string;
  loginVoice: string;
  postLoginVoice: string;
  billGeneratingVoice: string;
  billCompletedVoice: string;
  voiceModelId: string;
}

export type HelpCategory = 'REGULAR_SERVICE' | 'EMERGENCY' | 'INSTALLATION' | 'QUOTATION';

export const HelpWorkflowStatus = {
  OPEN: 'OPEN',
  ASSIGNED: 'ASSIGNED',
  ACCEPTED: 'ACCEPTED',
  POSTPONED: 'POSTPONED',
  INSTALLATION_IN_PROGRESS: 'INSTALLATION_IN_PROGRESS',
  INSTALLATION_COMPLETED: 'INSTALLATION_COMPLETED',
  BILLED: 'BILLED',
  SOLVED: 'SOLVED',
  NOT_SOLVED: 'NOT_SOLVED',
  RESOLVED: 'RESOLVED',
  REJECTED: 'REJECTED',
} as const;

export type HelpWorkflowStatusType = (typeof HelpWorkflowStatus)[keyof typeof HelpWorkflowStatus];

export interface CustomerHelpEntity {
  id: number;
  customerName: string;
  phone: string;
  address: string;
  locationUrl: string;
  helpDescription: string;
  status: HelpWorkflowStatusType;
  createdAt: number;
  updatedAt: number;
  acceptedBy: string;
  acceptedByName: string;
  acceptedByPhone: string;
  acceptedAt: number;
  solvedAt: number;
  billId: string;
  category: HelpCategory;
  quotationStatus: string;
  installationDate: string;
  installationReminderSent: boolean;
  rejectedReason: string;
  assignedTechnician: string;
  helpTokenId: string;
  quotationId: string;
  approvedQuotationJson: string;
}

export interface TechnicianSettlementEntity {
  id: number;
  technicianUsername: string;
  amount: number;
  note: string;
  recordedBy: string;
  createdAt: number;
}

export interface ExpenseEntity {
  id: string;
  title: string;
  category: string;
  amount: number;
  date: string;
  note: string;
  recordedBy: string;
  createdAt: number;
  updatedAt: number;
}

export interface TrashItemEntity {
  id: string;
  entityType: 'bill' | 'customer' | 'product' | 'help' | 'expense' | 'employee';
  entityId: string;
  title: string;
  summary: string;
  payloadJson: string;
  deletedBy: string;
  deletedByRole: string;
  status: 'TRASHED' | 'RESTORED';
  deletedAt: number;
  expireAt: number;
  restoredAt: number;
  restoredBy: string;
}

export interface AuditLogEntity {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  title: string;
  actorUsername: string;
  actorRole: string;
  details: string;
  createdAt: number;
}

export interface EmployeeEntity {
  employeeId: string;
  employeeCode: string;
  companyId: string;
  name: string;
  mobile: string;
  hiringDate: string;
  workStartDate: string;
  workStopDate: string;
  monthlySalary: number;
  address: string;
  notes: string;
  isActive: boolean;
  createdAt: number;
  updatedAt: number;
  createdByUid: string;
}

export interface SalaryPaymentEntity {
  paymentId: string;
  employeeId: string;
  companyId: string;
  amount: number;
  paymentDate: string;
  method: string;
  referenceNo: string;
  notes: string;
  cycleStart: string;
  cycleEnd: string;
  createdAt: number;
  createdByUid: string;
}

export type SalaryPaymentStatus = 'PENDING' | 'PARTIALLY_PAID' | 'FULLY_PAID';

export interface EmployeeSalarySummary {
  employee: EmployeeEntity;
  cycleStart: string;
  cycleEnd: string;
  calendarDaysInCycle: number;
  eligibleDays: number;
  dailySalary: number;
  earnedSalary: number;
  totalPaidInCycle: number;
  totalPaidAllTime: number;
  remainingBalance: number;
  status: SalaryPaymentStatus;
  cyclePayments: SalaryPaymentEntity[];
}

export interface UpiTransactionEntity {
  paymentId: string;
  billId: string;
  invoiceNo: string;
  orderId: string;
  amount: number;
  status: PaymentGatewayStatus;
  paymentGateway: 'MANUAL_UPI' | 'RAZORPAY' | 'CASHFREE';
  gatewaySignature: string;
  qrCodeUrl: string;
  customerEmail: string;
  customerPhone: string;
  createdAt: number;
  updatedAt: number;
  companyId: string;
  customerId: string;
  customerName: string;
  paymentRef: string;
  txId: string;
  upiUri: string;
  receiptNumber: string;
  expiresAtMillis: number;
}

export interface CloudWorkspaceNotification {
  id: string;
  category: string;
  title: string;
  message: string;
  sender: string;
  targetRole: string;
  timestamp: number;
  read: boolean;
}

export interface TechnicianPerformanceSummary {
  technicianUsername: string;
  displayName: string;
  phone: string;
  totalBillsGenerated: number;
  totalBilledAmount: number;
  totalCashCollected: number;
  totalOnlineCollected: number;
  totalDuePending: number;
  cashSettledWithAdmin: number;
  cashHeldInHand: number;
  averageRating: number;
  ratedBillsCount: number;
}

export interface BillDraft {
  documentType: DocumentType;
  invoiceNo: string;
  date: string;
  customerName: string;
  customerDetails: string;
  customerLocationUrl: string;
  items: InvoiceItem[];
  subtotal: number;
  discount: number;
  extraChargeType: ChargeType;
  extraChargeAmount: number;
  secondaryChargeType: ChargeType;
  secondaryChargeAmount: number;
  grandTotal: number;
  paidAmount: number;
  dueAmount: number;
  paymentMode: PaymentMode;
  signatureBase64: string;
  customQrText: string;
  staffRating: number;
  staffReviewNote: string;
  quotationNote: string;
  isHelpBill: boolean;
  helpRequestId: number;
}
