import {
  BillEntity,
  CustomerEntity,
  ProductEntity,
  ProductComboFile,
  ComboPackage,
  UserAccountEntity,
  CompanySettings,
  UpiSettings,
  VoiceAndNoteSettings,
  CustomerHelpEntity,
  ExpenseEntity,
  TrashItemEntity,
  AuditLogEntity,
  EmployeeEntity,
  SalaryPaymentEntity,
  CloudWorkspaceNotification,
  TechnicianSettlementEntity,
  AppIdentity,
  BillPaymentStatus,
} from '../types';

const STORAGE_KEYS = {
  BILLS: 'tse_bills_v2',
  CUSTOMERS: 'tse_customers_v2',
  PRODUCTS: 'tse_products_v2',
  COMBO_FILES: 'tse_combo_files_v2',
  USERS: 'tse_users_v2',
  COMPANY: 'tse_company_settings_v2',
  UPI: 'tse_upi_settings_v2',
  VOICE: 'tse_voice_settings_v2',
  HELP: 'tse_help_requests_v2',
  EXPENSES: 'tse_expenses_v2',
  TRASH: 'tse_trash_v2',
  AUDIT: 'tse_audit_logs_v2',
  EMPLOYEES: 'tse_employees_v2',
  SALARY_PAYMENTS: 'tse_salary_payments_v2',
  NOTIFICATIONS: 'tse_notifications_v2',
  SETTLEMENTS: 'tse_technician_settlements_v2',
  ADMIN_CONFIGURED: 'tse_admin_configured_v2',
  ADMIN_VERIFY_CODE: 'tse_admin_verify_code_v2',
  LAST_INVOICE_SEQ: 'tse_last_inv_seq_v2',
};

// Initial Company Defaults
export const defaultCompanySettings: CompanySettings = {
  companyName: AppIdentity.OFFICIAL_COMPANY_NAME,
  mobile: AppIdentity.OFFICIAL_MOBILE,
  email: AppIdentity.OFFICIAL_EMAIL,
  address: AppIdentity.OFFICIAL_ADDRESS,
  authorisedSignatureBase64: '',
  logoBase64: '/img_tse_logo.jpg',
};

export const defaultUpiSettings: UpiSettings = {
  upiId: 'totalsolution@upi',
  payeeName: AppIdentity.OFFICIAL_COMPANY_NAME,
  fallbackQrBase64: '',
};

export const defaultVoiceSettings: VoiceAndNoteSettings = {
  headerNote: `Welcome to ${AppIdentity.OFFICIAL_COMPANY_NAME} — CCTV Security, Sales, Installation & Support`,
  loginVoice: `Welcome to ${AppIdentity.OFFICIAL_APP_NAME}. Select your role and sign in.`,
  postLoginVoice: `Login successful. Welcome to ${AppIdentity.OFFICIAL_COMPANY_NAME}.`,
  billGeneratingVoice: 'Generating bill. Please verify customer summary.',
  billCompletedVoice: `Bill generated. Total amount is {amount} rupees. Thank you for choosing ${AppIdentity.OFFICIAL_COMPANY_NAME}.`,
  voiceModelId: 'preset_clear',
};

