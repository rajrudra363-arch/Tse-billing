import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  UserSession,
  BillEntity,
  CustomerEntity,
  ProductEntity,
  ProductComboFile,
  CompanySettings,
  UpiSettings,
  VoiceAndNoteSettings,
  CustomerHelpEntity,
  ExpenseEntity,
  TrashItemEntity,
  AuditLogEntity,
  EmployeeEntity,
  CloudWorkspaceNotification,
  BillDraft,
} from './types';
import { AppDb } from './storage/db';
import { sound } from './utils/soundEngine';
import {
  IDLE_ACTIVITY_EVENTS,
  IDLE_TIMEOUT_MS,
  LAST_ACTIVITY_STORAGE_KEY,
  createIdleSessionTracker,
  isSessionIdleExpired,
} from './utils/idleTimer';
import { TopBar } from './components/TopBar';
import { NotificationCenterDialog } from './components/NotificationCenterDialog';
import { AuthScreen } from './screens/AuthScreen';
import { BillScreen } from './screens/BillScreen';
import { SavedBillsScreen } from './screens/SavedBillsScreen';
import { CustomersScreen } from './screens/CustomersScreen';
import { CustomerHelpScreen } from './screens/CustomerHelpScreen';
import { ProductsScreen } from './screens/ProductsScreen';
import { EmployeeManagementScreen } from './screens/EmployeeManagementScreen';
import { SuperAdminDashboardScreen } from './screens/SuperAdminDashboardScreen';
import { AdminPanelScreen } from './screens/AdminPanelScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import {
  Receipt,
  ReceiptText,
  Users,
  LifeBuoy,
  Package,
  UserCheck,
  BarChart3,
  ShieldCheck,
  Settings,
} from 'lucide-react';

type NavTab =
  | 'BILL'
  | 'SAVED'
  | 'CUSTOMERS'
  | 'HELP'
  | 'PRODUCTS'
  | 'EMPLOYEES'
  | 'ANALYTICS'
  | 'ADMIN'
  | 'SETTINGS';