// Seed Products
const initialProducts: ProductEntity[] = [
  { id: 1, name: 'Hikvision 2MP HD 1080P Dome Camera (Night Vision)', unit: 'Nos', defaultRate: 1650, folder: 'Hikvision', stockQty: 24, minStockAlert: 5, purchasePrice: 1250, sku: 'HIK-DOM-2MP', barcode: '8901001001' },
  { id: 2, name: 'Hikvision 2MP HD Bullet Camera (Outdoor Weatherproof)', unit: 'Nos', defaultRate: 1750, folder: 'Hikvision', stockQty: 18, minStockAlert: 5, purchasePrice: 1350, sku: 'HIK-BUL-2MP', barcode: '8901001002' },
  { id: 3, name: 'Hikvision 5MP ColorVu Full-Color Audio Dome Camera', unit: 'Nos', defaultRate: 2650, folder: 'Hikvision', stockQty: 12, minStockAlert: 3, purchasePrice: 2050, sku: 'HIK-CV-5MP', barcode: '8901001003' },
  { id: 4, name: 'Dahua 4-Channel 1080P XVR / DVR (Smart H.265+)', unit: 'Nos', defaultRate: 3200, folder: 'Dahua', stockQty: 8, minStockAlert: 2, purchasePrice: 2400, sku: 'DH-XVR-4CH', barcode: '8901002001' },
  { id: 5, name: 'Dahua 8-Channel 1080P XVR / DVR with Audio Support', unit: 'Nos', defaultRate: 4800, folder: 'Dahua', stockQty: 6, minStockAlert: 2, purchasePrice: 3800, sku: 'DH-XVR-8CH', barcode: '8901002002' },
  { id: 6, name: 'CP Plus 4-Channel SMPS CCTV Power Supply with Fuse', unit: 'Nos', defaultRate: 950, folder: 'Power Supply', stockQty: 14, minStockAlert: 4, purchasePrice: 650, sku: 'CP-PWR-4CH', barcode: '8901003001' },
  { id: 7, name: 'CP Plus 8-Channel SMPS CCTV Regulated Power Supply', unit: 'Nos', defaultRate: 1450, folder: 'Power Supply', stockQty: 9, minStockAlert: 3, purchasePrice: 1050, sku: 'CP-PWR-8CH', barcode: '8901003002' },
  { id: 8, name: 'Seagate SkyHawk 1TB Surveillance CCTV Hard Disk', unit: 'Nos', defaultRate: 4200, folder: 'Hard Disk', stockQty: 10, minStockAlert: 3, purchasePrice: 3400, sku: 'SEA-SUR-1TB', barcode: '8901004001' },
  { id: 9, name: 'Seagate SkyHawk 2TB Surveillance CCTV Hard Disk', unit: 'Nos', defaultRate: 6100, folder: 'Hard Disk', stockQty: 7, minStockAlert: 2, purchasePrice: 5100, sku: 'SEA-SUR-2TB', barcode: '8901004002' },
  { id: 10, name: 'D-Link Cat6 Solid Copper UTP Network Cable (305m)', unit: 'Bundle', defaultRate: 4800, folder: 'Cable', stockQty: 4, minStockAlert: 1, purchasePrice: 3800, sku: 'DL-CAT6-305', barcode: '8901005001' },
  { id: 11, name: '3+1 Pure Copper Shielded CCTV Coaxial Cable (90m)', unit: 'Coil', defaultRate: 1250, folder: 'Cable', stockQty: 16, minStockAlert: 4, purchasePrice: 850, sku: 'CBL-31-90M', barcode: '8901005002' },
  { id: 12, name: 'BNC Heavy Duty Pure Copper Connector with Spring', unit: 'Nos', defaultRate: 45, folder: 'Accessories', stockQty: 140, minStockAlert: 30, purchasePrice: 20, sku: 'ACC-BNC-CU', barcode: '8901006001' },
  { id: 13, name: 'DC Heavy Duty Male Power Plug Connector', unit: 'Nos', defaultRate: 30, folder: 'Accessories', stockQty: 160, minStockAlert: 30, purchasePrice: 12, sku: 'ACC-DC-PLUG', barcode: '8901006002' },
  { id: 14, name: 'Waterproof Camera PVC Junction Box 4x4 with Screws', unit: 'Nos', defaultRate: 95, folder: 'Accessories', stockQty: 55, minStockAlert: 15, purchasePrice: 50, sku: 'ACC-JUNC-4X4', barcode: '8901006003' },
  { id: 15, name: 'Heavy Duty 4U Wall-Mount DVR Rack with Glass Door & Lock', unit: 'Nos', defaultRate: 1850, folder: 'Accessories', stockQty: 6, minStockAlert: 2, purchasePrice: 1300, sku: 'ACC-RACK-4U', barcode: '8901006004' },
  { id: 16, name: 'High Speed 4K 1.5M Gold-Plated HDMI Cable', unit: 'Nos', defaultRate: 250, folder: 'Accessories', stockQty: 25, minStockAlert: 5, purchasePrice: 140, sku: 'ACC-HDMI-15M', barcode: '8901006005' },
];

// Seed Combo Packages
const initialCombos: ProductComboFile[] = [
  {
    comboFileId: 'combo_4cam_standard',
    companyId: 'total_solution',
    name: '4-Camera HD Standard Villa Security Combo',
    description: 'Complete 4-camera CCTV installation package with 1TB Surveillance HDD, DVR, power supply, cables and fittings.',
    items: [
      { productId: 1, productName: 'Hikvision 2MP HD 1080P Dome Camera (Night Vision)', unit: 'Nos', qty: 2, catalogRate: 1650, folder: 'Hikvision' },
      { productId: 2, productName: 'Hikvision 2MP HD Bullet Camera (Outdoor Weatherproof)', unit: 'Nos', qty: 2, catalogRate: 1750, folder: 'Hikvision' },
      { productId: 4, productName: 'Dahua 4-Channel 1080P XVR / DVR (Smart H.265+)', unit: 'Nos', qty: 1, catalogRate: 3200, folder: 'Dahua' },
      { productId: 8, productName: 'Seagate SkyHawk 1TB Surveillance CCTV Hard Disk', unit: 'Nos', qty: 1, catalogRate: 4200, folder: 'Hard Disk' },
      { productId: 6, productName: 'CP Plus 4-Channel SMPS CCTV Power Supply with Fuse', unit: 'Nos', qty: 1, catalogRate: 950, folder: 'Power Supply' },
      { productId: 11, productName: '3+1 Pure Copper Shielded CCTV Coaxial Cable (90m)', unit: 'Coil', qty: 1, catalogRate: 1250, folder: 'Cable' },
      { productId: 12, productName: 'BNC Heavy Duty Pure Copper Connector with Spring', unit: 'Nos', qty: 8, catalogRate: 45, folder: 'Accessories' },
      { productId: 13, productName: 'DC Heavy Duty Male Power Plug Connector', unit: 'Nos', qty: 4, catalogRate: 30, folder: 'Accessories' },
      { productId: 14, productName: 'Waterproof Camera PVC Junction Box 4x4 with Screws', unit: 'Nos', qty: 4, catalogRate: 95, folder: 'Accessories' },
      { productId: 15, productName: 'Heavy Duty 4U Wall-Mount DVR Rack with Glass Door & Lock', unit: 'Nos', qty: 1, catalogRate: 1850, folder: 'Accessories' },
      { productId: 16, productName: 'High Speed 4K 1.5M Gold-Plated HDMI Cable', unit: 'Nos', qty: 1, catalogRate: 250, folder: 'Accessories' },
    ],
    comboPrice: 17999,
    createdAt: Date.now() - 86400000 * 10,
    updatedAt: Date.now() - 86400000 * 10,
  },
];

// Seed Customers
const initialCustomers: CustomerEntity[] = [
  {
    id: 1,
    name: 'Dr. Rajesh Naik',
    details: 'Phone: 9822114455\nNaika Vaddo, Calangute, Goa 403516',
    phone: '9822114455',
    address: 'Naika Vaddo, Calangute, Goa 403516',
    locationUrl: 'https://maps.google.com/?q=15.543,73.755',
  },
  {
    id: 2,
    name: 'Sunil Shetty - Sea Breeze Resort',
    details: 'Phone: 9422003322\nBaga Beach Road, Arpora, North Goa',
    phone: '9422003322',
    address: 'Baga Beach Road, Arpora, North Goa',
    locationUrl: 'https://maps.google.com/?q=15.558,73.751',
  },
  {
    id: 3,
    name: 'Goa Supermarket & Cold Storage',
    details: 'Phone: 8322271890\nMain Market, Mapusa, Goa 403507',
    phone: '8322271890',
    address: 'Main Market, Mapusa, Goa 403507',
    locationUrl: 'https://maps.google.com/?q=15.592,73.816',
  },
];

// Seed Employees
const initialEmployees: EmployeeEntity[] = [
  {
    employeeId: 'emp_ramesh_101',
    employeeCode: 'EMP-101',
    companyId: 'total_solution',
    name: 'Ramesh Verma (Senior CCTV Technician)',
    mobile: '9823123456',
    hiringDate: '2024-01-15',
    workStartDate: '2024-01-15',
    workStopDate: '',
    monthlySalary: 28000,
    address: 'House No 45, Porvorim, North Goa',
    notes: 'Certified CCTV Specialist & IP Camera Configurator',
    isActive: true,
    createdAt: Date.now() - 86400000 * 60,
    updatedAt: Date.now() - 86400000 * 60,
    createdByUid: 'admin',
  },
  {
    employeeId: 'emp_amit_102',
    employeeCode: 'EMP-102',
    companyId: 'total_solution',
    name: 'Amit Sawant (Field Cable & Cabling Tech)',
    mobile: '9765432109',
    hiringDate: '2024-03-01',
    workStartDate: '2024-03-01',
    workStopDate: '',
    monthlySalary: 19500,
    address: 'Naika Vaddo, Calangute, Goa',
    notes: 'Field wiring, trunking, conduit and pole mounting',
    isActive: true,
    createdAt: Date.now() - 86400000 * 30,
    updatedAt: Date.now() - 86400000 * 30,
    createdByUid: 'admin',
  },
];

// Seed Users
const initialUsers: UserAccountEntity[] = [
  {
    username: '@TotalSolutionAdmin',
    displayName: 'Admin (TOTAL SOLUTION)',
    passwordHash: 'Total@1234', // default demo password meeting strength requirements
    role: 'admin',
    phone: AppIdentity.OFFICIAL_MOBILE,
    photoBase64: '',
    email: AppIdentity.OFFICIAL_EMAIL,
    uid: 'admin_root',
    techId: '',
    companyId: 'total_solution',
    loginEnabled: true,
  },
  {
    username: '@superadmin',
    displayName: 'Super Admin',
    passwordHash: 'Super@1234',
    role: 'super_admin',
    phone: '7083805120',
    photoBase64: '',
    email: 'superadmin@totalsolution.com',
    uid: 'superadmin_1',
    techId: '',
    companyId: 'total_solution',
    loginEnabled: true,
  },
  {
    username: '@ramesh_tech',
    displayName: 'Ramesh Verma',
    passwordHash: 'Tech@1234',
    role: 'technician',
    phone: '9823123456',
    photoBase64: '',
    email: 'ramesh@totalsolution.com',
    uid: 'tech_ramesh',
    techId: 'EMP-101',
    companyId: 'total_solution',
    loginEnabled: true,
  },
];