export const App: React.FC = () => {
  // Session State (invalidate immediately if persisted session has been idle >= 30 minutes)
  const [logoutNotice, setLogoutNotice] = useState<string | null>(() => {
    const saved = localStorage.getItem('tse_active_session');
    if (!saved) return null;
    const rawLast = localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY);
    const parsedLast = rawLast ? Number(rawLast) : NaN;
    if (isSessionIdleExpired(parsedLast, Date.now(), IDLE_TIMEOUT_MS)) {
      localStorage.removeItem('tse_active_session');
      localStorage.removeItem(LAST_ACTIVITY_STORAGE_KEY);
      return 'Signed out automatically after 30 minutes of inactivity to protect shared workspace devices.';
    }
    return null;
  });

  const [session, setSession] = useState<UserSession | null>(() => {
    const saved = localStorage.getItem('tse_active_session');
    if (!saved) return null;
    const rawLast = localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY);
    const parsedLast = rawLast ? Number(rawLast) : NaN;
    if (isSessionIdleExpired(parsedLast, Date.now(), IDLE_TIMEOUT_MS)) {
      localStorage.removeItem('tse_active_session');
      localStorage.removeItem(LAST_ACTIVITY_STORAGE_KEY);
      return null;
    }
    return JSON.parse(saved);
  });

  // Active Tab
  const [activeTab, setActiveTab] = useState<NavTab>('BILL');

  // Database Data States
  const [company, setCompany] = useState<CompanySettings>(AppDb.getCompanySettings());
  const [upi, setUpi] = useState<UpiSettings>(AppDb.getUpiSettings());
  const [voice, setVoice] = useState<VoiceAndNoteSettings>(AppDb.getVoiceSettings());
  const [bills, setBills] = useState<BillEntity[]>(AppDb.getBills());
  const [customers, setCustomers] = useState<CustomerEntity[]>(AppDb.getCustomers());
  const [products, setProducts] = useState<ProductEntity[]>(AppDb.getProducts());
  const [combos, setCombos] = useState<ProductComboFile[]>(AppDb.getCombos());
  const [employees, setEmployees] = useState<EmployeeEntity[]>(AppDb.getEmployees());
  const [helpRequests, setHelpRequests] = useState<CustomerHelpEntity[]>(AppDb.getHelpRequests());
  const [expenses, setExpenses] = useState<ExpenseEntity[]>(AppDb.getExpenses());
  const [trashItems, setTrashItems] = useState<TrashItemEntity[]>(AppDb.getTrashItems());
  const [auditLogs, setAuditLogs] = useState<AuditLogEntity[]>(AppDb.getAuditLogs());
  const [notifications, setNotifications] = useState<CloudWorkspaceNotification[]>(AppDb.getNotifications());
  const [users, setUsers] = useState(AppDb.getUsers());

  // Sound Engine
  const [soundEnabled, setSoundEnabled] = useState<boolean>(sound.isEnabled());

  // Notification Modal
  const [notificationModalOpen, setNotificationModalOpen] = useState(false);

  // Edit / Prefill Draft in BillScreen
  const [prefillDraft, setPrefillDraft] = useState<Partial<BillDraft> | null>(null);

  // Initial Splash Screen (3 seconds or click to dismiss)
  const [splashVisible, setSplashVisible] = useState<boolean>(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setSplashVisible(false);
    }, 1800);
    return () => clearTimeout(timer);
  }, []);

  // Sync data across tabs
  const refreshAllData = () => {
    setCompany(AppDb.getCompanySettings());
    setUpi(AppDb.getUpiSettings());
    setVoice(AppDb.getVoiceSettings());
    setBills(AppDb.getBills());
    setCustomers(AppDb.getCustomers());
    setProducts(AppDb.getProducts());
    setCombos(AppDb.getCombos());
    setEmployees(AppDb.getEmployees());
    setHelpRequests(AppDb.getHelpRequests());
    setExpenses(AppDb.getExpenses());
    setTrashItems(AppDb.getTrashItems());
    setAuditLogs(AppDb.getAuditLogs());
    setNotifications(AppDb.getNotifications());
    setUsers(AppDb.getUsers());
  };

  const handleLoginSuccess = (userSession: UserSession) => {
    setLogoutNotice(null);
    setSession(userSession);
    localStorage.setItem('tse_active_session', JSON.stringify(userSession));
    localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(Date.now()));
    if (userSession.isSuperAdmin) {
      setActiveTab('ANALYTICS');
    } else {
      setActiveTab('BILL');
    }
  };

  const handleLogout = useCallback(() => {
    sound.playClick();
    setNotificationModalOpen(false);
    setSession(null);
    localStorage.removeItem('tse_active_session');
    localStorage.removeItem(LAST_ACTIVITY_STORAGE_KEY);
  }, []);

  const handleLogoutRef = useRef(handleLogout);
  handleLogoutRef.current = handleLogout;

  // Idle Session Timer: automatically call handleLogout after 30 minutes of inactivity
  useEffect(() => {
    if (!session) return;

    const readStoredActivity = (): number | undefined => {
      const raw = localStorage.getItem(LAST_ACTIVITY_STORAGE_KEY);
      if (!raw) return undefined;
      const parsed = Number(raw);
      return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
    };

    const tracker = createIdleSessionTracker({
      timeoutMs: IDLE_TIMEOUT_MS,
      onIdle: () => {
        setLogoutNotice(
          'Signed out automatically after 30 minutes of inactivity to protect shared workspace devices.'
        );
        handleLogoutRef.current();
      },
    });

    const initialTs = readStoredActivity() ?? Date.now();
    localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(initialTs));
    tracker.start(initialTs);
    if (tracker.isStopped()) {
      return () => {
        tracker.stop();
      };
    }

    let lastThrottledRecord = 0;
    const handleActivity = (event: Event) => {
      const now = Date.now();
      const isHighFrequency =
        event.type === 'mousemove' || event.type === 'scroll' || event.type === 'wheel';
      if (isHighFrequency && now - lastThrottledRecord < 1000) {
        return;
      }
      lastThrottledRecord = now;
      localStorage.setItem(LAST_ACTIVITY_STORAGE_KEY, String(now));
      tracker.recordActivity();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        const stored = readStoredActivity();
        if (stored !== undefined && isSessionIdleExpired(stored, Date.now(), IDLE_TIMEOUT_MS)) {
          setLogoutNotice(
            'Signed out automatically after 30 minutes of inactivity to protect shared workspace devices.'
          );
          handleLogoutRef.current();
          return;
        }
        tracker.checkExpired();
      }
    };

    const handleStorageSync = (event: StorageEvent) => {
      if (event.key === 'tse_active_session' && event.newValue === null) {
        handleLogoutRef.current();
      } else if (event.key === LAST_ACTIVITY_STORAGE_KEY && event.newValue) {
        const synced = Number(event.newValue);
        if (Number.isFinite(synced) && synced > tracker.getLastActivityAt()) {
          tracker.recordActivity();
        }
      }
    };

    for (const eventName of IDLE_ACTIVITY_EVENTS) {
      window.addEventListener(eventName, handleActivity, { passive: true });
    }
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('storage', handleStorageSync);

    return () => {
      tracker.stop();
      for (const eventName of IDLE_ACTIVITY_EVENTS) {
        window.removeEventListener(eventName, handleActivity);
      }
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('storage', handleStorageSync);
    };
  }, [session]);

  const toggleSound = () => {
    const next = sound.toggle();
    setSoundEnabled(next);
  };

  const handleSendNotification = (title: string, message: string, targetRole: string) => {
    AppDb.addNotification({
      category: 'GENERAL',
      title,
      message,
      sender: session?.displayName || 'User',
      targetRole,
    });
    sound.playSuccess();
    setNotifications(AppDb.getNotifications());
  };

  const handleOpenBillInDraft = (bill: BillEntity) => {
    let items = [];
    try {
      items = JSON.parse(bill.itemsJson);
    } catch {
      items = [];
    }
    setPrefillDraft({
      documentType: bill.documentType,
      invoiceNo: bill.invoiceNo,
      date: bill.date,
      customerName: bill.customerName,
      customerDetails: bill.customerDetails,
      items,
      subtotal: bill.subtotal,
      discount: bill.discount,
      extraChargeType: bill.extraChargeType,
      extraChargeAmount: bill.extraChargeAmount,
      secondaryChargeType: bill.secondaryChargeType,
      secondaryChargeAmount: bill.secondaryChargeAmount,
      grandTotal: bill.grandTotal,
      paidAmount: bill.paidAmount,
      dueAmount: bill.dueAmount,
      paymentMode: bill.paymentMode,
      signatureBase64: bill.signatureBase64,
      staffRating: bill.staffRating,
      staffReviewNote: bill.staffReviewNote,
      quotationNote: bill.quotationNote,
      isHelpBill: bill.isHelpBill,
      helpRequestId: bill.helpRequestId,
    });
    setActiveTab('BILL');
  };

  const handleConvertTicketToBill = (req: CustomerHelpEntity) => {
    setPrefillDraft({
      documentType: req.category === 'QUOTATION' ? 'QUOTATION' : 'BILL',
      invoiceNo: AppDb.getNextInvoiceNo(req.category === 'QUOTATION' ? 'QUOTATION' : 'BILL'),
      date: new Date().toISOString().slice(0, 10),
      customerName: req.customerName,
      customerDetails: `Phone: ${req.phone}\n${req.address}`,
      customerLocationUrl: req.locationUrl,
      items: [
        {
          localId: Date.now(),
          folder: 'Service',
          description: `Service Ticket #${req.id}: ${req.helpDescription}`,
          qty: 1,
          unit: 'Job',
          rate: req.category === 'REGULAR_SERVICE' ? 650 : 1200,
          amount: req.category === 'REGULAR_SERVICE' ? 650 : 1200,
        },
      ],
      isHelpBill: true,
      helpRequestId: req.id,
    });
    setActiveTab('BILL');
  };

  // Nav Items depending on role
  const navItems = React.useMemo(() => {
    if (!session) return [];
    if (session.isSuperAdmin) {
      return [
        { id: 'ANALYTICS', label: 'Analytics', icon: BarChart3 },
        { id: 'SAVED', label: 'Saved Bills', icon: ReceiptText },
        { id: 'CUSTOMERS', label: 'Customers', icon: Users },
        { id: 'HELP', label: 'Help Desk', icon: LifeBuoy },
        { id: 'PRODUCTS', label: 'Products', icon: Package },
        { id: 'EMPLOYEES', label: 'Employees', icon: UserCheck },
        { id: 'SETTINGS', label: 'Settings', icon: Settings },
      ] as const;
    }
    if (session.isAdmin) {
      return [
        { id: 'BILL', label: 'New Bill', icon: Receipt },
        { id: 'SAVED', label: 'Saved Bills', icon: ReceiptText },
        { id: 'CUSTOMERS', label: 'Customers', icon: Users },
        { id: 'HELP', label: 'Help Desk', icon: LifeBuoy },
        { id: 'PRODUCTS', label: 'Products', icon: Package },
        { id: 'EMPLOYEES', label: 'Employees', icon: UserCheck },
        { id: 'ANALYTICS', label: 'Analytics', icon: BarChart3 },
        { id: 'ADMIN', label: 'Admin', icon: ShieldCheck },
        { id: 'SETTINGS', label: 'Settings', icon: Settings },
      ] as const;
    }
    // Technician
    return [
      { id: 'BILL', label: 'New Bill', icon: Receipt },
      { id: 'SAVED', label: 'Saved Bills', icon: ReceiptText },
      { id: 'CUSTOMERS', label: 'Customers', icon: Users },
      { id: 'HELP', label: 'Help Desk', icon: LifeBuoy },
      { id: 'PRODUCTS', label: 'Products', icon: Package },
      { id: 'SETTINGS', label: 'Settings', icon: Settings },
    ] as const;
  }, [session]);

  const unreadCount = notifications.filter((n) => !n.read).length;

  if (splashVisible) {
    return (
      <div
        onClick={() => setSplashVisible(false)}
        className="fixed inset-0 z-50 bg-[#0F172A] flex flex-col items-center justify-center p-6 text-white cursor-pointer select-none"
      >
        <div className="flex flex-col items-center text-center space-y-4 animate-in fade-in zoom-in duration-500">
          <img
            src="/img_tse_logo.jpg"
            alt="TSE Logo"
            className="w-28 h-28 object-contain rounded-3xl bg-white p-2 shadow-2xl border-2 border-blue-400"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/ic_launcher_cctv_fg.png';
            }}
          />
          <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white uppercase">
            {company.companyName}
          </h1>
          <p className="text-sm font-semibold text-cyan-300 max-w-sm">
            CCTV Security • Dynamic UPI QR • Payroll & Help Token Workflow
          </p>
          <div className="pt-8">
            <span className="text-xs text-slate-400 animate-pulse">Click anywhere or waiting to start...</span>
          </div>
        </div>
      </div>
    );
  }

  if (!session) {
    return (
      <AuthScreen
        company={company}
        sessionNotice={logoutNotice}
        onDismissSessionNotice={() => setLogoutNotice(null)}
        onLoginSuccess={handleLoginSuccess}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#F0F4FF] text-slate-900 flex flex-col">
      {/* Top Bar */}
      <TopBar
        company={company}
        session={session}
        soundEnabled={soundEnabled}
        unreadNotifications={unreadCount}
        onToggleSound={toggleSound}
        onOpenNotifications={() => {
          AppDb.markNotificationsRead();
          setNotifications(AppDb.getNotifications());
          setNotificationModalOpen(true);
        }}
        onLogout={() => {
          setLogoutNotice(null);
          handleLogout();
        }}
      />

      {/* Navigation Sub-Header Bar */}
      <nav className="no-print bg-white border-b border-slate-200 sticky top-14 z-30 shadow-xs">
        <div className="max-w-7xl mx-auto px-3 sm:px-6 flex items-center gap-1 overflow-x-auto py-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isSelected = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => {
                  sound.playClick();
                  setActiveTab(item.id as NavTab);
                }}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold whitespace-nowrap transition ${
                  isSelected
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`}
              >
                <Icon className={`w-4 h-4 ${isSelected ? 'text-white' : 'text-blue-600'}`} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Main Screen Views */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 sm:p-6">
        {activeTab === 'BILL' && (
          <BillScreen
            currentUser={session}
            company={company}
            upi={upi}
            products={products}
            customers={customers}
            combos={combos}
            prefillDraft={prefillDraft}
            onBillGenerated={() => {
              refreshAllData();
            }}
          />
        )}

        {activeTab === 'SAVED' && (
          <SavedBillsScreen
            bills={bills}
            company={company}
            upi={upi}
            isAdmin={session.isAdmin}
            onEditBill={handleOpenBillInDraft}
            onBillsUpdated={refreshAllData}
          />
        )}

        {activeTab === 'CUSTOMERS' && (
          <CustomersScreen
            customers={customers}
            bills={bills}
            user={session}
            onCustomersUpdated={refreshAllData}
            onOpenBillInDraft={handleOpenBillInDraft}
          />
        )}

        {activeTab === 'HELP' && (
          <CustomerHelpScreen
            helpRequests={helpRequests}
            customers={customers}
            accounts={users}
            company={company}
            user={session}
            onHelpRequestsUpdated={refreshAllData}
            onConvertTicketToBill={handleConvertTicketToBill}
          />
        )}

        {activeTab === 'PRODUCTS' && (
          <ProductsScreen
            products={products}
            combos={combos}
            user={session}
            onProductsUpdated={refreshAllData}
          />
        )}

        {activeTab === 'EMPLOYEES' && (
          <EmployeeManagementScreen
            employees={employees}
            user={session}
            onEmployeesUpdated={refreshAllData}
          />
        )}

        {activeTab === 'ANALYTICS' && (
          <SuperAdminDashboardScreen
            bills={bills}
            customers={customers}
            helpRequests={helpRequests}
            products={products}
            expenses={expenses}
            accounts={users}
          />
        )}

        {activeTab === 'ADMIN' && session.isAdmin && (
          <AdminPanelScreen
            upi={upi}
            voice={voice}
            accounts={users}
            expenses={expenses}
            trashItems={trashItems}
            auditLogs={auditLogs}
            onDataUpdated={refreshAllData}
          />
        )}

        {activeTab === 'SETTINGS' && (
          <SettingsScreen
            settings={company}
            user={session}
            onSettingsUpdated={refreshAllData}
          />
        )}
      </main>

      {/* Team Notifications Modal */}
      {notificationModalOpen && (
        <NotificationCenterDialog
          notifications={notifications}
          currentUser={session}
          onSendNotification={handleSendNotification}
          onDismiss={() => setNotificationModalOpen(false)}
        />
      )}
    </div>
  );
};
export default App;