// Seed Help Requests
const initialHelpRequests: CustomerHelpEntity[] = [
  {
    id: 101,
    customerName: 'Sunil Shetty - Sea Breeze Resort',
    phone: '9422003322',
    address: 'Baga Beach Road, Arpora, North Goa',
    locationUrl: 'https://maps.google.com/?q=15.558,73.751',
    helpDescription: 'Camera 3 and Camera 4 showing Video Loss on DVR monitor after storm yesterday.',
    status: 'ACCEPTED',
    createdAt: Date.now() - 86400000 * 2,
    updatedAt: Date.now() - 86400000 * 1,
    acceptedBy: '@ramesh_tech',
    acceptedByName: 'Ramesh Verma',
    acceptedByPhone: '9823123456',
    acceptedAt: Date.now() - 86400000 * 1,
    solvedAt: 0,
    billId: '',
    category: 'REGULAR_SERVICE',
    quotationStatus: '',
    installationDate: '2026-11-28',
    installationReminderSent: false,
    rejectedReason: '',
    assignedTechnician: '@ramesh_tech',
    helpTokenId: 'TKN-8821',
    quotationId: '',
    approvedQuotationJson: '',
  },
  {
    id: 102,
    customerName: 'Hotel Cidade Vista',
    phone: '9823998877',
    address: 'Near St. Anthony Chapel, Candolim, Goa',
    locationUrl: 'https://maps.google.com/?q=15.518,73.766',
    helpDescription: 'Quotation requested for 8 IP Cameras setup across perimeter wall and pool area.',
    status: 'OPEN',
    createdAt: Date.now() - 86400000 * 1,
    updatedAt: Date.now() - 86400000 * 1,
    acceptedBy: '',
    acceptedByName: '',
    acceptedByPhone: '',
    acceptedAt: 0,
    solvedAt: 0,
    billId: '',
    category: 'QUOTATION',
    quotationStatus: 'PENDING_APPROVAL',
    installationDate: '',
    installationReminderSent: false,
    rejectedReason: '',
    assignedTechnician: '',
    helpTokenId: 'TKN-8822',
    quotationId: '',
    approvedQuotationJson: '',
  },
];

// Seed Bills
const initialBills: BillEntity[] = [
  {
    id: 'bill_demo_1',
    invoiceNo: 'TSE-INV-20261120-001',
    date: '2026-11-20',
    customerName: 'Dr. Rajesh Naik',
    customerDetails: 'Phone: 9822114455\nNaika Vaddo, Calangute, Goa 403516',
    itemsJson: JSON.stringify([
      { localId: 1, folder: 'Hikvision', description: 'Hikvision 2MP HD 1080P Dome Camera (Night Vision)', qty: 2, unit: 'Nos', rate: 1650, amount: 3300 },
      { localId: 2, folder: 'Accessories', description: 'BNC Heavy Duty Pure Copper Connector with Spring', qty: 4, unit: 'Nos', rate: 45, amount: 180 },
      { localId: 3, folder: 'Accessories', description: 'DC Heavy Duty Male Power Plug Connector', qty: 2, unit: 'Nos', rate: 30, amount: 60 },
    ]),
    subtotal: 3540,
    discount: 140,
    grandTotal: 4000,
    paidAmount: 4000,
    dueAmount: 0,
    paymentMode: 'ONLINE',
    signatureBase64: '',
    customQrText: '',
    createdBy: '@ramesh_tech',
    updatedAt: Date.now() - 86400000 * 6,
    extraChargeType: 'INSTALLATION_AND_SERVICE',
    extraChargeAmount: 600,
    secondaryChargeType: 'NONE',
    secondaryChargeAmount: 0,
    staffRating: 5,
    staffReviewNote: 'Excellent clean installation, proper cable concealed in casing.',
    documentType: 'BILL',
    isHelpBill: false,
    helpRequestId: 0,
    quotationNote: '',
    paymentStatus: 'PAID',
    linkedPaymentId: 'UPI-TXN-99881',
    stockDeducted: true,
  },
  {
    id: 'bill_demo_2',
    invoiceNo: 'TSE-INV-20261122-002',
    date: '2026-11-22',
    customerName: 'Goa Supermarket & Cold Storage',
    customerDetails: 'Phone: 8322271890\nMain Market, Mapusa, Goa 403507',
    itemsJson: JSON.stringify([
      { localId: 1, folder: 'Dahua', description: 'Dahua 8-Channel 1080P XVR / DVR with Audio Support', qty: 1, unit: 'Nos', rate: 4800, amount: 4800 },
      { localId: 2, folder: 'Hard Disk', description: 'Seagate SkyHawk 2TB Surveillance CCTV Hard Disk', qty: 1, unit: 'Nos', rate: 6100, amount: 6100 },
      { localId: 3, folder: 'Power Supply', description: 'CP Plus 8-Channel SMPS CCTV Regulated Power Supply', qty: 1, unit: 'Nos', rate: 1450, amount: 1450 },
    ]),
    subtotal: 12350,
    discount: 350,
    grandTotal: 13500,
    paidAmount: 8500,
    dueAmount: 5000,
    paymentMode: 'ONLINE',
    signatureBase64: '',
    customQrText: '',
    createdBy: '@TotalSolutionAdmin',
    updatedAt: Date.now() - 86400000 * 4,
    extraChargeType: 'INSTALLATION',
    extraChargeAmount: 1500,
    secondaryChargeType: 'NONE',
    secondaryChargeAmount: 0,
    staffRating: 5,
    staffReviewNote: 'DVR replaced and 2TB hard disk configured. Remaining 5,000 due on Monday.',
    documentType: 'BILL',
    isHelpBill: false,
    helpRequestId: 0,
    quotationNote: '',
    paymentStatus: 'PARTIAL',
    linkedPaymentId: 'UPI-TXN-99882',
    stockDeducted: true,
  },
];

// Helper to load or initialize from localStorage
function getStored<T>(key: string, defaultValue: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) {
      localStorage.setItem(key, JSON.stringify(defaultValue));
      return defaultValue;
    }
    return JSON.parse(raw);
  } catch {
    return defaultValue;
  }
}

function setStored<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    console.error(`Error saving ${key} to storage:`, e);
  }
}

export class AppDb {
  public static getBills(): BillEntity[] {
    return getStored<BillEntity[]>(STORAGE_KEYS.BILLS, initialBills);
  }

  public static saveBill(bill: BillEntity): void {
    const bills = this.getBills();
    const idx = bills.findIndex((b) => b.id === bill.id);
    if (idx >= 0) {
      bills[idx] = { ...bill, updatedAt: Date.now() };
    } else {
      bills.unshift({ ...bill, updatedAt: Date.now() });
    }
    setStored(STORAGE_KEYS.BILLS, bills);

    // Update Customer details if matched
    if (bill.customerName.trim()) {
      this.upsertCustomerFromBill(bill);
    }
  }

  public static deleteBill(id: string, actor: string = 'admin'): boolean {
    const bills = this.getBills();
    const target = bills.find((b) => b.id === id);
    if (!target) return false;

    // Soft delete into Trash
    const trashItem: TrashItemEntity = {
      id: `trash_${Date.now()}_${id}`,
      entityType: 'bill',
      entityId: id,
      title: `${target.documentType === 'QUOTATION' ? 'Quotation' : 'Invoice'} #${target.invoiceNo} (${target.customerName})`,
      summary: `Amount: ₹${target.grandTotal.toFixed(2)} | Date: ${target.date}`,
      payloadJson: JSON.stringify(target),
      deletedBy: actor,
      deletedByRole: 'admin',
      status: 'TRASHED',
      deletedAt: Date.now(),
      expireAt: Date.now() + 86400000, // 24 hours retention
      restoredAt: 0,
      restoredBy: '',
    };
    this.addTrashItem(trashItem);

    const filtered = bills.filter((b) => b.id !== id);
    setStored(STORAGE_KEYS.BILLS, filtered);
    this.logAudit('DELETE_BILL', 'bill', id, target.invoiceNo, actor, 'admin', `Deleted ${target.documentType} #${target.invoiceNo}`);
    return true;
  }

  public static getCustomers(): CustomerEntity[] {
    return getStored<CustomerEntity[]>(STORAGE_KEYS.CUSTOMERS, initialCustomers);
  }

  public static saveCustomer(customer: CustomerEntity): void {
    const customers = this.getCustomers();
    const idx = customers.findIndex((c) => c.id === customer.id);
    if (idx >= 0) {
      customers[idx] = customer;
    } else {
      const maxId = customers.reduce((acc, c) => Math.max(acc, c.id), 0);
      customers.push({ ...customer, id: maxId + 1 });
    }
    setStored(STORAGE_KEYS.CUSTOMERS, customers);
  }

  public static deleteCustomer(id: number, actor: string = 'admin'): void {
    const customers = this.getCustomers();
    const target = customers.find((c) => c.id === id);
    if (target) {
      this.addTrashItem({
        id: `trash_cust_${Date.now()}_${id}`,
        entityType: 'customer',
        entityId: String(id),
        title: `Customer: ${target.name}`,
        summary: `Phone: ${target.phone} | Address: ${target.address}`,
        payloadJson: JSON.stringify(target),
        deletedBy: actor,
        deletedByRole: 'admin',
        status: 'TRASHED',
        deletedAt: Date.now(),
        expireAt: Date.now() + 86400000,
        restoredAt: 0,
        restoredBy: '',
      });
      this.logAudit('DELETE_CUSTOMER', 'customer', String(id), target.name, actor, 'admin', `Deleted customer ${target.name}`);
    }
    setStored(STORAGE_KEYS.CUSTOMERS, customers.filter((c) => c.id !== id));
  }

  private static upsertCustomerFromBill(bill: BillEntity): void {
    const customers = this.getCustomers();
    const cleanName = bill.customerName.trim();
    const existing = customers.find((c) => c.name.trim().toLowerCase() === cleanName.toLowerCase());

    const phoneMatch = bill.customerDetails.match(/(\+?91[\s-]?)?[6-9]\d{9}/);
    const phone = phoneMatch ? phoneMatch[0] : '';

    if (existing) {
      existing.details = bill.customerDetails;
      if (phone && !existing.phone) existing.phone = phone;
      setStored(STORAGE_KEYS.CUSTOMERS, customers);
    } else {
      const maxId = customers.reduce((acc, c) => Math.max(acc, c.id), 0);
      customers.push({
        id: maxId + 1,
        name: cleanName,
        details: bill.customerDetails,
        phone,
        address: bill.customerDetails.split('\n').slice(1).join(', ') || '',
        locationUrl: '',
      });
      setStored(STORAGE_KEYS.CUSTOMERS, customers);
    }
  }

  public static getProducts(): ProductEntity[] {
    return getStored<ProductEntity[]>(STORAGE_KEYS.PRODUCTS, initialProducts);
  }

  public static saveProduct(prod: ProductEntity): void {
    const products = this.getProducts();
    const idx = products.findIndex((p) => p.id === prod.id);
    if (idx >= 0) {
      products[idx] = prod;
    } else {
      const maxId = products.reduce((acc, p) => Math.max(acc, p.id), 0);
      products.push({ ...prod, id: maxId + 1 });
    }
    setStored(STORAGE_KEYS.PRODUCTS, products);
  }

  public static deleteProduct(id: number): void {
    const products = this.getProducts();
    setStored(STORAGE_KEYS.PRODUCTS, products.filter((p) => p.id !== id));
  }

  public static deductProductStock(items: { description: string; qty: number }[]): void {
    const products = this.getProducts();
    let modified = false;

    items.forEach((item) => {
      const prod = products.find((p) => p.name.trim().toLowerCase() === item.description.trim().toLowerCase());
      if (prod) {
        prod.stockQty = Math.max(0, prod.stockQty - item.qty);
        modified = true;
      }
    });

    if (modified) {
      setStored(STORAGE_KEYS.PRODUCTS, products);
    }
  }

  public static getCombos(): ProductComboFile[] {
    return getStored<ProductComboFile[]>(STORAGE_KEYS.COMBO_FILES, initialCombos);
  }

  public static saveCombo(combo: ProductComboFile): void {
    const combos = this.getCombos();
    const idx = combos.findIndex((c) => c.comboFileId === combo.comboFileId);
    if (idx >= 0) {
      combos[idx] = { ...combo, updatedAt: Date.now() };
    } else {
      combos.unshift({ ...combo, createdAt: Date.now(), updatedAt: Date.now() });
    }
    setStored(STORAGE_KEYS.COMBO_FILES, combos);
  }

  public static deleteCombo(id: string): void {
    const combos = this.getCombos();
    setStored(STORAGE_KEYS.COMBO_FILES, combos.filter((c) => c.comboFileId !== id));
  }

  public static getEmployees(): EmployeeEntity[] {
    return getStored<EmployeeEntity[]>(STORAGE_KEYS.EMPLOYEES, initialEmployees);
  }

  public static saveEmployee(emp: EmployeeEntity): void {
    const employees = this.getEmployees();
    const idx = employees.findIndex((e) => e.employeeId === emp.employeeId);
    if (idx >= 0) {
      employees[idx] = { ...emp, updatedAt: Date.now() };
    } else {
      employees.push({ ...emp, createdAt: Date.now(), updatedAt: Date.now() });
    }
    setStored(STORAGE_KEYS.EMPLOYEES, employees);
  }

  public static deleteEmployee(id: string): void {
    const employees = this.getEmployees();
    setStored(STORAGE_KEYS.EMPLOYEES, employees.filter((e) => e.employeeId !== id));
  }

  public static getSalaryPayments(): SalaryPaymentEntity[] {
    return getStored<SalaryPaymentEntity[]>(STORAGE_KEYS.SALARY_PAYMENTS, []);
  }

  public static recordSalaryPayment(payment: SalaryPaymentEntity): void {
    const payments = this.getSalaryPayments();
    payments.unshift({ ...payment, createdAt: Date.now() });
    setStored(STORAGE_KEYS.SALARY_PAYMENTS, payments);
  }

  public static getHelpRequests(): CustomerHelpEntity[] {
    return getStored<CustomerHelpEntity[]>(STORAGE_KEYS.HELP, initialHelpRequests);
  }

  public static saveHelpRequest(req: CustomerHelpEntity): void {
    const reqs = this.getHelpRequests();
    const idx = reqs.findIndex((r) => r.id === req.id);
    if (idx >= 0) {
      reqs[idx] = { ...req, updatedAt: Date.now() };
    } else {
      const maxId = reqs.reduce((acc, r) => Math.max(acc, r.id), 0);
      reqs.unshift({ ...req, id: maxId + 1, createdAt: Date.now(), updatedAt: Date.now() });
    }
    setStored(STORAGE_KEYS.HELP, reqs);
  }

  public static deleteHelpRequest(id: number): void {
    const reqs = this.getHelpRequests();
    setStored(STORAGE_KEYS.HELP, reqs.filter((r) => r.id !== id));
  }

  public static getExpenses(): ExpenseEntity[] {
    return getStored<ExpenseEntity[]>(STORAGE_KEYS.EXPENSES, [
      {
        id: 'exp_1',
        title: 'Office Internet & Fiber Broadband',
        category: 'Utilities',
        amount: 1499,
        date: '2026-11-15',
        note: 'BSNL Fiber Monthly Bill',
        recordedBy: '@TotalSolutionAdmin',
        createdAt: Date.now() - 86400000 * 11,
        updatedAt: Date.now() - 86400000 * 11,
      },
      {
        id: 'exp_2',
        title: 'Van Fuel & Toll Expenses',
        category: 'Travel',
        amount: 2200,
        date: '2026-11-18',
        note: 'Service calls to Candolim, Baga & Calangute',
        recordedBy: '@ramesh_tech',
        createdAt: Date.now() - 86400000 * 8,
        updatedAt: Date.now() - 86400000 * 8,
      },
    ]);
  }

  public static saveExpense(exp: ExpenseEntity): void {
    const expenses = this.getExpenses();
    const idx = expenses.findIndex((e) => e.id === exp.id);
    if (idx >= 0) {
      expenses[idx] = { ...exp, updatedAt: Date.now() };
    } else {
      expenses.unshift({ ...exp, createdAt: Date.now(), updatedAt: Date.now() });
    }
    setStored(STORAGE_KEYS.EXPENSES, expenses);
  }

  public static deleteExpense(id: string): void {
    const expenses = this.getExpenses();
    setStored(STORAGE_KEYS.EXPENSES, expenses.filter((e) => e.id !== id));
  }

  public static getTrashItems(): TrashItemEntity[] {
    return getStored<TrashItemEntity[]>(STORAGE_KEYS.TRASH, []);
  }

  public static addTrashItem(item: TrashItemEntity): void {
    const trash = this.getTrashItems();
    trash.unshift(item);
    setStored(STORAGE_KEYS.TRASH, trash);
  }

  public static restoreTrashItem(id: string, actor: string = 'admin'): boolean {
    const trash = this.getTrashItems();
    const target = trash.find((t) => t.id === id);
    if (!target) return false;

    try {
      if (target.entityType === 'bill') {
        const bill = JSON.parse(target.payloadJson) as BillEntity;
        const bills = this.getBills();
        bills.unshift(bill);
        setStored(STORAGE_KEYS.BILLS, bills);
      } else if (target.entityType === 'customer') {
        const cust = JSON.parse(target.payloadJson) as CustomerEntity;
        const customers = this.getCustomers();
        customers.push(cust);
        setStored(STORAGE_KEYS.CUSTOMERS, customers);
      }
      setStored(STORAGE_KEYS.TRASH, trash.filter((t) => t.id !== id));
      this.logAudit('RESTORE_TRASH', target.entityType, target.entityId, target.title, actor, 'admin', `Restored item from trash: ${target.title}`);
      return true;
    } catch {
      return false;
    }
  }

  public static purgeExpiredTrash(): void {
    const trash = this.getTrashItems();
    const now = Date.now();
    const active = trash.filter((t) => t.expireAt > now);
    if (active.length !== trash.length) {
      setStored(STORAGE_KEYS.TRASH, active);
    }
  }

  public static getAuditLogs(): AuditLogEntity[] {
    return getStored<AuditLogEntity[]>(STORAGE_KEYS.AUDIT, [
      {
        id: 'audit_init',
        action: 'SYSTEM_STARTUP',
        entityType: 'system',
        entityId: 'tse',
        title: 'System Initialized',
        actorUsername: '@TotalSolutionAdmin',
        actorRole: 'admin',
        details: 'TSE Billing & Surveillance Management System online.',
        createdAt: Date.now() - 86400000 * 30,
      },
    ]);
  }

  public static logAudit(action: string, entityType: string, entityId: string, title: string, actorUsername: string, actorRole: string, details: string): void {
    const logs = this.getAuditLogs();
    logs.unshift({
      id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      action,
      entityType,
      entityId,
      title,
      actorUsername,
      actorRole,
      details,
      createdAt: Date.now(),
    });
    setStored(STORAGE_KEYS.AUDIT, logs.slice(0, 500)); // retain last 500
  }

  public static getUsers(): UserAccountEntity[] {
    return getStored<UserAccountEntity[]>(STORAGE_KEYS.USERS, initialUsers);
  }

  public static saveUser(user: UserAccountEntity): void {
    const users = this.getUsers();
    const idx = users.findIndex((u) => u.username.toLowerCase() === user.username.toLowerCase());
    if (idx >= 0) {
      users[idx] = user;
    } else {
      users.push(user);
    }
    setStored(STORAGE_KEYS.USERS, users);
  }

  public static deleteUser(username: string): void {
    const users = this.getUsers();
    setStored(STORAGE_KEYS.USERS, users.filter((u) => u.username.toLowerCase() !== username.toLowerCase()));
  }

  public static getCompanySettings(): CompanySettings {
    return getStored<CompanySettings>(STORAGE_KEYS.COMPANY, defaultCompanySettings);
  }

  public static saveCompanySettings(settings: CompanySettings): void {
    setStored(STORAGE_KEYS.COMPANY, settings);
  }

  public static getUpiSettings(): UpiSettings {
    return getStored<UpiSettings>(STORAGE_KEYS.UPI, defaultUpiSettings);
  }

  public static saveUpiSettings(settings: UpiSettings): void {
    setStored(STORAGE_KEYS.UPI, settings);
  }

  public static getVoiceSettings(): VoiceAndNoteSettings {
    return getStored<VoiceAndNoteSettings>(STORAGE_KEYS.VOICE, defaultVoiceSettings);
  }

  public static saveVoiceSettings(settings: VoiceAndNoteSettings): void {
    setStored(STORAGE_KEYS.VOICE, settings);
  }

  public static getNotifications(): CloudWorkspaceNotification[] {
    return getStored<CloudWorkspaceNotification[]>(STORAGE_KEYS.NOTIFICATIONS, [
      {
        id: 'notif_welcome',
        category: 'SYSTEM',
        title: 'Welcome to TOTAL SOLUTION',
        message: 'System running with dynamic UPI payment QR, Quotation approval workflow and product combo files.',
        sender: 'Admin',
        targetRole: 'all',
        timestamp: Date.now() - 3600000,
        read: false,
      },
    ]);
  }

  public static addNotification(notif: Omit<CloudWorkspaceNotification, 'id' | 'timestamp' | 'read'>): void {
    const notifs = this.getNotifications();
    notifs.unshift({
      ...notif,
      id: `notif_${Date.now()}`,
      timestamp: Date.now(),
      read: false,
    });
    setStored(STORAGE_KEYS.NOTIFICATIONS, notifs);
  }

  public static markNotificationsRead(): void {
    const notifs = this.getNotifications();
    notifs.forEach((n) => (n.read = true));
    setStored(STORAGE_KEYS.NOTIFICATIONS, notifs);
  }

  public static getNextInvoiceNo(documentType: 'BILL' | 'QUOTATION' = 'BILL'): string {
    const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
    let seq = parseInt(localStorage.getItem(STORAGE_KEYS.LAST_INVOICE_SEQ) || '100', 10) + 1;
    localStorage.setItem(STORAGE_KEYS.LAST_INVOICE_SEQ, String(seq));
    const prefix = documentType === 'QUOTATION' ? 'TSE-QUO' : 'TSE-INV';
    return `${prefix}-${dateStr}-${String(seq).padStart(3, '0')}`;
  }

  public static exportFullBackupJson(): string {
    const backup = {
      version: '2.4.0',
      exportedAt: Date.now(),
      company: this.getCompanySettings(),
      upi: this.getUpiSettings(),
      voice: this.getVoiceSettings(),
      users: this.getUsers(),
      bills: this.getBills(),
      customers: this.getCustomers(),
      products: this.getProducts(),
      combos: this.getCombos(),
      employees: this.getEmployees(),
      salaryPayments: this.getSalaryPayments(),
      helpRequests: this.getHelpRequests(),
      expenses: this.getExpenses(),
      trash: this.getTrashItems(),
      audit: this.getAuditLogs(),
    };
    return JSON.stringify(backup, null, 2);
  }

  public static importFullBackupJson(rawJson: string): boolean {
    try {
      const data = JSON.parse(rawJson);
      if (data.company) setStored(STORAGE_KEYS.COMPANY, data.company);
      if (data.upi) setStored(STORAGE_KEYS.UPI, data.upi);
      if (data.voice) setStored(STORAGE_KEYS.VOICE, data.voice);
      if (Array.isArray(data.users)) setStored(STORAGE_KEYS.USERS, data.users);
      if (Array.isArray(data.bills)) setStored(STORAGE_KEYS.BILLS, data.bills);
      if (Array.isArray(data.customers)) setStored(STORAGE_KEYS.CUSTOMERS, data.customers);
      if (Array.isArray(data.products)) setStored(STORAGE_KEYS.PRODUCTS, data.products);
      if (Array.isArray(data.combos)) setStored(STORAGE_KEYS.COMBO_FILES, data.combos);
      if (Array.isArray(data.employees)) setStored(STORAGE_KEYS.EMPLOYEES, data.employees);
      if (Array.isArray(data.salaryPayments)) setStored(STORAGE_KEYS.SALARY_PAYMENTS, data.salaryPayments);
      if (Array.isArray(data.helpRequests)) setStored(STORAGE_KEYS.HELP, data.helpRequests);
      if (Array.isArray(data.expenses)) setStored(STORAGE_KEYS.EXPENSES, data.expenses);
      return true;
    } catch (e) {
      console.error('Failed to parse backup JSON:', e);
      return false;
    }
  }
}
