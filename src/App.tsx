import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  BadgePercent,
  Banknote,
  BarChart3,
  Bot,
  Boxes,
  Check,
  CheckCircle2,
  ClipboardList,
  Code2,
  Copy,
  Download,
  Edit3,
  Eye,
  Globe,
  History,
  LayoutDashboard,
  Lock,
  LogIn,
  LogOut,
  Menu,
  Minus,
  Moon,
  Package,
  PanelLeftClose,
  Plus,
  Printer,
  QrCode,
  ReceiptText,
  RefreshCw,
  ScanLine,
  Search,
  Settings,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  Sun,
  Tablet,
  Trash2,
  UserCog,
  X,
} from 'lucide-react';
import {
  AuditLog,
  CartItem,
  Category,
  Expense,
  InventoryMovement,
  ModuleId,
  OrderStatus,
  OrderWithItems,
  OutletSettings,
  PermissionKey,
  PrinterRole,
  Product,
  Promotion,
  RestaurantTable,
  SavedPrinterDevice,
  StaffProfile,
  StaffRole,
  StaffShift,
  WebsiteCmsData,
} from './types/pos';
import {
  INITIAL_AUDIT_LOGS,
  INITIAL_CATEGORIES,
  INITIAL_EXPENSES,
  INITIAL_INVENTORY_MOVEMENTS,
  INITIAL_ORDERS,
  INITIAL_OUTLET_SETTINGS,
  INITIAL_PRODUCTS,
  INITIAL_PROMOTIONS,
  INITIAL_SHIFTS,
  INITIAL_STAFF,
  INITIAL_TABLES,
  INITIAL_WEBSITE_CMS,
} from './data/sandboxSeed';
import {
  BackendMode,
  CANDIDATE_PROJECTS,
  downloadCsvFile,
  formatEscPosReceiptText,
  formatRupiah,
  generateDeterministicQrMatrix,
  getVerifiedSupabaseClient,
  loadVerificationConfig,
  saveVerificationConfig,
  SupabaseVerificationConfig,
} from './lib/supabaseBridge';
import { KotlinProjectModal } from './components/KotlinProjectModal';

interface NavGroup {
  label: string;
  items: Array<{
    id: ModuleId;
    label: string;
    permission: PermissionKey;
    icon: React.ComponentType<{ className?: string }>;
  }>;
}

const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Utama',
    items: [{ id: 'dashboard', label: 'Dashboard', permission: 'dashboard', icon: LayoutDashboard }],
  },
  {
    label: 'AI',
    items: [{ id: 'ai', label: 'AI Assistant', permission: 'reports', icon: Bot }],
  },
  {
    label: 'Transaksi',
    items: [
      { id: 'kasir', label: 'Kasir', permission: 'orders', icon: ScanLine },
      { id: 'pesanan', label: 'Pesanan', permission: 'orders', icon: ClipboardList },
      { id: 'history', label: 'History', permission: 'orders', icon: History },
      { id: 'shift-kasir', label: 'Opening & Closing', permission: 'orders', icon: Banknote },
      { id: 'audit-shift', label: 'Audit Shift', permission: 'orders', icon: ShieldCheck },
      { id: 'activity-log', label: 'Log Aktivitas', permission: 'settings', icon: History },
    ],
  },
  {
    label: 'Produk & Outlet',
    items: [
      { id: 'produk', label: 'Produk & Stok', permission: 'products', icon: Package },
      { id: 'meja', label: 'Meja & QR Menu', permission: 'tables', icon: QrCode },
      { id: 'inventory', label: 'Inventory', permission: 'inventory', icon: Boxes },
    ],
  },
  {
    label: 'Pemasaran',
    items: [{ id: 'promosi', label: 'Promosi & Diskon', permission: 'promotions', icon: BadgePercent }],
  },
  {
    label: 'Manajemen',
    items: [
      { id: 'keuangan', label: 'Pengeluaran & Laba Rugi', permission: 'reports', icon: ReceiptText },
      { id: 'laporan', label: 'Laporan Penjualan', permission: 'reports', icon: BarChart3 },
      { id: 'pegawai', label: 'Pegawai & Shift', permission: 'settings', icon: UserCog },
      { id: 'pengaturan', label: 'Pengaturan Outlet', permission: 'settings', icon: Settings },
      { id: 'printer', label: 'Printer', permission: 'settings', icon: Printer },
    ],
  },
];

const STATUS_LABELS: Record<OrderStatus, string> = {
  new: 'Baru',
  preparing: 'Diproses',
  ready: 'Siap',
  completed: 'Selesai',
  cancelled: 'Dibatalkan',
};

const ZOOM_PRESETS = [40, 50, 60, 70, 80, 90, 100, 110, 120] as const;

export default function App() {
  // Theme, Scale & Device Preview State
  const [darkMode, setDarkMode] = useState<boolean>(() => {
    return localStorage.getItem('km_dark_mode') === '1';
  });
  const [screenZoom, setScreenZoom] = useState<number>(() => {
    const saved = Number(localStorage.getItem('cafe-flow-screen-zoom') || 100);
    return Number.isFinite(saved) && saved >= 40 && saved <= 120 ? saved : 100;
  });
  const [viewportMode, setViewportMode] = useState<'full' | 'phone' | 'tablet'>('full');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [kotlinModalOpen, setKotlinModalOpen] = useState(false);
  const [loginScreenOpen, setLoginScreenOpen] = useState(false);

  // Navigation Module
  const [activeModule, setActiveModule] = useState<ModuleId>('dashboard');

  // Supabase Verification & Data Mode State
  // Default is strictly INACTIVE_PENDING_VERIFICATION as required by section 10!
  const [supabaseConfig, setSupabaseConfig] = useState<SupabaseVerificationConfig>(() =>
    loadVerificationConfig()
  );
  const [backendMode, setBackendMode] = useState<BackendMode>(() => {
    const cfg = loadVerificationConfig();
    return cfg.verifiedByUser && cfg.supabaseUrl && cfg.publishableKey
      ? 'LIVE_SUPABASE'
      : 'INACTIVE_PENDING_VERIFICATION';
  });
  const [isSyncingBackend, setIsSyncingBackend] = useState(false);
  const [authEmail, setAuthEmail] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authLoading, setAuthLoading] = useState(false);

  // Feedback Banner State
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(
    null
  );

  // Operational State (Populated either from Verified Live Supabase OR Explicitly Toggled Sandbox UI Mode)
  const [staffList, setStaffList] = useState<StaffProfile[]>(INITIAL_STAFF);
  const [currentStaff, setCurrentStaff] = useState<StaffProfile>(INITIAL_STAFF[0]);
  const [categories, setCategories] = useState<Category[]>(INITIAL_CATEGORIES);
  const [products, setProducts] = useState<Product[]>(INITIAL_PRODUCTS);
  const [tables, setTables] = useState<RestaurantTable[]>(INITIAL_TABLES);
  const [promotions, setPromotions] = useState<Promotion[]>(INITIAL_PROMOTIONS);
  const [orders, setOrders] = useState<OrderWithItems[]>(INITIAL_ORDERS);
  const [shifts, setShifts] = useState<StaffShift[]>(INITIAL_SHIFTS);
  const [expenses, setExpenses] = useState<Expense[]>(INITIAL_EXPENSES);
  const [inventoryMovements, setInventoryMovements] = useState<InventoryMovement[]>(
    INITIAL_INVENTORY_MOVEMENTS
  );
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>(INITIAL_AUDIT_LOGS);
  const [outletSettings, setOutletSettings] = useState<OutletSettings>(INITIAL_OUTLET_SETTINGS);
  const [websiteCms, setWebsiteCms] = useState<WebsiteCmsData>(INITIAL_WEBSITE_CMS);

  // Kasir Cart & Checkout State
  const [kasirSearch, setKasirSearch] = useState('');
  const [kasirCategory, setKasirCategory] = useState<string>('ALL');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [selectedTableId, setSelectedTableId] = useState<string>('tbl-1');
  const [customerName, setCustomerName] = useState('');
  const [orderNotes, setOrderNotes] = useState('');
  const [selectedPromoId, setSelectedPromoId] = useState<string>('');
  const [paymentMethod, setPaymentMethod] = useState<'cash' | 'qris' | 'transfer' | 'ewallet'>('cash');
  const [cashReceivedInput, setCashReceivedInput] = useState<string>('');
  const [confirmCheckoutModalOpen, setConfirmCheckoutModalOpen] = useState(false);
  const [isCheckoutProcessing, setIsCheckoutProcessing] = useState(false);
  const checkoutLockRef = useRef(false);
  const [receiptModalOrder, setReceiptModalOrder] = useState<OrderWithItems | null>(null);

  // Item Customization Modal State
  const [customizingProduct, setCustomizingProduct] = useState<Product | null>(null);
  const [customSugar, setCustomSugar] = useState('Normal Sugar');
  const [customIce, setCustomIce] = useState('Normal Ice');
  const [customCup, setCustomCup] = useState('Regular');
  const [customNote, setCustomNote] = useState('');
  const [customAddons, setCustomAddons] = useState<Array<{ name: string; price: number }>>([]);

  // Pesanan & History Filters
  const [orderStatusFilter, setOrderStatusFilter] = useState<'ALL' | OrderStatus>('ALL');
  const [orderSourceFilter, setOrderSourceFilter] = useState<'ALL' | 'pos' | 'qr'>('ALL');
  const [orderSearch, setOrderSearch] = useState('');
  const [historyDateFrom, setHistoryDateFrom] = useState('2026-10-01');
  const [historyDateTo, setHistoryDateTo] = useState('2026-10-12');

  // Produk Modal & Delete Confirm State
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [deletingProduct, setDeletingProduct] = useState<Product | null>(null);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [productStockFilter, setProductStockFilter] = useState<'ALL' | 'LOW' | 'OUT'>('ALL');

  // Meja & QR Modal State
  const [newTableName, setNewTableName] = useState('');
  const [newTableCode, setNewTableCode] = useState('');
  const [selectedQrTable, setSelectedQrTable] = useState<RestaurantTable | null>(INITIAL_TABLES[0]);

  // Shift Kasir State
  const [openingCashInput, setOpeningCashInput] = useState('300000');
  const [closingCashInput, setClosingCashInput] = useState('');
  const [closingShiftNotes, setClosingShiftNotes] = useState('');

  // Inventory Form State
  const [invProductId, setInvProductId] = useState(INITIAL_PRODUCTS[0]?.id || '');
  const [invType, setInvType] = useState<'in' | 'out' | 'adjustment'>('in');
  const [invQty, setInvQty] = useState('10');
  const [invReason, setInvReason] = useState('');

  // Promosi Form State
  const [promoName, setPromoName] = useState('');
  const [promoType, setPromoType] = useState<'percentage' | 'fixed'>('percentage');
  const [promoValue, setPromoValue] = useState('10');
  const [promoMinPurchase, setPromoMinPurchase] = useState('50000');
  const [promoMinQuantity, setPromoMinQuantity] = useState('1');
  const [promoScope, setPromoScope] = useState<'order' | 'product'>('order');
  const [promoProductId, setPromoProductId] = useState('');
  const [promoKind, setPromoKind] = useState<'promotion' | 'voucher' | 'bundle' | 'campaign'>('promotion');
  const [promoAllowCustomer, setPromoAllowCustomer] = useState(true);
  const [promoAllowCashier, setPromoAllowCashier] = useState(true);
  const [promoModalOpen, setPromoModalOpen] = useState(false);
  const [promoTab, setPromoTab] = useState<'all' | 'active' | 'voucher' | 'bundle' | 'campaign'>('all');

  // Keuangan & Laba Rugi State
  const [financeTab, setFinanceTab] = useState<'expenses' | 'pnl'>('expenses');
  const [expenseAmount, setExpenseAmount] = useState('');
  const [expenseCategory, setExpenseCategory] = useState('Bahan baku');
  const [expenseDate, setExpenseDate] = useState('2026-10-11');
  const [expenseMethod, setExpenseMethod] = useState('cash');
  const [expenseDesc, setExpenseDesc] = useState('');
  const [financeFrom, setFinanceFrom] = useState('2026-10-01');
  const [financeTo, setFinanceTo] = useState('2026-10-12');

  // Dual Bluetooth Printer State
  const [bluetoothPermGranted, setBluetoothPermGranted] = useState(true);
  const [isScanningPrinters, setIsScanningPrinters] = useState(false);
  const [discoveredPrinters, setDiscoveredPrinters] = useState<
    Array<{ name: string; address: string; rssi: string }>
  >([
    { name: 'RPP02N-Thermal-Kasir', address: 'DC:0D:30:A1:44:12', rssi: '-54 dBm' },
    { name: 'Epson-TM-P20-Dapur', address: '00:01:90:C4:88:7B', rssi: '-61 dBm' },
    { name: 'VSC-MP-58C-Bar', address: '86:67:7A:19:03:E5', rssi: '-68 dBm' },
  ]);
  const [printers, setPrinters] = useState<Record<PrinterRole, SavedPrinterDevice | null>>({
    cashier: {
      role: 'cashier',
      name: 'RPP02N-Thermal-Kasir',
      address: 'DC:0D:30:A1:44:12',
      paperWidth: '58mm',
      autoPrint: true,
      connected: true,
    },
    kitchen: {
      role: 'kitchen',
      name: 'Epson-TM-P20-Dapur',
      address: '00:01:90:C4:88:7B',
      paperWidth: '58mm',
      autoPrint: true,
      connected: true,
    },
  });
  const [printedReceiptIds, setPrintedReceiptIds] = useState<string[]>([]);
  const [printedKitchenIds, setPrintedKitchenIds] = useState<string[]>([]);
  const [thermalPreviewText, setThermalPreviewText] = useState<string>(() =>
    formatEscPosReceiptText(INITIAL_ORDERS[0], 'cashier')
  );

  // Sync dark mode class on root
  useEffect(() => {
    localStorage.setItem('km_dark_mode', darkMode ? '1' : '0');
    if (darkMode) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [darkMode]);

  const handleSetZoom = (nextZoom: number) => {
    const clamped = Math.min(120, Math.max(40, nextZoom));
    setScreenZoom(clamped);
    localStorage.setItem('cafe-flow-screen-zoom', String(clamped));
  };

  const showNotice = (type: 'success' | 'error' | 'info', message: string) => {
    setFeedback({ type, message });
    setTimeout(() => {
      setFeedback((prev) => (prev?.message === message ? null : prev));
    }, 4500);
  };

  const canAccessModule = (perm: PermissionKey): boolean => {
    if (!currentStaff.is_active) return false;
    return (
      currentStaff.role === 'super_admin' ||
      currentStaff.role === 'owner' ||
      currentStaff.permissions[perm] === true
    );
  };

  const appendAuditLog = (action: string, entityType: string, entityId: string, detailsObj: unknown) => {
    const nextLog: AuditLog = {
      id: `log-${Date.now()}`,
      actor_user_id: currentStaff.user_id,
      actor_name: currentStaff.full_name,
      action,
      entity_type: entityType,
      entity_id: entityId,
      details: JSON.stringify(detailsObj),
      created_at: new Date().toISOString(),
    };
    setAuditLogs((prev) => [nextLog, ...prev]);
  };

  // ============================================================================
  // LIVE SUPABASE SYNC (WHEN VERIFIED BY USER)
  // ============================================================================
  const syncFromLiveSupabase = async (cfg: SupabaseVerificationConfig = supabaseConfig) => {
    const client = getVerifiedSupabaseClient(cfg);
    if (!client) {
      showNotice(
        'error',
        'Koneksi layanan belum siap. Periksa konfigurasi koneksi di Pengaturan Outlet.'
      );
      return;
    }
    setIsSyncingBackend(true);
    try {
      const [catRes, prodRes, tblRes, ordRes, promoRes] = await Promise.all([
        client.from('categories').select('*').order('sort_order'),
        client.from('products').select('*').order('name'),
        client.from('restaurant_tables').select('*').order('code'),
        client
          .from('orders')
          .select('*, order_items(*), payments(method, amount, cash_received, change_amount), restaurant_tables(name)')
          .order('created_at', { ascending: false })
          .limit(200),
        client.from('promotions').select('*').order('created_at', { ascending: false }),
      ]);

      if (catRes.error) throw catRes.error;
      if (prodRes.error) throw prodRes.error;
      if (tblRes.error) throw tblRes.error;

      setCategories((catRes.data ?? []) as Category[]);
      setProducts(
        (prodRes.data ?? []).map((p: Record<string, unknown>) => ({
          id: String(p.id),
          name: String(p.name),
          description: p.description ? String(p.description) : null,
          price: Number(p.price ?? 0),
          cost_price: Number(p.cost_price ?? 0),
          image_url: p.image_url ? String(p.image_url) : null,
          category_id: p.category_id ? String(p.category_id) : null,
          stock: Number(p.stock ?? 0),
          low_stock_threshold: Number(p.low_stock_threshold ?? 5),
          show_on_menu: Boolean(p.show_on_menu ?? true),
          is_active: Boolean(p.is_active ?? true),
        }))
      );
      setTables((tblRes.data ?? []) as RestaurantTable[]);
      // Use a real Supabase UUID for POS table selection; sandbox IDs like `tbl-1` are not database IDs.
      if (tblRes.data?.length) setSelectedTableId(String(tblRes.data[0].id));
      if (!ordRes.error && ordRes.data) {
        setOrders(ordRes.data as unknown as OrderWithItems[]);
      }
      if (!promoRes.error && promoRes.data) {
        setPromotions(promoRes.data as unknown as Promotion[]);
      }

      setBackendMode('LIVE_SUPABASE');
      showNotice('success', `Data berhasil disinkronkan.`);
    } catch (err) {
      showNotice(
        'error',
        `Data gagal dimuat. Periksa koneksi dan hak akses, lalu coba lagi.`
      );
    } finally {
      setIsSyncingBackend(false);
    }
  };

  const handleSupabaseLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    const client = getVerifiedSupabaseClient(supabaseConfig);
    if (!client) {
      showNotice('error', 'Selesaikan konfigurasi koneksi terlebih dahulu.');
      return;
    }
    setAuthLoading(true);
    try {
      const { data, error } = await client.auth.signInWithPassword({
        email: authEmail.trim(),
        password: authPassword,
      });
      if (error) throw error;
      if (data.user) {
        const { data: profileData } = await client
          .from('staff_profiles')
          .select('*')
          .eq('user_id', data.user.id)
          .maybeSingle();
        if (profileData) {
          setCurrentStaff(profileData as StaffProfile);
        }
        setAuthPassword('');
        showNotice('success', `Berhasil masuk sebagai ${data.user.email}`);
        await syncFromLiveSupabase(supabaseConfig);
      }
    } catch (err) {
      showNotice('error', err instanceof Error ? err.message : 'Login gagal. Periksa email & password.');
    } finally {
      setAuthLoading(false);
    }
  };

  // ============================================================================
  // KASIR & CART COMPUTATIONS
  // ============================================================================
  const filteredKasirProducts = useMemo(() => {
    return products.filter((p) => {
      if (!p.is_active) return false;
      const matchesCat = kasirCategory === 'ALL' || p.category_id === kasirCategory;
      const matchesSearch =
        !kasirSearch.trim() ||
        p.name.toLowerCase().includes(kasirSearch.toLowerCase()) ||
        (p.description ?? '').toLowerCase().includes(kasirSearch.toLowerCase());
      return matchesCat && matchesSearch;
    });
  }, [products, kasirCategory, kasirSearch]);

  const cartSubtotal = useMemo(() => {
    return cart.reduce((sum, item) => {
      const addonTotal = item.addons.reduce((a, b) => a + b.price, 0);
      return sum + (item.product.price + addonTotal) * item.quantity;
    }, 0);
  }, [cart]);

  const selectedPromo = useMemo(
    () => promotions.find((p) => p.id === selectedPromoId && p.active && p.allow_cashier !== false) || null,
    [promotions, selectedPromoId]
  );

  const promoEligibility = useMemo(() => {
    if (!selectedPromo) return { eligible: true, reason: '', eligibleSubtotal: cartSubtotal };
    if (cartSubtotal < Number(selectedPromo.min_purchase || 0)) {
      return { eligible: false, reason: `Minimum belanja ${formatRupiah(Number(selectedPromo.min_purchase || 0))} belum tercapai.`, eligibleSubtotal: 0 };
    }
    const matchingItems = selectedPromo.scope === 'product'
      ? cart.filter((item) => item.product.id === selectedPromo.product_id)
      : cart;
    const matchingQuantity = matchingItems.reduce((sum, item) => sum + item.quantity, 0);
    if (matchingQuantity < Math.max(1, Number(selectedPromo.min_quantity || 1))) {
      return { eligible: false, reason: `Minimal ${selectedPromo.min_quantity} item produk yang memenuhi promo belum tercapai.`, eligibleSubtotal: 0 };
    }
    const eligibleSubtotal = matchingItems.reduce((sum, item) => {
      const addonTotal = item.addons.reduce((a, b) => a + b.price, 0);
      return sum + (item.product.price + addonTotal) * item.quantity;
    }, 0);
    if (selectedPromo.scope === 'product' && eligibleSubtotal <= 0) {
      return { eligible: false, reason: 'Produk yang memenuhi promo belum ada di keranjang.', eligibleSubtotal: 0 };
    }
    return { eligible: true, reason: '', eligibleSubtotal };
  }, [selectedPromo, cart, cartSubtotal]);

  const cartDiscount = useMemo(() => {
    if (!selectedPromo || !promoEligibility.eligible) return 0;
    const raw = selectedPromo.type === 'percentage'
      ? (promoEligibility.eligibleSubtotal * selectedPromo.value) / 100
      : selectedPromo.value;
    return Math.min(promoEligibility.eligibleSubtotal, Math.max(0, Math.round(raw)));
  }, [selectedPromo, promoEligibility]);

  const cartTaxAndService = useMemo(() => {
    const base = Math.max(0, cartSubtotal - cartDiscount);
    const tax = outletSettings.tax_enabled ? (base * outletSettings.tax_rate) / 100 : 0;
    const svc = outletSettings.service_enabled ? (base * outletSettings.service_rate) / 100 : 0;
    return Math.round(tax + svc);
  }, [cartSubtotal, cartDiscount, outletSettings]);

  const cartGrandTotal = useMemo(() => {
    const raw = Math.max(0, cartSubtotal - cartDiscount) + cartTaxAndService;
    if (outletSettings.rounding > 0) {
      return Math.ceil(raw / outletSettings.rounding) * outletSettings.rounding;
    }
    return raw;
  }, [cartSubtotal, cartDiscount, cartTaxAndService, outletSettings.rounding]);

  const cashReceivedAmount = useMemo(() => {
    const parsed = Number(cashReceivedInput);
    return Number.isFinite(parsed) && cashReceivedInput !== '' ? parsed : cartGrandTotal;
  }, [cashReceivedInput, cartGrandTotal]);

  const cashChangeAmount = useMemo(() => {
    return Math.max(0, cashReceivedAmount - cartGrandTotal);
  }, [cashReceivedAmount, cartGrandTotal]);

  const handleAddToCartQuick = (product: Product) => {
    if (product.stock <= 0) {
      showNotice('error', `Stok ${product.name} sudah habis.`);
      return;
    }
    setCart((prev) => {
      const existingIdx = prev.findIndex(
        (item) => item.product.id === product.id && !item.note && item.addons.length === 0
      );
      const totalProductInCart = prev
        .filter((item) => item.product.id === product.id)
        .reduce((s, i) => s + i.quantity, 0);
      if (totalProductInCart + 1 > product.stock) {
        showNotice('error', `Stok ${product.name} hanya tersisa ${product.stock}.`);
        return prev;
      }
      if (existingIdx >= 0) {
        const clone = [...prev];
        clone[existingIdx] = { ...clone[existingIdx], quantity: clone[existingIdx].quantity + 1 };
        return clone;
      }
      return [
        ...prev,
        {
          cartItemId: `${product.id}-${Date.now()}`,
          product,
          quantity: 1,
          note: '',
          sugarLevel: 'Normal Sugar',
          iceLevel: 'Normal Ice',
          cupSize: 'Regular',
          addons: [],
        },
      ];
    });
  };

  const handleConfirmCustomProduct = () => {
    if (!customizingProduct) return;
    const totalProductInCart = cart
      .filter((item) => item.product.id === customizingProduct.id)
      .reduce((s, i) => s + i.quantity, 0);
    if (totalProductInCart + 1 > customizingProduct.stock) {
      showNotice('error', `Stok ${customizingProduct.name} hanya tersisa ${customizingProduct.stock}.`);
      return;
    }
    const combinedNote = [
      customSugar,
      customIce,
      customCup !== 'Regular' ? `Cup ${customCup}` : '',
      customAddons.length ? `Topping: ${customAddons.map((a) => a.name).join(', ')}` : '',
      customNote.trim(),
    ]
      .filter(Boolean)
      .join(' · ');

    setCart((prev) => [
      ...prev,
      {
        cartItemId: `${customizingProduct.id}-${Date.now()}`,
        product: customizingProduct,
        quantity: 1,
        note: combinedNote,
        sugarLevel: customSugar,
        iceLevel: customIce,
        cupSize: customCup,
        addons: customAddons,
      },
    ]);
    setCustomizingProduct(null);
    setCustomNote('');
    setCustomAddons([]);
  };

  const handleUpdateCartQty = (cartItemId: string, delta: number) => {
    setCart((prev) => {
      return prev
        .map((item) => {
          if (item.cartItemId !== cartItemId) return item;
          const nextQty = item.quantity + delta;
          if (nextQty <= 0) return null;
          if (nextQty > item.product.stock) {
            showNotice('error', `Stok maksimal ${item.product.name} adalah ${item.product.stock}.`);
            return item;
          }
          return { ...item, quantity: nextQty };
        })
        .filter(Boolean) as CartItem[];
    });
  };

  // ============================================================================
  // FINAL CHECKOUT (WITH IDEMPOTENCY & ANTI-DOUBLE SUBMIT)
  // ============================================================================
  const handleExecuteCheckout = async () => {
    if (cart.length === 0 || isCheckoutProcessing || checkoutLockRef.current) return;
    if (selectedPromoId && (!selectedPromo || !promoEligibility.eligible)) {\n      showNotice('error', promoEligibility.reason || 'Promo tidak aktif atau tidak tersedia untuk kasir.');\n      return;\n    }\n    if (paymentMethod === 'cash' && cashReceivedAmount < cartGrandTotal) {
      showNotice('error', 'Nominal uang tunai yang diterima kurang dari total tagihan.');
      return;
    }

    checkoutLockRef.current = true;
    setIsCheckoutProcessing(true);
    const idempotencyKey = crypto.randomUUID ? crypto.randomUUID() : `idem-${Date.now()}`;

    try {
      const tableObj = tables.find((t) => t.id === selectedTableId) || null;

      if (backendMode === 'LIVE_SUPABASE') {
        const client = getVerifiedSupabaseClient(supabaseConfig);
        if (!client) throw new Error('Klien Supabase belum aktif.');

        const payload = {
          p_order: {
            idempotency_key: idempotencyKey,
            source: 'pos',
            status: 'preparing',
            table_id: tableObj && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(tableObj.id) ? tableObj.id : null,
            promotion_id: selectedPromo?.id ?? null,
            customer_name: customerName.trim() || 'Walk-in Customer',
            notes: orderNotes.trim() || null,
            subtotal: cartSubtotal,
            discount: cartDiscount,
            tax: cartTaxAndService,
            total: cartGrandTotal,
            is_paid: true,
            payment_method: paymentMethod,
            payment_status: 'paid',
          },
          p_items: cart.map((c) => ({
            product_id: c.product.id,
            quantity: c.quantity,
            note: c.note || null,
          })),
          p_payment: {
            method: paymentMethod,
            cash_received: paymentMethod === 'cash' ? cashReceivedAmount : null,
          },
        };

        const { data, error } = await client.rpc('create_pos_order', payload);
        if (error) throw error;
        const createdId = (data as { order?: { id?: string } } | null)?.order?.id;
        if (!createdId) throw new Error('Respons RPC create_pos_order tidak mengembalikan ID order.');
        await syncFromLiveSupabase(supabaseConfig);
        setConfirmCheckoutModalOpen(false);
        setCart([]);
        setSelectedPromoId('');
        setCustomerName('');
        setOrderNotes('');
        setCashReceivedInput('');
        showNotice('success', 'Transaksi berhasil dikonfirmasi oleh server Supabase.');
        return;
      }

      // Mode Simulasi UI Lokal Terlabel
      const nextOrderNo =
        orders.length > 0 ? Math.max(...orders.map((o) => o.order_no)) + 1 : 1001;
      const nowIso = new Date().toISOString();
      const newOrderId = `ord-${nextOrderNo}`;

      const newOrder: OrderWithItems = {
        id: newOrderId,
        order_no: nextOrderNo,
        source: 'pos',
        status: 'preparing',
        table_id: tableObj?.id ?? null,
        customer_name: customerName.trim() || 'Walk-in Customer',
        notes: orderNotes.trim() || null,
        subtotal: cartSubtotal,
        discount: cartDiscount,
        tax: cartTaxAndService,
        total: cartGrandTotal,
        is_paid: true,
        payment_method: paymentMethod,
        payment_status: 'paid',
        created_at: nowIso,
        updated_at: nowIso,
        restaurant_tables: tableObj ? { name: tableObj.name } : { name: 'Takeaway' },
        order_items: cart.map((c, idx) => {
          const addonPrice = c.addons.reduce((s, a) => s + a.price, 0);
          const unitPrice = c.product.price + addonPrice;
          return {
            id: `oi-${Date.now()}-${idx}`,
            order_id: newOrderId,
            product_id: c.product.id,
            product_name: c.product.name,
            unit_price: unitPrice,
            quantity: c.quantity,
            note: c.note || null,
            line_total: unitPrice * c.quantity,
            cost_price_snapshot: c.product.cost_price,
          };
        }),
        payments: [
          {
            id: `pay-${Date.now()}`,
            method: paymentMethod,
            amount: cartGrandTotal,
            cash_received: paymentMethod === 'cash' ? cashReceivedAmount : null,
            change_amount: paymentMethod === 'cash' ? cashChangeAmount : null,
          },
        ],
      };

      // Deduct stock ONCE atomically (matching trg_deduct_stock in PostgreSQL)
      setProducts((prevProducts) =>
        prevProducts.map((prod) => {
          const soldQty = cart
            .filter((c) => c.product.id === prod.id)
            .reduce((sum, c) => sum + c.quantity, 0);
          if (soldQty <= 0) return prod;
          return { ...prod, stock: Math.max(0, prod.stock - soldQty) };
        })
      );

      setOrders((prev) => [newOrder, ...prev]);
      appendAuditLog('order.create_pos_order', 'orders', newOrder.id, {
        idempotency_key: idempotencyKey,
        order_no: newOrder.order_no,
        total: newOrder.total,
        method: paymentMethod,
      });

      // Auto-print if configured
      if (printers.cashier?.connected && printers.cashier.autoPrint) {
        setPrintedReceiptIds((prev) => [...prev, newOrder.id]);
        setThermalPreviewText(formatEscPosReceiptText(newOrder, 'cashier'));
      }
      if (printers.kitchen?.connected && printers.kitchen.autoPrint) {
        setPrintedKitchenIds((prev) => [...prev, newOrder.id]);
      }

      setConfirmCheckoutModalOpen(false);
      setCart([]);
      setSelectedPromoId('');
      setCustomerName('');
      setOrderNotes('');
      setCashReceivedInput('');
      setReceiptModalOrder(newOrder);
      showNotice(
        'success',
        `Order #${newOrder.order_no} berhasil disimpan (${formatRupiah(newOrder.total)}).`
      );
    } catch (err) {
      showNotice(
        'error',
        err instanceof Error ? err.message : 'Gagal memproses transaksi.'
      );
    } finally {
      setIsCheckoutProcessing(false);
      checkoutLockRef.current = false;
    }
  };

  // ============================================================================
  // ORDER STATUS & QR APPROVAL HANDLERS
  // ============================================================================
  const handleUpdateOrderStatus = async (order: OrderWithItems, nextStatus: OrderStatus) => {
    if (backendMode === 'LIVE_SUPABASE') {
      showNotice('error', 'Perubahan status langsung diblokir sementara: perlu RPC server yang memvalidasi outlet, pembayaran, dan pemulihan stok. Status order tidak diubah.');
      return;
    }

    // Restore stock once if transitioning to cancelled (matching trg_restore_stock)
    if (nextStatus === 'cancelled' && order.status !== 'cancelled') {
      setProducts((prev) =>
        prev.map((prod) => {
          const item = order.order_items.find((oi) => oi.product_id === prod.id);
          if (!item) return prod;
          return { ...prod, stock: prod.stock + item.quantity };
        })
      );
    }

    setOrders((prev) =>
      prev.map((o) =>
        o.id === order.id ? { ...o, status: nextStatus, updated_at: new Date().toISOString() } : o
      )
    );
    appendAuditLog('order.update_status', 'orders', order.id, {
      order_no: order.order_no,
      from: order.status,
      to: nextStatus,
    });
    showNotice('success', `Order #${order.order_no} diperbarui menjadi ${STATUS_LABELS[nextStatus]}.`);
  };

  const handleApproveQrOrder = async (order: OrderWithItems, method: 'qris' | 'cash') => {
    if (backendMode === 'LIVE_SUPABASE') {
      const client = getVerifiedSupabaseClient(supabaseConfig);
      if (!client) return;
      const { error } = await client.rpc('approve_qr_order_with_method', {
        p_order_id: order.id,
        p_payment_method: method,
        p_cash_received: method === 'cash' ? order.total : null,
      });
      if (error) {
        showNotice('error', error.message);
        return;
      }
      await syncFromLiveSupabase(supabaseConfig);
      showNotice('success', `Pembayaran QR Order #${order.order_no} dikonfirmasi.`);
      return;
    }

    setOrders((prev) =>
      prev.map((o) =>
        o.id === order.id
          ? {
              ...o,
              is_paid: true,
              payment_method: method,
              payment_status: 'paid',
              status: o.status === 'new' ? 'preparing' : o.status,
              payments: [
                {
                  id: `pay-${Date.now()}`,
                  method,
                  amount: o.total,
                  cash_received: method === 'cash' ? o.total : null,
                  change_amount: method === 'cash' ? 0 : null,
                },
              ],
            }
          : o
      )
    );
    appendAuditLog('order.approve_qr_order', 'orders', order.id, {
      order_no: order.order_no,
      method,
    });
    showNotice('success', `Pembayaran QR Order #${order.order_no} (${method.toUpperCase()}) disetujui.`);
  };

  const handleRejectQrOrder = async (order: OrderWithItems) => {
    if (backendMode === 'LIVE_SUPABASE') {
      const client = getVerifiedSupabaseClient(supabaseConfig);
      if (!client) return;
      const { error } = await client.rpc('reject_qr_order', { p_order_id: order.id });
      if (error) {
        showNotice('error', error.message);
        return;
      }
      await syncFromLiveSupabase(supabaseConfig);
      showNotice('info', `Pesanan QR #${order.order_no} ditolak.`);
      return;
    }

    setOrders((prev) =>
      prev.map((o) =>
        o.id === order.id
          ? { ...o, status: 'cancelled', payment_status: 'rejected', is_paid: false }
          : o
      )
    );
    showNotice('info', `Pesanan QR #${order.order_no} telah ditolak.`);
  };

  // ============================================================================
  // PRINTER ESC/POS DISPATCH (WITH ANTI-DUPLICATE LOCK)
  // ============================================================================
  const handlePrintOrder = (order: OrderWithItems, role: PrinterRole, forceReprint = false) => {
    const targetPrinter = printers[role];
    if (!bluetoothPermGranted) {
      showNotice(
        'error',
        'Izin Bluetooth (BLUETOOTH_CONNECT & BLUETOOTH_SCAN) belum diberikan pada perangkat Android.'
      );
      return;
    }
    if (!targetPrinter || !targetPrinter.connected) {
      showNotice(
        'error',
        `Printer ${role === 'cashier' ? 'Struk Kasir' : 'Tiket Dapur'} belum terhubung. Periksa menu Printer.`
      );
      return;
    }

    if (role === 'cashier' && !forceReprint && printedReceiptIds.includes(order.id)) {
      showNotice(
        'info',
        `Struk Order #${order.order_no} sudah pernah dicetak. Gunakan tombol "Cetak Ulang" jika ingin mencetak salinan.`
      );
      return;
    }
    if (role === 'kitchen' && !forceReprint && printedKitchenIds.includes(order.id)) {
      showNotice(
        'info',
        `Tiket Dapur Order #${order.order_no} sudah pernah dicetak.`
      );
      return;
    }

    const formatted = formatEscPosReceiptText(order, role);
    setThermalPreviewText(formatted);
    if (role === 'cashier') {
      setPrintedReceiptIds((prev) => (prev.includes(order.id) ? prev : [...prev, order.id]));
    } else {
      setPrintedKitchenIds((prev) => (prev.includes(order.id) ? prev : [...prev, order.id]));
    }
    showNotice(
      'info',
      `Preview struk/tiket dibuat untuk ${targetPrinter.name}. Pengiriman ESC/POS ke printer fisik belum terhubung di UI web ini.`
    );
  };

  // ============================================================================
  // DASHBOARD & REPORT METRICS
  // ============================================================================
  const paidOrders = useMemo(
    () => orders.filter((o) => o.is_paid && o.status !== 'cancelled'),
    [orders]
  );

  const todayRevenue = useMemo(
    () => paidOrders.reduce((sum, o) => sum + Number(o.total), 0),
    [paidOrders]
  );

  const activeOrdersCount = useMemo(
    () => orders.filter((o) => o.status === 'new' || o.status === 'preparing' || o.status === 'ready').length,
    [orders]
  );

  const lowStockProducts = useMemo(
    () => products.filter((p) => p.stock <= p.low_stock_threshold),
    [products]
  );

  const topSellingProducts = useMemo(() => {
    const map = new Map<string, { name: string; qty: number; revenue: number }>();
    paidOrders.forEach((o) => {
      o.order_items.forEach((item) => {
        const key = item.product_name;
        const curr = map.get(key) || { name: item.product_name, qty: 0, revenue: 0 };
        curr.qty += item.quantity;
        curr.revenue += item.line_total;
        map.set(key, curr);
      });
    });
    return Array.from(map.values()).sort((a, b) => b.qty - a.qty);
  }, [paidOrders]);

  const paymentBreakdown = useMemo(() => {
    const map = new Map<string, { method: string; count: number; total: number }>();
    paidOrders.forEach((o) => {
      const m = (o.payment_method || 'cash').toLowerCase();
      const curr = map.get(m) || { method: m, count: 0, total: 0 };
      curr.count += 1;
      curr.total += o.total;
      map.set(m, curr);
    });
    return Array.from(map.values());
  }, [paidOrders]);

  const profitLossSummary = useMemo(() => {
    const prodCostMap = new Map(products.map((p) => [p.id, p.cost_price]));
    const netSales = paidOrders.reduce((s, o) => s + o.total, 0);
    const cogs = paidOrders.reduce((s, o) => {
      return (
        s +
        o.order_items.reduce((acc, item) => {
          const unitCost =
            item.cost_price_snapshot ?? (item.product_id ? prodCostMap.get(item.product_id) ?? 0 : 0);
          return acc + unitCost * item.quantity;
        }, 0)
      );
    }, 0);
    const filteredExp = expenses.filter(
      (e) => e.expense_date >= financeFrom && e.expense_date <= financeTo
    );
    const totalExpenses = filteredExp.reduce((s, e) => s + e.amount, 0);
    return {
      netSales,
      cogs,
      totalExpenses,
      netProfit: netSales - cogs - totalExpenses,
      filteredExp,
    };
  }, [paidOrders, products, expenses, financeFrom, financeTo]);

  // ============================================================================
  // RENDER MODULE CONTENT
  // ============================================================================
  const renderBackendInactiveGate = () => (
    <div className="rounded-2xl border border-zinc-300 bg-white p-6 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-2xl space-y-2">
          <div className="inline-flex items-center gap-2 text-xs font-bold tracking-wider text-amber-700 uppercase dark:text-amber-400">
            <AlertTriangle className="size-4" />
            <span>Integrasi Supabase: BELUM AKTIF (Menunggu Verifikasi Project ID)</span>
          </div>
          <h2 className="text-lg font-bold">
            Verifikasi Project ID Supabase Sebelum Menghubungkan Database Produksi
          </h2>
          <p className="text-sm leading-relaxed text-zinc-600 dark:text-zinc-400">
            Hasil pemeriksaan kami terhadap kedua referensi menunjukkan dua Project ID yang berbeda:
            <span className="mx-1 font-mono font-semibold text-zinc-900 dark:text-zinc-100">
              rvaemgwtdvncfvaerhgr
            </span>
            (pada <code className="font-mono">supabase/config.toml</code> di repository GitHub) dan
            <span className="mx-1 font-mono font-semibold text-zinc-900 dark:text-zinc-100">
              dbazoxwhhljpgtbnletc
            </span>
            (pada bundle produksi <code className="font-mono">pos.yuripratama246.workers.dev</code>).
            Sesuai pedoman keamanan, aplikasi tidak memilih salah satu secara asumsi dan tidak menampilkan data contoh seolah-olah berasal dari database produksi.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={() => setActiveModule('pengaturan')}
            className="inline-flex min-h-[42px] items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200 whitespace-nowrap shrink-0"
          >
            <Settings className="size-4" />
            Verifikasi &amp; Hubungkan Supabase
          </button>
          <button
            type="button"
            onClick={() => {
              setBackendMode('LOCAL_SANDBOX_UI');
              showNotice(
                'info',
                'Mode Simulasi UI Lokal diaktifkan. Seluruh data diberi penanda [SIMULASI LOKAL - BUKAN DATABASE PRODUKSI].'
              );
            }}
            className="inline-flex min-h-[42px] items-center gap-2 rounded-xl border border-zinc-300 bg-zinc-50 px-4 py-2.5 text-xs font-semibold text-zinc-900 transition-colors hover:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700 whitespace-nowrap shrink-0"
          >
            <Eye className="size-4" />
            Uji Alur UI (Mode Simulasi Lokal Terlabel)
          </button>
        </div>
      </div>
    </div>
  );

  const renderModuleContent = () => {
    if (!canAccessModule(NAV_GROUPS.flatMap((g) => g.items).find((i) => i.id === activeModule)?.permission || 'dashboard')) {
      return (
        <div className="rounded-2xl border border-zinc-200 bg-white p-8 text-center dark:border-zinc-800 dark:bg-zinc-900">
          <Lock className="mx-auto mb-3 size-8 text-zinc-500" />
          <h3 className="text-base font-bold">Akses Modul Dibatasi Role</h3>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Akun aktif saat ini ({currentStaff.full_name} · Role: <span className="font-mono">{currentStaff.role}</span>) tidak memiliki izin untuk membuka modul ini.
          </p>
        </div>
      );
    }

    // If backend is still INACTIVE_PENDING_VERIFICATION and user hasn't opened Pengaturan,Printer, or AI
    if (
      backendMode === 'INACTIVE_PENDING_VERIFICATION' &&
      activeModule !== 'pengaturan' &&
      activeModule !== 'printer' &&
      activeModule !== 'ai'
    ) {
      return (
        <div className="space-y-4">
          {renderBackendInactiveGate()}
          <div className="rounded-2xl border border-dashed border-zinc-300 bg-white/60 p-10 text-center dark:border-zinc-800 dark:bg-zinc-900/40">
            <ShieldAlert className="mx-auto mb-3 size-9 text-zinc-400" />
            <h3 className="text-base font-bold">Menunggu Verifikasi Kredensial Database Produksi</h3>
            <p className="mx-auto mt-1 max-w-lg text-xs leading-relaxed text-zinc-500 dark:text-zinc-400">
              Modul <strong>{NAV_GROUPS.flatMap((g) => g.items).find((i) => i.id === activeModule)?.label}</strong> tidak menampilkan data dummy sebagai data database. Klik <strong>&ldquo;Verifikasi &amp; Hubungkan Supabase&rdquo;</strong> untuk menyambungkan database produksi, atau klik <strong>&ldquo;Uji Alur UI (Mode Simulasi Lokal Terlabel)&rdquo;</strong> di atas untuk menguji seluruh fungsi interaktif.
            </p>
          </div>
        </div>
      );
    }

    switch (activeModule) {
      // ======================================================================
      // 1. DASHBOARD
      // ======================================================================
      case 'dashboard':
        return (
          <div className="space-y-6">
            {/* KPI Row */}
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <p className="text-xs font-medium text-zinc-500 dark:text-zinc-400">Omzet Terkonfirmasi</p>
                <p className="num mt-2 text-2xl font-bold tracking-tight">{formatRupiah(todayRevenue)}</p>
                <p className="mt-1 text-xs text-zinc-500">
                  {paidOrders.length} transaksi lunas · Hari ini
                </p>
              </div>
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <p className="text-xs font-medium text-zinc-500 dark:text-zinc-400">Pesanan Aktif</p>
                <p className="num mt-2 text-2xl font-bold tracking-tight">{activeOrdersCount}</p>
                <p className="mt-1 text-xs text-zinc-500">
                  Baru, Diproses &amp; Siap Saji
                </p>
              </div>
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <p className="text-xs font-medium text-zinc-500 dark:text-zinc-400">Rata-rata Nilai Transaksi</p>
                <p className="num mt-2 text-2xl font-bold tracking-tight">
                  {formatRupiah(paidOrders.length ? todayRevenue / paidOrders.length : 0)}
                </p>
                <p className="mt-1 text-xs text-zinc-500">Per struk terbayar</p>
              </div>
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <p className="text-xs font-medium text-zinc-500 dark:text-zinc-400">Indikator Stok Menipis</p>
                <p className="num mt-2 text-2xl font-bold tracking-tight">{lowStockProducts.length} Produk</p>
                <p className="mt-1 text-xs text-zinc-500">
                  Di bawah ambang batas minimum
                </p>
              </div>
            </div>

            {/* Chart & Top Products */}
            <div className="grid gap-6 lg:grid-cols-3">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 lg:col-span-2 dark:border-zinc-800 dark:bg-zinc-900">
                <div className="flex items-center justify-between border-b border-zinc-100 pb-4 dark:border-zinc-800">
                  <div>
                    <h3 className="text-base font-bold">Grafik Penjualan &amp; Distribusi Kanal</h3>
                    <p className="text-xs text-zinc-500">Perbandingan transaksi Kasir POS vs QR Meja</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setActiveModule('kasir')}
                    className="rounded-lg bg-zinc-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 whitespace-nowrap shrink-0"
                  >
                    Buka Kasir POS
                  </button>
                </div>

                {/* Clean Monochrome Bar Distribution */}
                <div className="mt-5 space-y-4">
                  {topSellingProducts.slice(0, 5).map((item) => {
                    const maxRev = Math.max(...topSellingProducts.map((x) => x.revenue), 1);
                    const widthPct = Math.max(8, Math.round((item.revenue / maxRev) * 100));
                    return (
                      <div key={item.name} className="space-y-1.5">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-medium">{item.name}</span>
                          <span className="num text-zinc-500">
                            {item.qty} terjual · <strong className="text-zinc-900 dark:text-zinc-100">{formatRupiah(item.revenue)}</strong>
                          </span>
                        </div>
                        <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
                          <div
                            className="h-full rounded-full bg-zinc-900 dark:bg-zinc-200"
                            style={{ width: `${widthPct}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Metode Pembayaran & Stok Menipis */}
              <div className="space-y-6">
                <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                  <h3 className="text-base font-bold">Metode Pembayaran</h3>
                  <div className="mt-3 divide-y divide-zinc-100 dark:divide-zinc-800">
                    {paymentBreakdown.map((pm) => (
                      <div key={pm.method} className="flex items-center justify-between py-2.5 text-xs">
                        <span className="font-semibold uppercase">{pm.method}</span>
                        <span className="num text-zinc-600 dark:text-zinc-300">
                          {pm.count}x · <strong>{formatRupiah(pm.total)}</strong>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-bold">Peringatan Stok Menipis</h3>
                    <button
                      type="button"
                      onClick={() => setActiveModule('produk')}
                      className="text-xs font-semibold underline"
                    >
                      Kelola Stok
                    </button>
                  </div>
                  <div className="mt-3 divide-y divide-zinc-100 dark:divide-zinc-800">
                    {lowStockProducts.length === 0 ? (
                      <p className="py-4 text-xs text-zinc-500">Seluruh stok produk berada di atas batas aman.</p>
                    ) : (
                      lowStockProducts.map((p) => (
                        <div key={p.id} className="flex items-center justify-between py-2.5 text-xs">
                          <div>
                            <p className="font-medium">{p.name}</p>
                            <p className="text-zinc-500">Batas minimum: {p.low_stock_threshold}</p>
                          </div>
                          <span className="num font-bold text-red-600 dark:text-red-400">
                            Sisa {p.stock}
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 2. AI ASSISTANT (HONEST STATUS DISCLOSURE)
      // ======================================================================
      case 'ai':
        return (
          <div className="mx-auto max-w-4xl space-y-6">
            <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-start gap-4">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900">
                  <Bot className="size-6" />
                </div>
                <div className="space-y-2">
                  <h2 className="text-lg font-bold">AI Assistant — Kereta Malam Cafe</h2>
                  <p className="text-xs leading-relaxed text-zinc-600 dark:text-zinc-400">
                    Mengikuti implementasi <code className="font-mono">src/routes/_authenticated/ai.tsx</code> pada sistem referensi Cafe Flow yang memanggil Supabase Edge Function <code className="font-mono">supabase.functions.invoke(&quot;ai-assistant&quot;)</code>.
                  </p>
                  <div className="mt-3 rounded-lg border border-amber-300 bg-amber-50/80 p-4 text-xs text-amber-950 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                    <p className="font-bold">STATUS BACKEND AI: BELUM TERHUBUNG</p>
                    <p className="mt-1">
                      Edge Function <code className="font-mono">ai-assistant</code> pada Supabase belum dikonfigurasi/diverifikasi. Sesuai aturan sistem, aplikasi ini <strong>tidak menampilkan jawaban AI palsu</strong> seolah-olah AI sudah terintegrasi.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 3. KASIR (POS)
      // ======================================================================
      case 'kasir':
        return (
          <div className="grid gap-6 lg:grid-cols-12">
            {/* Katalog Produk (Kiri - 7 Kolom) */}
            <div className="space-y-4 lg:col-span-7 xl:col-span-8">
              <div className="flex flex-wrap items-center gap-3 rounded-xl border border-zinc-200 bg-white p-3.5 dark:border-zinc-800 dark:bg-zinc-900">
                <div className="relative min-w-[200px] flex-1">
                  <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-zinc-400" />
                  <input
                    type="text"
                    value={kasirSearch}
                    onChange={(e) => setKasirSearch(e.target.value)}
                    placeholder="Cari menu kopi, makanan, snack..."
                    className="w-full rounded-lg border border-zinc-200 bg-zinc-50 py-2 pr-3 pl-9 text-xs focus:border-zinc-900 focus:outline-none dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setKasirCategory('ALL')}
                    className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                      kasirCategory === 'ALL'
                        ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                        : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300'
                    }`}
                  >
                    Semua Menu
                  </button>
                  {categories.map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => setKasirCategory(cat.id)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                        kasirCategory === cat.id
                          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300'
                      }`}
                    >
                      {cat.name}
                    </button>
                  ))}
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {filteredKasirProducts.map((product) => {
                  const isOut = product.stock <= 0;
                  const isLow = product.stock > 0 && product.stock <= product.low_stock_threshold;
                  return (
                    <div
                      key={product.id}
                      className={`flex flex-col justify-between rounded-xl border p-4 transition-all ${
                        isOut
                          ? 'border-zinc-200 bg-zinc-100/60 opacity-60 dark:border-zinc-800 dark:bg-zinc-900/40'
                          : 'border-zinc-200 bg-white hover:border-zinc-400 dark:border-zinc-800 dark:bg-zinc-900 dark:hover:border-zinc-600'
                      }`}
                    >
                      <div>
                        <div className="flex items-start justify-between gap-2">
                          <h4 className="text-sm font-bold">{product.name}</h4>
                          <span className="num text-sm font-bold">{formatRupiah(product.price)}</span>
                        </div>
                        <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
                          {product.description || 'Menu Kereta Malam Cafe'}
                        </p>
                        <p className="mt-2 text-[11px] text-zinc-500">
                          Stok: <span className="num font-semibold">{product.stock}</span>
                          {isLow ? ' · Stok Menipis' : ''}
                          {isOut ? ' · Habis' : ''}
                        </p>
                      </div>

                      <div className="mt-4 flex items-center gap-2 pt-2">
                        <button
                          type="button"
                          disabled={isOut}
                          onClick={() => handleAddToCartQuick(product)}
                          className="min-h-[38px] flex-1 rounded-lg bg-zinc-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200 whitespace-nowrap shrink-0"
                        >
                          + Tambah
                        </button>
                        <button
                          type="button"
                          disabled={isOut}
                          onClick={() => {
                            setCustomizingProduct(product);
                            setCustomSugar('Normal Sugar');
                            setCustomIce('Normal Ice');
                            setCustomCup('Regular');
                            setCustomNote('');
                            setCustomAddons([]);
                          }}
                          className="min-h-[38px] rounded-lg border border-zinc-300 px-2.5 py-1.5 text-xs font-medium hover:bg-zinc-100 disabled:opacity-40 dark:border-zinc-700 dark:hover:bg-zinc-800 whitespace-nowrap shrink-0"
                        >
                          Opsi
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Panel Keranjang & Pembayaran (Kanan - 5 Kolom) */}
            <div className="lg:col-span-5 xl:col-span-4">
              <div className="sticky top-20 flex flex-col rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <div className="flex items-center justify-between border-b border-zinc-100 pb-3 dark:border-zinc-800">
                  <h3 className="text-base font-bold">Keranjang Transaksi</h3>
                  {cart.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setCart([])}
                      className="text-xs font-medium text-red-600 hover:underline dark:text-red-400"
                    >
                      Kosongkan
                    </button>
                  )}
                </div>

                {/* Pilihan Meja & Nama Pelanggan */}
                <div className="mt-3 grid grid-cols-2 gap-2.5">
                  <div>
                    <label className="block text-[11px] font-medium text-zinc-500">Meja / Layanan</label>
                    <select
                      value={selectedTableId}
                      onChange={(e) => setSelectedTableId(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    >
                      {tables.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name} ({t.code})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-zinc-500">Nama Pelanggan</label>
                    <input
                      type="text"
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
                      placeholder="Walk-in / Nama..."
                      className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    />
                  </div>
                </div>

                {/* Daftar Item Keranjang */}
                <div className="my-4 max-h-64 space-y-2.5 overflow-y-auto pr-1">
                  {cart.length === 0 ? (
                    <p className="py-8 text-center text-xs text-zinc-400">
                      Keranjang masih kosong. Pilih produk di sebelah kiri.
                    </p>
                  ) : (
                    cart.map((item) => {
                      const addonSum = item.addons.reduce((s, a) => s + a.price, 0);
                      const unit = item.product.price + addonSum;
                      return (
                        <div
                          key={item.cartItemId}
                          className="rounded-lg border border-zinc-200 p-2.5 text-xs dark:border-zinc-800"
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <p className="font-semibold">{item.product.name}</p>
                              <p className="num text-zinc-500">
                                {formatRupiah(unit)} × {item.quantity} ={' '}
                                <strong className="text-zinc-900 dark:text-zinc-100">
                                  {formatRupiah(unit * item.quantity)}
                                </strong>
                              </p>
                            </div>
                            <div className="flex items-center gap-1">
                              <button
                                type="button"
                                onClick={() => handleUpdateCartQty(item.cartItemId, -1)}
                                className="flex size-7 items-center justify-center rounded border border-zinc-300 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                              >
                                <Minus className="size-3" />
                              </button>
                              <span className="num w-6 text-center font-bold">{item.quantity}</span>
                              <button
                                type="button"
                                onClick={() => handleUpdateCartQty(item.cartItemId, 1)}
                                className="flex size-7 items-center justify-center rounded border border-zinc-300 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                              >
                                <Plus className="size-3" />
                              </button>
                            </div>
                          </div>
                          <input
                            type="text"
                            value={item.note}
                            onChange={(e) => {
                              const val = e.target.value;
                              setCart((prev) =>
                                prev.map((c) => (c.cartItemId === item.cartItemId ? { ...c, note: val } : c))
                              );
                            }}
                            placeholder="Catatan item (mis. less sugar, saus pisah)..."
                            className="mt-2 w-full rounded border border-zinc-200 bg-zinc-50 px-2 py-1 text-[11px] dark:border-zinc-800 dark:bg-zinc-950"
                          />
                        </div>
                      );
                    })
                  )}
                </div>

                {/* Promo & Metode Pembayaran */}
                <div className="space-y-2.5 border-t border-zinc-100 pt-3 text-xs dark:border-zinc-800">
                  <div>
                    <label className="block text-[11px] font-medium text-zinc-500">Voucher / Promosi</label>
                    <select
                      value={selectedPromoId}
                      onChange={(e) => setSelectedPromoId(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    >
                      <option value="">Tanpa Promosi</option>
                      {promotions
                        .filter((p) => p.active && p.allow_cashier !== false)
                        .map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} (Min. {formatRupiah(p.min_purchase)}{p.scope === 'product' ? `, min ${p.min_quantity} item produk` : ''})
                          </option>
                        ))}
                    </select>
                  </div>
                  {selectedPromoId && (
                    <p className={`rounded-lg px-2.5 py-2 text-[11px] ${promoEligibility.eligible ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-300' : 'bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-300'}`}>
                      {promoEligibility.eligible ? `Promo berlaku · Diskon ${formatRupiah(cartDiscount)}` : promoEligibility.reason}
                    </p>
                  )}

                  <div>
                    <label className="block text-[11px] font-medium text-zinc-500">Metode Pembayaran</label>
                    <div className="mt-1 grid grid-cols-4 gap-1.5">
                      {(
                        [
                          ['cash', 'Tunai'],
                          ['qris', 'QRIS'],
                          ['transfer', 'Transfer'],
                          ['ewallet', 'E-Wallet'],
                        ] as const
                      ).map(([code, label]) => (
                        <button
                          key={code}
                          type="button"
                          onClick={() => setPaymentMethod(code)}
                          className={`rounded-lg py-2 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                            paymentMethod === code
                              ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                              : 'border border-zinc-200 bg-zinc-50 text-zinc-700 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {paymentMethod === 'cash' && (
                    <div>
                      <label className="block text-[11px] font-medium text-zinc-500">
                        Uang Tunai Diterima (Rp)
                      </label>
                      <input
                        type="number"
                        value={cashReceivedInput}
                        onChange={(e) => setCashReceivedInput(e.target.value)}
                        placeholder={String(cartGrandTotal)}
                        className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-2.5 py-2 text-xs font-semibold dark:border-zinc-800 dark:bg-zinc-950"
                      />
                      <div className="mt-1.5 flex flex-wrap gap-1.5">
                        {[cartGrandTotal, 50000, 100000, 200000].map((amt, i) => (
                          <button
                            key={i}
                            type="button"
                            onClick={() => setCashReceivedInput(String(amt))}
                            className="num rounded border border-zinc-200 px-2 py-1 text-[11px] hover:bg-zinc-100 dark:border-zinc-800 dark:hover:bg-zinc-800"
                          >
                            {i === 0 ? 'Uang Pas' : formatRupiah(amt)}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Ringkasan Kalkulasi */}
                  <div className="space-y-1 border-t border-zinc-100 pt-2.5 dark:border-zinc-800">
                    <div className="flex justify-between text-zinc-500">
                      <span>Subtotal</span>
                      <span className="num">{formatRupiah(cartSubtotal)}</span>
                    </div>
                    {cartDiscount > 0 && (
                      <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                        <span>Diskon Promo</span>
                        <span className="num">-{formatRupiah(cartDiscount)}</span>
                      </div>
                    )}
                    {cartTaxAndService > 0 && (
                      <div className="flex justify-between text-zinc-500">
                        <span>Pajak &amp; Layanan</span>
                        <span className="num">{formatRupiah(cartTaxAndService)}</span>
                      </div>
                    )}
                    <div className="flex justify-between pt-1 text-sm font-bold">
                      <span>Total Tagihan</span>
                      <span className="num">{formatRupiah(cartGrandTotal)}</span>
                    </div>
                    {paymentMethod === 'cash' && cart.length > 0 && (
                      <div className="flex justify-between text-xs text-zinc-500">
                        <span>Kembalian</span>
                        <span className="num font-semibold">{formatRupiah(cashChangeAmount)}</span>
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    disabled={cart.length === 0 || isCheckoutProcessing}
                    onClick={() => setConfirmCheckoutModalOpen(true)}
                    className="mt-3 min-h-[44px] w-full rounded-xl bg-zinc-900 py-3 text-xs font-bold text-white transition-colors hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
                  >
                    {isCheckoutProcessing ? 'Memproses Transaksi...' : `Proses Pembayaran · ${formatRupiah(cartGrandTotal)}`}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 4. PESANAN (ORDERS & QR ORDERING)
      // ======================================================================
      case 'pesanan': {
        const filteredOrders = orders.filter((o) => {
          const matchStatus = orderStatusFilter === 'ALL' || o.status === orderStatusFilter;
          const matchSource = orderSourceFilter === 'ALL' || o.source === orderSourceFilter;
          const matchSearch =
            !orderSearch.trim() ||
            String(o.order_no).includes(orderSearch) ||
            (o.customer_name ?? '').toLowerCase().includes(orderSearch.toLowerCase());
          return matchStatus && matchSource && matchSearch;
        });

        return (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex flex-wrap items-center gap-1.5">
                {(['ALL', 'new', 'preparing', 'ready', 'completed', 'cancelled'] as const).map(
                  (st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => setOrderStatusFilter(st)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
                        orderStatusFilter === st
                          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
                      }`}
                    >
                      {st === 'ALL' ? 'Semua Status' : STATUS_LABELS[st]}
                    </button>
                  )
                )}
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={orderSearch}
                  onChange={(e) => setOrderSearch(e.target.value)}
                  placeholder="Cari #Order atau pelanggan..."
                  className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {filteredOrders.map((order) => (
                <div
                  key={order.id}
                  className="flex flex-col justify-between rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900"
                >
                  <div>
                    <div className="flex items-center justify-between border-b border-zinc-100 pb-3 dark:border-zinc-800">
                      <div>
                        <span className="num text-base font-bold">Order #{order.order_no}</span>
                        <p className="text-xs text-zinc-500">
                          {order.source.toUpperCase()} · {order.restaurant_tables?.name ?? 'Takeaway'} ·{' '}
                          {order.customer_name || 'Walk-in'}
                        </p>
                      </div>
                      <div className="text-right text-xs">
                        <p className="font-bold">{STATUS_LABELS[order.status]}</p>
                        <p className="text-zinc-500">
                          {order.is_paid ? `Lunas (${(order.payment_method || 'cash').toUpperCase()})` : 'Menunggu Konfirmasi'}
                        </p>
                      </div>
                    </div>

                    <div className="my-3 space-y-1.5 text-xs">
                      {order.order_items.map((item) => (
                        <div key={item.id} className="flex justify-between">
                          <span>
                            <strong>{item.quantity}x</strong> {item.product_name}
                            {item.note ? <em className="block text-[11px] text-zinc-500">Catatan: {item.note}</em> : null}
                          </span>
                          <span className="num">{formatRupiah(item.line_total)}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="space-y-3 border-t border-zinc-100 pt-3 dark:border-zinc-800">
                    <div className="flex items-center justify-between text-sm font-bold">
                      <span>Total</span>
                      <span className="num">{formatRupiah(order.total)}</span>
                    </div>

                    {/* Konfirmasi Pembayaran QR jika belum lunas */}
                    {order.source === 'qr' && !order.is_paid && order.status !== 'cancelled' && (
                      <div className="flex flex-wrap gap-1.5">
                        <button
                          type="button"
                          onClick={() => handleApproveQrOrder(order, 'qris')}
                          className="flex-1 rounded-lg bg-zinc-900 px-2.5 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900 whitespace-nowrap shrink-0"
                        >
                          Setujui QRIS
                        </button>
                        <button
                          type="button"
                          onClick={() => handleApproveQrOrder(order, 'cash')}
                          className="flex-1 rounded-lg border border-zinc-300 px-2.5 py-2 text-xs font-semibold dark:border-zinc-700 whitespace-nowrap shrink-0"
                        >
                          Terima Tunai
                        </button>
                        <button
                          type="button"
                          onClick={() => handleRejectQrOrder(order)}
                          className="rounded-lg border border-red-300 px-2.5 py-2 text-xs font-semibold text-red-600 dark:border-red-800 whitespace-nowrap shrink-0"
                        >
                          Tolak
                        </button>
                      </div>
                    )}

                    {/* Update Status Alur Pesanan */}
                    <div className="flex flex-wrap gap-1.5">
                      {order.status === 'new' && (
                        <button
                          type="button"
                          onClick={() => handleUpdateOrderStatus(order, 'preparing')}
                          className="flex-1 rounded-lg bg-zinc-900 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                        >
                          Proses ke Dapur
                        </button>
                      )}
                      {order.status === 'preparing' && (
                        <button
                          type="button"
                          onClick={() => handleUpdateOrderStatus(order, 'ready')}
                          className="flex-1 rounded-lg bg-zinc-900 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                        >
                          Tandai Siap Saji
                        </button>
                      )}
                      {order.status === 'ready' && (
                        <button
                          type="button"
                          onClick={() => handleUpdateOrderStatus(order, 'completed')}
                          className="flex-1 rounded-lg bg-zinc-900 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                        >
                          Selesaikan Order
                        </button>
                      )}
                      {order.status !== 'completed' && order.status !== 'cancelled' && (
                        <button
                          type="button"
                          onClick={() => handleUpdateOrderStatus(order, 'cancelled')}
                          className="rounded-lg border border-zinc-300 px-3 py-2 text-xs font-medium text-red-600 dark:border-zinc-700"
                        >
                          Batalkan
                        </button>
                      )}
                    </div>

                    {/* Cetak Tiket Dapur & Struk Kasir */}
                    <div className="flex items-center gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => handlePrintOrder(order, 'kitchen')}
                        className="flex-1 rounded-lg border border-zinc-200 py-1.5 text-[11px] font-medium hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-800"
                      >
                        Cetak Dapur
                      </button>
                      <button
                        type="button"
                        onClick={() => setReceiptModalOrder(order)}
                        className="flex-1 rounded-lg border border-zinc-200 py-1.5 text-[11px] font-medium hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-800"
                      >
                        Detail Struk
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        );
      }

      // ======================================================================
      // 5. HISTORY TRANSAKSI
      // ======================================================================
      case 'history':
        return (
          <div className="space-y-4">
            <div className="flex flex-wrap items-end justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex flex-wrap items-end gap-3">
                <div>
                  <label className="block text-xs text-zinc-500">Dari Tanggal</label>
                  <input
                    type="date"
                    value={historyDateFrom}
                    onChange={(e) => setHistoryDateFrom(e.target.value)}
                    className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Sampai Tanggal</label>
                  <input
                    type="date"
                    value={historyDateTo}
                    onChange={(e) => setHistoryDateTo(e.target.value)}
                    className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  const rows = [
                    ['Riwayat Transaksi Kereta Malam Cafe'],
                    ['No Order', 'Waktu', 'Sumber', 'Meja', 'Pelanggan', 'Status', 'Metode', 'Total (Rp)'],
                    ...orders.map((o) => [
                      `#${o.order_no}`,
                      o.created_at,
                      o.source.toUpperCase(),
                      o.restaurant_tables?.name ?? 'Takeaway',
                      o.customer_name ?? '-',
                      STATUS_LABELS[o.status],
                      o.payment_method ?? '-',
                      o.total,
                    ]),
                  ];
                  downloadCsvFile(`history-transaksi-${historyDateFrom}-${historyDateTo}.csv`, rows);
                  showNotice('success', 'File CSV Riwayat Transaksi berhasil diunduh.');
                }}
                className="inline-flex items-center gap-2 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900 whitespace-nowrap shrink-0"
              >
                <Download className="size-3.5" />
                Ekspor CSV History
              </button>
            </div>

            <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-500 dark:border-zinc-800 dark:bg-zinc-950">
                  <tr>
                    <th className="px-4 py-3">Order</th>
                    <th className="px-4 py-3">Waktu</th>
                    <th className="px-4 py-3">Meja / Kanal</th>
                    <th className="px-4 py-3">Pelanggan</th>
                    <th className="px-4 py-3">Metode</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Total</th>
                    <th className="px-4 py-3 text-right">Aksi</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                  {orders.map((o) => (
                    <tr key={o.id} className="hover:bg-zinc-50/70 dark:hover:bg-zinc-800/40">
                      <td className="num px-4 py-3 font-bold">#{o.order_no}</td>
                      <td className="num px-4 py-3 text-zinc-500">
                        {new Date(o.created_at).toLocaleString('id-ID')}
                      </td>
                      <td className="px-4 py-3">
                        {o.restaurant_tables?.name ?? 'Takeaway'} · {o.source.toUpperCase()}
                      </td>
                      <td className="px-4 py-3">{o.customer_name || '-'}</td>
                      <td className="px-4 py-3 uppercase">{o.payment_method || '-'}</td>
                      <td className="px-4 py-3 font-medium">{STATUS_LABELS[o.status]}</td>
                      <td className="num px-4 py-3 text-right font-bold">{formatRupiah(o.total)}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => setReceiptModalOrder(o)}
                          className="rounded border border-zinc-300 px-2.5 py-1 text-xs font-medium hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                        >
                          Lihat Struk
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );

      // ======================================================================
      // 6. OPENING & CLOSING (SHIFT KASIR)
      // ======================================================================
      case 'shift-kasir': {
        const activeShift = shifts.find((s) => s.status === 'open');
        const cashSalesDuringShift = paidOrders
          .filter((o) => o.payment_method === 'cash')
          .reduce((s, o) => s + o.total, 0);

        return (
          <div className="mx-auto max-w-4xl space-y-6">
            {activeShift ? (
              <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
                <h3 className="text-base font-bold">Shift Kasir Sedang Berjalan (OPEN)</h3>
                <p className="mt-1 text-xs text-zinc-500">
                  Kasir: {activeShift.staff_name} · Dibuka pada{' '}
                  {new Date(activeShift.opened_at).toLocaleString('id-ID')}
                </p>

                <div className="mt-4 grid gap-4 sm:grid-cols-3">
                  <div className="rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
                    <p className="text-xs text-zinc-500">Modal Kas Awal (Opening)</p>
                    <p className="num mt-1 text-lg font-bold">{formatRupiah(activeShift.opening_cash)}</p>
                  </div>
                  <div className="rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
                    <p className="text-xs text-zinc-500">Penjualan Tunai Shift Ini</p>
                    <p className="num mt-1 text-lg font-bold">{formatRupiah(cashSalesDuringShift)}</p>
                  </div>
                  <div className="rounded-lg border border-zinc-200 p-4 dark:border-zinc-800">
                    <p className="text-xs text-zinc-500">Ekspektasi Kas Laci</p>
                    <p className="num mt-1 text-lg font-bold">
                      {formatRupiah(activeShift.opening_cash + cashSalesDuringShift)}
                    </p>
                  </div>
                </div>

                <div className="mt-6 space-y-3 border-t border-zinc-100 pt-4 dark:border-zinc-800">
                  <h4 className="text-sm font-bold">Form Closing Shift Kasir</h4>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <label className="block text-xs text-zinc-500">Hitung Fisik Kas Akhir (Rp)</label>
                      <input
                        type="number"
                        value={closingCashInput}
                        onChange={(e) => setClosingCashInput(e.target.value)}
                        placeholder={String(activeShift.opening_cash + cashSalesDuringShift)}
                        className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-zinc-500">Catatan Closing</label>
                      <input
                        type="text"
                        value={closingShiftNotes}
                        onChange={(e) => setClosingShiftNotes(e.target.value)}
                        placeholder="Catatan setoran / selisih..."
                        className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      />
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      const actual = Number(closingCashInput) || activeShift.opening_cash + cashSalesDuringShift;
                      const expected = activeShift.opening_cash + cashSalesDuringShift;
                      setShifts((prev) =>
                        prev.map((s) =>
                          s.id === activeShift.id
                            ? {
                                ...s,
                                status: 'closed',
                                closing_cash: actual,
                                expected_cash: expected,
                                notes: closingShiftNotes || s.notes,
                                closed_at: new Date().toISOString(),
                              }
                            : s
                        )
                      );
                      appendAuditLog('shift.close', 'staff_shifts', activeShift.id, {
                        closing_cash: actual,
                        expected_cash: expected,
                      });
                      setClosingCashInput('');
                      setClosingShiftNotes('');
                      showNotice('success', 'Shift kasir berhasil ditutup dan dikirim ke Audit Shift.');
                    }}
                    className="rounded-xl bg-zinc-900 px-5 py-2.5 text-xs font-bold text-white dark:bg-zinc-100 dark:text-zinc-900"
                  >
                    Tutup Shift Sekarang (Closing)
                  </button>
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
                <h3 className="text-base font-bold">Buka Shift Kasir Baru (Opening)</h3>
                <p className="mt-1 text-xs text-zinc-500">
                  Masukkan modal uang kembalian awal di laci kasir sebelum memulai transaksi.
                </p>
                <div className="mt-4 flex flex-wrap items-end gap-3">
                  <div>
                    <label className="block text-xs text-zinc-500">Modal Kas Awal (Rp)</label>
                    <input
                      type="number"
                      value={openingCashInput}
                      onChange={(e) => setOpeningCashInput(e.target.value)}
                      className="num mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      const openAmt = Math.max(0, Number(openingCashInput) || 0);
                      const newShift: StaffShift = {
                        id: `shf-${Date.now()}`,
                        staff_user_id: currentStaff.user_id,
                        staff_name: currentStaff.full_name,
                        opening_cash: openAmt,
                        closing_cash: null,
                        expected_cash: openAmt,
                        status: 'open',
                        notes: 'Shift dibuka dari aplikasi Android',
                        audit_verified: false,
                        opened_at: new Date().toISOString(),
                        closed_at: null,
                      };
                      setShifts((prev) => [newShift, ...prev]);
                      appendAuditLog('shift.open', 'staff_shifts', newShift.id, { opening_cash: openAmt });
                      showNotice('success', `Shift baru dibuka dengan modal awal ${formatRupiah(openAmt)}.`);
                    }}
                    className="rounded-xl bg-zinc-900 px-5 py-2.5 text-xs font-bold text-white dark:bg-zinc-100 dark:text-zinc-900"
                  >
                    Buka Shift Kasir
                  </button>
                </div>
              </div>
            )}
          </div>
        );
      }

      // ======================================================================
      // 7. AUDIT SHIFT
      // ======================================================================
      case 'audit-shift':
        return (
          <div className="space-y-4">
            <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">Rekonsiliasi &amp; Audit Shift Kasir</h3>
              <p className="mt-1 text-xs text-zinc-500">
                Verifikasi kesesuaian kas fisik laci dengan ekspektasi transaksi tunai pada tabel <code className="font-mono">staff_shifts</code>.
              </p>
              <div className="mt-4 overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-500 dark:border-zinc-800 dark:bg-zinc-950">
                    <tr>
                      <th className="px-4 py-3">Kasir</th>
                      <th className="px-4 py-3">Waktu Buka</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3 text-right">Modal Awal</th>
                      <th className="px-4 py-3 text-right">Ekspektasi</th>
                      <th className="px-4 py-3 text-right">Kas Fisik Akhir</th>
                      <th className="px-4 py-3 text-right">Selisih</th>
                      <th className="px-4 py-3 text-right">Verifikasi</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                    {shifts.map((s) => {
                      const diff =
                        s.closing_cash != null && s.expected_cash != null
                          ? s.closing_cash - s.expected_cash
                          : 0;
                      return (
                        <tr key={s.id}>
                          <td className="px-4 py-3 font-semibold">{s.staff_name}</td>
                          <td className="num px-4 py-3 text-zinc-500">
                            {new Date(s.opened_at).toLocaleString('id-ID')}
                          </td>
                          <td className="px-4 py-3 uppercase">{s.status}</td>
                          <td className="num px-4 py-3 text-right">{formatRupiah(s.opening_cash)}</td>
                          <td className="num px-4 py-3 text-right">
                            {s.expected_cash != null ? formatRupiah(s.expected_cash) : '-'}
                          </td>
                          <td className="num px-4 py-3 text-right">
                            {s.closing_cash != null ? formatRupiah(s.closing_cash) : '-'}
                          </td>
                          <td className="num px-4 py-3 text-right font-bold">
                            {s.status === 'closed' ? formatRupiah(diff) : '-'}
                          </td>
                          <td className="px-4 py-3 text-right">
                            {s.audit_verified ? (
                              <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                                Terverifikasi
                              </span>
                            ) : (
                              <button
                                type="button"
                                onClick={() => {
                                  setShifts((prev) =>
                                    prev.map((item) =>
                                      item.id === s.id
                                        ? { ...item, audit_verified: true, audit_note: 'Disetujui Audit' }
                                        : item
                                    )
                                  );
                                  appendAuditLog('shift.audit_verify', 'staff_shifts', s.id, { verified: true });
                                  showNotice('success', `Shift ${s.staff_name} berhasil diverifikasi.`);
                                }}
                                className="rounded bg-zinc-900 px-2.5 py-1 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                              >
                                Verifikasi Audit
                              </button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 8. LOG AKTIVITAS (AUDIT LOGS)
      // ======================================================================
      case 'activity-log':
        return (
          <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="text-base font-bold">Log Aktivitas Operasional (audit_logs)</h3>
            <div className="mt-4 divide-y divide-zinc-100 dark:divide-zinc-800">
              {auditLogs.map((log) => (
                <div key={log.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-xs">
                  <div>
                    <p className="font-mono font-bold">{log.action}</p>
                    <p className="text-zinc-500">
                      Aktor: {log.actor_name} · Entitas: {log.entity_type} ({log.entity_id})
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-mono text-[11px] text-zinc-600 dark:text-zinc-400">{log.details}</p>
                    <p className="num text-[11px] text-zinc-400">
                      {new Date(log.created_at).toLocaleString('id-ID')}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        );

      // ======================================================================
      // 9. PRODUK & STOK
      // ======================================================================
      case 'produk': {
        const displayedProducts = products.filter((p) => {
          if (productStockFilter === 'LOW') return p.stock > 0 && p.stock <= p.low_stock_threshold;
          if (productStockFilter === 'OUT') return p.stock <= 0;
          return true;
        });

        return (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex flex-wrap items-center gap-2">
                {(['ALL', 'LOW', 'OUT'] as const).map((f) => (
                  <button
                    key={f}
                    type="button"
                    onClick={() => setProductStockFilter(f)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-medium whitespace-nowrap shrink-0 ${
                      productStockFilter === f
                        ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                        : 'bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
                    }`}
                  >
                    {f === 'ALL'
                      ? `Semua Produk (${products.length})`
                      : f === 'LOW'
                        ? `Stok Menipis (${products.filter((x) => x.stock > 0 && x.stock <= x.low_stock_threshold).length})`
                        : `Stok Habis (${products.filter((x) => x.stock <= 0).length})`}
                  </button>
                ))}
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="Kategori baru..."
                  className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                />
                <button
                  type="button"
                  onClick={() => {
                    const trimmed = newCategoryName.trim();
                    if (!trimmed) return;
                    if (backendMode === 'LIVE_SUPABASE') {
                      showNotice('error', 'Tambah kategori belum terhubung ke penyimpanan Supabase. Tidak ada perubahan disimpan.');
                      return;
                    }
                    setCategories((prev) => [
                      ...prev,
                      { id: `cat-${Date.now()}`, name: trimmed, sort_order: prev.length + 1 },
                    ]);
                    setNewCategoryName('');
                    showNotice('success', `Kategori "${trimmed}" ditambahkan.`);
                  }}
                  className="rounded-lg border border-zinc-300 px-3 py-1.5 text-xs font-semibold dark:border-zinc-700"
                >
                  + Kategori
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditingProduct({
                      id: '',
                      name: '',
                      description: '',
                      price: 25000,
                      cost_price: 10000,
                      image_url: null,
                      category_id: categories[0]?.id ?? null,
                      stock: 20,
                      low_stock_threshold: 5,
                      show_on_menu: true,
                      is_active: true,
                    });
                    setIsProductModalOpen(true);
                  }}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-900 px-4 py-1.5 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                >
                  <Plus className="size-3.5" />
                  Tambah Produk
                </button>
              </div>
            </div>

            <div className="overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-500 dark:border-zinc-800 dark:bg-zinc-950">
                  <tr>
                    <th className="px-4 py-3">Nama Produk</th>
                    <th className="px-4 py-3">Kategori</th>
                    <th className="px-4 py-3 text-right">Harga Jual</th>
                    <th className="px-4 py-3 text-right">HPP / Modal</th>
                    <th className="px-4 py-3 text-right">Stok</th>
                    <th className="px-4 py-3">QR Menu</th>
                    <th className="px-4 py-3 text-right">Aksi</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                  {displayedProducts.map((p) => {
                    const catName = categories.find((c) => c.id === p.category_id)?.name ?? '-';
                    return (
                      <tr key={p.id}>
                        <td className="px-4 py-3 font-semibold">{p.name}</td>
                        <td className="px-4 py-3 text-zinc-500">{catName}</td>
                        <td className="num px-4 py-3 text-right font-bold">{formatRupiah(p.price)}</td>
                        <td className="num px-4 py-3 text-right text-zinc-500">
                          {formatRupiah(p.cost_price)}
                        </td>
                        <td className="num px-4 py-3 text-right font-bold">
                          <span className={p.stock <= p.low_stock_threshold ? 'text-red-600 dark:text-red-400' : ''}>
                            {p.stock}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <input
                            type="checkbox"
                            checked={p.show_on_menu}
                            onChange={(e) => {
                              const checked = e.target.checked;
                              if (backendMode === 'LIVE_SUPABASE') {
                                showNotice('error', 'Ubah status menu belum terhubung ke Supabase. Tidak ada perubahan disimpan.');
                                return;
                              }
                              setProducts((prev) =>
                                prev.map((x) => (x.id === p.id ? { ...x, show_on_menu: checked } : x))
                              );
                            }}
                          />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => {
                                setEditingProduct({ ...p });
                                setIsProductModalOpen(true);
                              }}
                              className="rounded border border-zinc-300 p-1.5 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                              title="Edit Produk"
                            >
                              <Edit3 className="size-3.5" />
                            </button>
                            <button
                              type="button"
                              onClick={() => setDeletingProduct(p)}
                              className="rounded border border-red-200 p-1.5 text-red-600 hover:bg-red-50 dark:border-red-900/50"
                              title="Hapus Produk"
                            >
                              <Trash2 className="size-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        );
      }

      // ======================================================================
      // 10. MEJA & QR MENU
      // ======================================================================
      case 'meja': {
        const targetQrTable = selectedQrTable || tables[0];
        const validQrUrl = `${outletSettings.public_web_base_url.replace(/\/$/, '')}/menu?table=${targetQrTable?.code || 'M1'}`;
        const qrMatrix = generateDeterministicQrMatrix(validQrUrl);

        return (
          <div className="grid gap-6 lg:grid-cols-12">
            <div className="space-y-4 lg:col-span-7">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <h3 className="text-base font-bold">Tambah Meja Baru</h3>
                <div className="mt-3 flex flex-wrap items-end gap-3">
                  <div>
                    <label className="block text-xs text-zinc-500">Nama Meja</label>
                    <input
                      type="text"
                      value={newTableName}
                      onChange={(e) => setNewTableName(e.target.value)}
                      placeholder="Contoh: Meja 6 VIP"
                      className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-zinc-500">Kode Unik</label>
                    <input
                      type="text"
                      value={newTableCode}
                      onChange={(e) => setNewTableCode(e.target.value)}
                      placeholder="Contoh: M6"
                      className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      if (!newTableName.trim() || !newTableCode.trim()) return;
                      if (backendMode === 'LIVE_SUPABASE') {
                        showNotice('error', 'Tambah meja belum terhubung ke penyimpanan Supabase. Tidak ada perubahan disimpan.');
                        return;
                      }
                      const created: RestaurantTable = {
                        id: `tbl-${Date.now()}`,
                        name: newTableName.trim(),
                        code: newTableCode.trim().toUpperCase(),
                      };
                      setTables((prev) => [...prev, created]);
                      setSelectedQrTable(created);
                      setNewTableName('');
                      setNewTableCode('');
                      showNotice('success', `Meja ${created.name} (${created.code}) berhasil ditambahkan.`);
                    }}
                    className="rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                  >
                    + Simpan Meja
                  </button>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                {tables.map((t) => {
                  const activeOrderOnTable = orders.find(
                    (o) =>
                      (o.table_id === t.id || o.restaurant_tables?.name === t.name) &&
                      (o.status === 'new' || o.status === 'preparing' || o.status === 'ready')
                  );
                  return (
                    <div
                      key={t.id}
                      onClick={() => setSelectedQrTable(t)}
                      className={`cursor-pointer rounded-xl border p-4 transition-all ${
                        targetQrTable?.id === t.id
                          ? 'border-zinc-900 bg-white dark:border-zinc-100 dark:bg-zinc-900'
                          : 'border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <h4 className="text-sm font-bold">{t.name}</h4>
                        <span className="font-mono text-xs font-bold text-zinc-500">{t.code}</span>
                      </div>
                      <p className="mt-2 text-xs text-zinc-500">
                        Status:{' '}
                        <strong className={activeOrderOnTable ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'}>
                          {activeOrderOnTable ? `Terisi (Order #${activeOrderOnTable.order_no})` : 'Tersedia'}
                        </strong>
                      </p>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Panel QR Code Valid */}
            <div className="lg:col-span-5">
              <div className="rounded-xl border border-zinc-200 bg-white p-6 text-center dark:border-zinc-800 dark:bg-zinc-900">
                <p className="text-xs font-semibold tracking-wider text-zinc-500 uppercase">
                  QR Ordering Pelanggan
                </p>
                <h3 className="mt-1 text-lg font-bold">
                  {targetQrTable?.name} ({targetQrTable?.code})
                </h3>

                <div className="mx-auto my-5 inline-block rounded-2xl border border-zinc-200 bg-white p-4 shadow-xs">
                  <svg
                    viewBox={`0 0 ${qrMatrix.length} ${qrMatrix.length}`}
                    className="size-48"
                    shapeRendering="crispEdges"
                  >
                    {qrMatrix.map((row, rIdx) =>
                      row.map((cell, cIdx) =>
                        cell ? (
                          <rect
                            key={`${rIdx}-${cIdx}`}
                            x={cIdx}
                            y={rIdx}
                            width={1}
                            height={1}
                            fill="#09090B"
                          />
                        ) : null
                      )
                    )}
                  </svg>
                </div>

                <p className="font-mono text-xs break-all text-zinc-600 dark:text-zinc-400">
                  {validQrUrl}
                </p>

                <div className="mt-4 flex justify-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      navigator.clipboard.writeText(validQrUrl);
                      showNotice('success', `URL QR Meja disalin: ${validQrUrl}`);
                    }}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                  >
                    <Copy className="size-3.5" />
                    Salin URL QR Valid
                  </button>
                </div>
              </div>
            </div>
          </div>
        );
      }

      // ======================================================================
      // 11. INVENTORY
      // ======================================================================
      case 'inventory':
        return (
          <div className="space-y-6">
            <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">Catat Mutasi Stok / Penyesuaian Opname</h3>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const prod = products.find((p) => p.id === invProductId);
                  const qtyNum = Number(invQty);
                  if (!prod || !Number.isFinite(qtyNum) || qtyNum === 0) return;
                  if (!invReason.trim()) {
                    showNotice('error', 'Alasan mutasi stok wajib diisi.');
                    return;
                  }
                  if (backendMode === 'LIVE_SUPABASE') {
                    showNotice('error', 'Mutasi stok LIVE belum aman: harus lewat RPC server yang memvalidasi outlet dan stok. Tidak ada perubahan disimpan.');
                    return;
                  }
                  const signedDelta = invType === 'out' ? -Math.abs(qtyNum) : qtyNum;
                  setProducts((prev) =>
                    prev.map((p) =>
                      p.id === prod.id ? { ...p, stock: Math.max(0, p.stock + signedDelta) } : p
                    )
                  );
                  const movement: InventoryMovement = {
                    id: `inv-${Date.now()}`,
                    product_id: prod.id,
                    product_name: prod.name,
                    warehouse_name: 'Gudang Utama Bar',
                    type: invType,
                    quantity: signedDelta,
                    reason: invReason.trim(),
                    actor_name: currentStaff.full_name,
                    created_at: new Date().toISOString(),
                  };
                  setInventoryMovements((prev) => [movement, ...prev]);
                  setInvReason('');
                  showNotice('success', `Mutasi stok ${prod.name} berhasil dicatat.`);
                }}
                className="mt-4 grid gap-3 sm:grid-cols-4"
              >
                <div>
                  <label className="block text-xs text-zinc-500">Produk</label>
                  <select
                    value={invProductId}
                    onChange={(e) => setInvProductId(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  >
                    {products.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} (Stok: {p.stock})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Tipe Mutasi</label>
                  <select
                    value={invType}
                    onChange={(e) => setInvType(e.target.value as 'in' | 'out' | 'adjustment')}
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  >
                    <option value="in">Stok Masuk (Restock)</option>
                    <option value="out">Stok Keluar (Rusak / Waste)</option>
                    <option value="adjustment">Penyesuaian Opname</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Jumlah Unit</label>
                  <input
                    type="number"
                    value={invQty}
                    onChange={(e) => setInvQty(e.target.value)}
                    className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Alasan / Keterangan</label>
                  <input
                    type="text"
                    value={invReason}
                    onChange={(e) => setInvReason(e.target.value)}
                    placeholder="Contoh: Restock supplier pagi"
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div className="sm:col-span-4">
                  <button
                    type="submit"
                    className="rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                  >
                    Simpan Mutasi Stok
                  </button>
                </div>
              </form>
            </div>

            <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">Riwayat Perubahan Stok (inventory_movements)</h3>
              <div className="mt-3 divide-y divide-zinc-100 dark:divide-zinc-800">
                {inventoryMovements.map((m) => (
                  <div key={m.id} className="flex items-center justify-between py-3 text-xs">
                    <div>
                      <p className="font-semibold">
                        {m.product_name} · <span className="uppercase">{m.type}</span>
                      </p>
                      <p className="text-zinc-500">
                        {m.reason} · Oleh {m.actor_name}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="num font-bold">
                        {m.quantity > 0 ? `+${m.quantity}` : m.quantity} unit
                      </p>
                      <p className="num text-[11px] text-zinc-400">
                        {new Date(m.created_at).toLocaleString('id-ID')}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 12. PROMOSI & DISKON
      // ======================================================================
      case 'promosi':
        return (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div><h2 className="text-xl font-bold">Promosi & Diskon</h2><p className="mt-1 text-sm text-zinc-500">Atur diskon, voucher, bundel, dan campaign sesuai strategi outlet.</p></div>
              <button type="button" onClick={() => setPromoModalOpen(true)} className="rounded-xl bg-zinc-900 px-4 py-2.5 text-sm font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">+ Buat Promosi</button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {[
                {key:'active',label:'Promo Aktif',count:promotions.filter(p=>p.active).length,desc:'Promosi yang sedang berlaku'},
                {key:'voucher',label:'Voucher',count:promotions.filter(p=>p.campaign_type==='voucher').length,desc:'Diskon yang bisa dipilih'},
                {key:'bundle',label:'Bundel',count:promotions.filter(p=>p.campaign_type==='bundle').length,desc:'Paket produk'},
                {key:'campaign',label:'Campaign',count:promotions.filter(p=>p.campaign_type==='campaign').length,desc:'Program promosi khusus'},
              ].map(card=><button type="button" key={card.key} onClick={()=>setPromoTab(card.key as typeof promoTab)} className={`rounded-xl border p-4 text-left transition ${promoTab===card.key?'border-zinc-900 bg-zinc-100 dark:border-zinc-100 dark:bg-zinc-800':'border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900'}`}><div className="text-sm text-zinc-500">{card.label}</div><div className="mt-2 text-2xl font-bold">{card.count}</div><div className="mt-1 text-xs text-zinc-500">{card.desc}</div></button>)}
            </div>
            <div className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex flex-wrap gap-2">{([{key:'all',label:'Semua Promosi'},{key:'active',label:'Promo Aktif'},{key:'voucher',label:'Voucher'},{key:'bundle',label:'Bundel'},{key:'campaign',label:'Campaign'}] as const).map(tab=><button type="button" key={tab.key} onClick={()=>setPromoTab(tab.key)} className={`rounded-lg px-3 py-2 text-xs font-semibold ${promoTab===tab.key?'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900':'bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300'}`}>{tab.label}</button>)}</div>
              <div className="mt-4 space-y-3">
                {promotions.filter(p=>promoTab==='all'||(promoTab==='active'?p.active:p.campaign_type===promoTab)).map(p=><div key={p.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 p-4 dark:border-zinc-800">
                  <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{p.name}</h3><span className="rounded-full bg-zinc-100 px-2 py-1 text-[10px] dark:bg-zinc-800">{({promotion:'Promosi',voucher:'Voucher',bundle:'Bundel',campaign:'Campaign'} as const)[p.campaign_type||'promotion']}</span><span className={`rounded-full px-2 py-1 text-[10px] ${p.active?'bg-emerald-100 text-emerald-800':'bg-zinc-100 text-zinc-500'}`}>{p.active?'Aktif':'Nonaktif'}</span></div>
                  <p className="mt-1 text-sm text-zinc-500">{p.type==='percentage'?p.value+'%':formatRupiah(p.value)} · Minimum belanja {formatRupiah(p.min_purchase)}{p.scope==='product'? ` · ${products.find(x=>x.id===p.product_id)?.name||'Produk tertentu'} minimal ${p.min_quantity} item`:' · Berlaku untuk seluruh belanja'}</p>
                  <p className="mt-1 text-xs text-zinc-500">Bisa dipilih customer: {p.allow_customer===false?'Tidak':'Ya'} · Bisa dipilih kasir: {p.allow_cashier===false?'Tidak':'Ya'}</p></div>
                  <button type="button" onClick={async()=>{const nextActive=!p.active;if(backendMode==='LIVE_SUPABASE'){const client=getVerifiedSupabaseClient(supabaseConfig);if(!client)return;const {data,error}=await client.from('promotions').update({active:nextActive}).eq('id',p.id).select('*').single();if(error){showNotice('error',`Gagal mengubah status promo: ${error.message}`);return;}setPromotions(prev=>prev.map(x=>x.id===p.id?({...x,...data} as Promotion):x));showNotice('success',`Promo ${p.name} ${nextActive?'diaktifkan':'dinonaktifkan'}.`);return;}setPromotions(prev=>prev.map(x=>x.id===p.id?{...x,active:nextActive}:x));showNotice('success',`Promo ${p.name} ${nextActive?'diaktifkan':'dinonaktifkan'}.`);}} className="rounded-lg border border-zinc-300 px-3 py-2 text-xs font-semibold dark:border-zinc-700">{p.active?'Nonaktifkan':'Aktifkan'}</button>
                </div>)}
                {promotions.filter(p=>promoTab==='all'||(promoTab==='active'?p.active:p.campaign_type===promoTab)).length===0&&<div className="rounded-xl border border-dashed border-zinc-300 p-8 text-center text-sm text-zinc-500">Belum ada promosi di kategori ini. Klik “Buat Promosi” untuk mulai.</div>}
              </div>
            </div>
            {promoModalOpen&&<div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-3" onMouseDown={e=>{if(e.target===e.currentTarget)setPromoModalOpen(false);}}><div className="max-h-[92vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl dark:bg-zinc-900"><div className="flex items-center justify-between"><div><h3 className="text-lg font-bold">Buat Promosi Baru</h3><p className="text-xs text-zinc-500">Atur syarat diskon sebelum disimpan.</p></div><button type="button" onClick={()=>setPromoModalOpen(false)} className="rounded-lg px-3 py-2 text-zinc-500">✕</button></div>
              <form className="mt-5 space-y-4" onSubmit={async e=>{e.preventDefault();if(!promoName.trim()){showNotice('error','Nama promosi wajib diisi.');return;}if(promoScope==='product'&&!promoProductId){showNotice('error','Pilih produk yang mendapat diskon.');return;}const value=Math.max(0,Number(promoValue)||0);if(value<=0||(promoType==='percentage'&&value>100)){showNotice('error','Nilai diskon harus lebih dari 0 dan persentase maksimal 100%.');return;}const payload={name:promoName.trim(),type:promoType,value,min_purchase:Math.max(0,Number(promoMinPurchase)||0),min_quantity:Math.max(1,Number(promoMinQuantity)||1),scope:promoScope,product_id:promoScope==='product'?promoProductId:null,active:true,campaign_type:promoKind,allow_customer:promoAllowCustomer,allow_cashier:promoAllowCashier};if(backendMode==='LIVE_SUPABASE'){const client=getVerifiedSupabaseClient(supabaseConfig);if(!client)return;const {data,error}=await client.from('promotions').insert(payload).select('*').single();if(error){showNotice('error',`Gagal menyimpan promosi: ${error.message}. Pastikan SQL supabase/promotions-enhancements.sql sudah dijalankan.`);return;}const created=data as Promotion;setPromotions(prev=>[created,...prev]);setPromoName('');setPromoModalOpen(false);setPromoTab('all');showNotice('success',`Promosi "${created.name}" tersimpan di Supabase.`);return;}const created:Promotion={id:`promo-${Date.now()}`,...payload};setPromotions(prev=>[created,...prev]);setPromoName('');setPromoModalOpen(false);setPromoTab('all');showNotice('success',`Promosi "${created.name}" berhasil dibuat.`);}}>
              <div><label className="mb-1 block text-xs font-medium text-zinc-500">Nama Promo</label><input required value={promoName} onChange={e=>setPromoName(e.target.value)} placeholder="Contoh: Belanja 500 ribu diskon 20 ribu" className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"/></div>
              <div className="grid gap-3 sm:grid-cols-2"><div><label className="mb-1 block text-xs font-medium text-zinc-500">Jenis Promo</label><select value={promoKind} onChange={e=>setPromoKind(e.target.value as typeof promoKind)} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"><option value="promotion">Promosi / Diskon</option><option value="voucher">Voucher</option><option value="bundle">Bundel</option><option value="campaign">Campaign</option></select></div><div><label className="mb-1 block text-xs font-medium text-zinc-500">Tipe Diskon</label><select value={promoType} onChange={e=>setPromoType(e.target.value as 'percentage'|'fixed')} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"><option value="percentage">Persentase (%)</option><option value="fixed">Nominal (Rp)</option></select></div></div>
              <div className="grid gap-3 sm:grid-cols-2"><div><label className="mb-1 block text-xs font-medium text-zinc-500">Nilai Diskon {promoType==='percentage'?'(%)':'(Rp)'}</label><input required type="number" min="0" max={promoType==='percentage'?100:undefined} value={promoValue} onChange={e=>setPromoValue(e.target.value)} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"/></div><div><label className="mb-1 block text-xs font-medium text-zinc-500">Minimum Total Belanja (Rp)</label><input type="number" min="0" value={promoMinPurchase} onChange={e=>setPromoMinPurchase(e.target.value)} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"/></div></div>
              <div><label className="mb-1 block text-xs font-medium text-zinc-500">Berlaku Untuk</label><div className="grid gap-2 sm:grid-cols-2"><label className="flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm dark:border-zinc-700"><input type="radio" checked={promoScope==='order'} onChange={()=>setPromoScope('order')} name="promoScope"/> Seluruh belanja</label><label className="flex cursor-pointer items-center gap-2 rounded-lg border p-3 text-sm dark:border-zinc-700"><input type="radio" checked={promoScope==='product'} onChange={()=>setPromoScope('product')} name="promoScope"/> Produk tertentu</label></div></div>
              {promoScope==='product'&&<div className="grid gap-3 sm:grid-cols-2"><div><label className="mb-1 block text-xs font-medium text-zinc-500">Pilih Produk</label><select required value={promoProductId} onChange={e=>setPromoProductId(e.target.value)} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"><option value="">Pilih produk…</option>{products.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></div><div><label className="mb-1 block text-xs font-medium text-zinc-500">Minimal Jumlah Produk</label><input type="number" min="1" required value={promoMinQuantity} onChange={e=>setPromoMinQuantity(e.target.value)} className="w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2.5 text-sm dark:border-zinc-700 dark:bg-zinc-950"/></div></div>}
              <div className="rounded-xl bg-zinc-50 p-3 text-sm dark:bg-zinc-800"><div className="font-semibold">Cara penggunaan</div><p className="mt-1 text-xs text-zinc-500">Pelanggan atau kasir dapat memilih promo secara manual. Promo tidak otomatis dipaksakan ke transaksi.</p><div className="mt-3 flex flex-wrap gap-4"><label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={promoAllowCustomer} onChange={e=>setPromoAllowCustomer(e.target.checked)}/> Bisa dipilih customer</label><label className="flex items-center gap-2 text-xs"><input type="checkbox" checked={promoAllowCashier} onChange={e=>setPromoAllowCashier(e.target.checked)}/> Bisa dipilih kasir</label></div></div>
              <div className="flex justify-end gap-2 border-t pt-4 dark:border-zinc-800"><button type="button" onClick={()=>setPromoModalOpen(false)} className="rounded-lg border px-4 py-2 text-sm dark:border-zinc-700">Batal</button><button type="submit" className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900">Simpan Promo</button></div>
              </form></div></div>}
          </div>
        );

      // 13. PENGELUARAN & LABA RUGI (KEUANGAN)
      // ======================================================================
      case 'keuangan':
        return (
          <div className="space-y-4">
            <div className="flex flex-wrap items-end justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex flex-wrap items-end gap-3">
                <div>
                  <label className="block text-xs text-zinc-500">Dari Tanggal</label>
                  <input
                    type="date"
                    value={financeFrom}
                    onChange={(e) => setFinanceFrom(e.target.value)}
                    className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Sampai Tanggal</label>
                  <input
                    type="date"
                    value={financeTo}
                    onChange={(e) => setFinanceTo(e.target.value)}
                    className="mt-1 rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-1.5 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  const rows = [
                    ['Laporan Keuangan & Laba Rugi Kereta Malam Cafe'],
                    ['Periode', financeFrom, 'sampai', financeTo],
                    ['Penjualan bersih', profitLossSummary.netSales],
                    ['HPP / COGS', profitLossSummary.cogs],
                    ['Total pengeluaran', profitLossSummary.totalExpenses],
                    ['Laba / (Rugi)', profitLossSummary.netProfit],
                    [],
                    ['Tanggal', 'Kategori', 'Keterangan', 'Metode pembayaran', 'Nominal (Rp)'],
                    ...profitLossSummary.filteredExp.map((e) => [
                      e.expense_date,
                      e.category,
                      e.description,
                      e.payment_method,
                      e.amount,
                    ]),
                  ];
                  downloadCsvFile(`laporan-keuangan-${financeFrom}-${financeTo}.csv`, rows);
                  showNotice('success', 'CSV Laporan Keuangan & Laba Rugi berhasil diunduh.');
                }}
                className="inline-flex items-center gap-2 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                <Download className="size-3.5" />
                Ekspor CSV Keuangan
              </button>
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setFinanceTab('expenses')}
                className={`rounded-lg px-4 py-2 text-xs font-semibold ${
                  financeTab === 'expenses'
                    ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900'
                }`}
              >
                Kas Keluar
              </button>
              <button
                type="button"
                onClick={() => setFinanceTab('pnl')}
                className={`rounded-lg px-4 py-2 text-xs font-semibold ${
                  financeTab === 'pnl'
                    ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900'
                }`}
              >
                Laporan Laba Rugi
              </button>
            </div>

            {financeTab === 'expenses' ? (
              <div className="space-y-4">
                <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                  <h3 className="text-base font-bold">Tambah Pengeluaran</h3>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      const amt = Number(expenseAmount);
                      if (!Number.isFinite(amt) || amt <= 0 || !expenseDesc.trim()) {
                        showNotice('error', 'Nominal harus lebih dari Rp0 dan keterangan wajib diisi.');
                        return;
                      }
                      if (backendMode === 'LIVE_SUPABASE') {
                        showNotice('error', 'Pencatatan pengeluaran belum terhubung ke Supabase. Tidak ada perubahan disimpan.');
                        return;
                      }
                      const created: Expense = {
                        id: `exp-${Date.now()}`,
                        amount: amt,
                        category: expenseCategory,
                        description: expenseDesc.trim(),
                        expense_date: expenseDate,
                        payment_method: expenseMethod,
                        created_at: new Date().toISOString(),
                      };
                      setExpenses((prev) => [created, ...prev]);
                      setExpenseAmount('');
                      setExpenseDesc('');
                      showNotice('success', 'Pengeluaran berhasil disimpan.');
                    }}
                    className="mt-4 grid gap-3 md:grid-cols-2"
                  >
                    <div>
                      <label className="block text-xs text-zinc-500">Nominal (Rp)</label>
                      <input
                        type="number"
                        value={expenseAmount}
                        onChange={(e) => setExpenseAmount(e.target.value)}
                        placeholder="Contoh: 45000"
                        className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-zinc-500">Kategori</label>
                      <select
                        value={expenseCategory}
                        onChange={(e) => setExpenseCategory(e.target.value)}
                        className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      >
                        {[
                          'Bahan baku',
                          'Operasional',
                          'Listrik & air',
                          'Gaji',
                          'Sewa',
                          'Perawatan',
                          'Transportasi',
                          'Lainnya',
                        ].map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-zinc-500">Tanggal</label>
                      <input
                        type="date"
                        value={expenseDate}
                        onChange={(e) => setExpenseDate(e.target.value)}
                        className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      />
                    </div>
                    <div>
                      <label className="block text-xs text-zinc-500">Metode Pembayaran</label>
                      <select
                        value={expenseMethod}
                        onChange={(e) => setExpenseMethod(e.target.value)}
                        className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      >
                        <option value="cash">Tunai</option>
                        <option value="qris">QRIS</option>
                        <option value="transfer">Transfer</option>
                      </select>
                    </div>
                    <div className="md:col-span-2">
                      <label className="block text-xs text-zinc-500">Keterangan</label>
                      <input
                        type="text"
                        value={expenseDesc}
                        onChange={(e) => setExpenseDesc(e.target.value)}
                        placeholder="Contoh: Beli susu segar & es batu"
                        className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                      />
                    </div>
                    <div className="md:col-span-2">
                      <button
                        type="submit"
                        className="rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                      >
                        Simpan Pengeluaran
                      </button>
                    </div>
                  </form>
                </div>

                <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                  <h3 className="text-base font-bold">
                    Riwayat Kas Keluar · <span className="num">{formatRupiah(profitLossSummary.totalExpenses)}</span>
                  </h3>
                  <div className="mt-3 divide-y divide-zinc-100 dark:divide-zinc-800">
                    {profitLossSummary.filteredExp.map((e) => (
                      <div key={e.id} className="flex items-center justify-between py-3 text-xs">
                        <div>
                          <p className="font-semibold">{e.description}</p>
                          <p className="text-zinc-500">
                            {e.expense_date} · {e.category} · {e.payment_method.toUpperCase()}
                          </p>
                        </div>
                        <span className="num font-bold">{formatRupiah(e.amount)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  {[
                    ['Penjualan Bersih', profitLossSummary.netSales],
                    ['HPP / COGS', profitLossSummary.cogs],
                    ['Total Pengeluaran', profitLossSummary.totalExpenses],
                    ['Laba / (Rugi) Bersih', profitLossSummary.netProfit],
                  ].map(([label, val]) => (
                    <div
                      key={String(label)}
                      className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900"
                    >
                      <p className="text-xs text-zinc-500">{label}</p>
                      <p className="num mt-2 text-xl font-bold">{formatRupiah(Number(val))}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );

      // ======================================================================
      // 14. LAPORAN PENJUALAN
      // ======================================================================
      case 'laporan':
        return (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
              <div>
                <h3 className="text-base font-bold">Laporan Penjualan Lengkap (Harian, Produk, Metode &amp; Kanal)</h3>
                <p className="text-xs text-zinc-500">
                  Mencakup seluruh transaksi lunas dari kanal POS Kasir dan QR Ordering Meja.
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  const rows = [
                    ['Laporan Penjualan Kereta Malam Cafe'],
                    ['Total Omzet Lunas', todayRevenue],
                    ['Jumlah Transaksi Lunas', paidOrders.length],
                    [],
                    ['No Order', 'Waktu', 'Kanal', 'Meja', 'Pelanggan', 'Metode Bayar', 'Subtotal', 'Diskon', 'Pajak', 'Total (Rp)'],
                    ...paidOrders.map((o) => [
                      `#${o.order_no}`,
                      o.created_at,
                      o.source.toUpperCase(),
                      o.restaurant_tables?.name ?? 'Takeaway',
                      o.customer_name ?? '-',
                      o.payment_method ?? '-',
                      o.subtotal,
                      o.discount,
                      o.tax,
                      o.total,
                    ]),
                  ];
                  downloadCsvFile('laporan-penjualan-kereta-malam-cafe.csv', rows);
                  showNotice('success', 'File CSV Laporan Penjualan berhasil diunduh.');
                }}
                className="inline-flex items-center gap-2 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                <Download className="size-3.5" />
                Unduh CSV Laporan Penjualan
              </button>
            </div>

            <div className="grid gap-6 md:grid-cols-2">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <h4 className="text-sm font-bold">Penjualan per Produk</h4>
                <div className="mt-3 divide-y divide-zinc-100 text-xs dark:divide-zinc-800">
                  {topSellingProducts.map((p) => (
                    <div key={p.name} className="flex justify-between py-2.5">
                      <span>{p.name}</span>
                      <span className="num">
                        {p.qty} unit · <strong>{formatRupiah(p.revenue)}</strong>
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <h4 className="text-sm font-bold">Penjualan per Kanal &amp; Metode Pembayaran</h4>
                <div className="mt-3 space-y-2 text-xs">
                  <div className="flex justify-between border-b border-zinc-100 pb-2 dark:border-zinc-800">
                    <span>Kanal Kasir POS</span>
                    <span className="num font-bold">
                      {formatRupiah(paidOrders.filter((o) => o.source === 'pos').reduce((s, o) => s + o.total, 0))}
                    </span>
                  </div>
                  <div className="flex justify-between border-b border-zinc-100 pb-2 dark:border-zinc-800">
                    <span>Kanal QR Ordering Meja</span>
                    <span className="num font-bold">
                      {formatRupiah(paidOrders.filter((o) => o.source === 'qr').reduce((s, o) => s + o.total, 0))}
                    </span>
                  </div>
                  {paymentBreakdown.map((pm) => (
                    <div key={pm.method} className="flex justify-between pt-1">
                      <span className="uppercase">Metode {pm.method}</span>
                      <span className="num font-bold">{formatRupiah(pm.total)}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 15. PEGAWAI & HAK AKSES (RBAC)
      // ======================================================================
      case 'pegawai':
        return (
          <div className="space-y-6">
            <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">Manajemen Pegawai &amp; Hak Akses Role (staff_profiles)</h3>
              <p className="mt-1 text-xs text-zinc-500">
                Anda dapat menguji perpindahan sesi staf untuk memverifikasi bagaimana menu navigasi beradaptasi dengan role (<code className="font-mono">owner</code>, <code className="font-mono">manager</code>, <code className="font-mono">cashier</code>).
              </p>

              <div className="mt-4 grid gap-4 md:grid-cols-3">
                {staffList.map((st) => {
                  const isCurrent = st.user_id === currentStaff.user_id;
                  return (
                    <div
                      key={st.user_id}
                      className={`rounded-xl border p-4 ${
                        isCurrent
                          ? 'border-zinc-900 bg-zinc-50/70 dark:border-zinc-100 dark:bg-zinc-900'
                          : 'border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900'
                      }`}
                    >
                      <div className="flex items-start justify-between">
                        <div>
                          <h4 className="text-sm font-bold">{st.full_name}</h4>
                          <p className="font-mono text-xs text-zinc-500 uppercase">{st.role}</p>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            setCurrentStaff(st);
                            showNotice('info', `Beralih ke profil akses: ${st.full_name} (${st.role})`);
                          }}
                          className={`rounded-lg px-2.5 py-1 text-xs font-semibold ${
                            isCurrent
                              ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                              : 'border border-zinc-300 dark:border-zinc-700'
                          }`}
                        >
                          {isCurrent ? 'Sedang Aktif' : 'Gunakan Profil'}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 16. PENGATURAN OUTLET & VERIFIKASI SUPABASE
      // ======================================================================
      case 'pengaturan':
        return (
          <div className="mx-auto max-w-4xl space-y-6">
            {/* Panel Verifikasi Supabase */}
            <div className="rounded-xl border border-zinc-300 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">
                1. Verifikasi Project ID &amp; Kredensial Supabase Produksi
              </h3>
              <p className="mt-1 text-xs text-zinc-500">
                Pilih Project ID yang telah Anda verifikasi (<code className="font-mono">rvaemgwtdvncfvaerhgr</code> dari repository atau <code className="font-mono">dbazoxwhhljpgtbnletc</code> dari Cloudflare Worker) lalu masukkan Publishable/Anon Key resmi.
              </p>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {CANDIDATE_PROJECTS.map((cand) => (
                  <button
                    key={cand.id}
                    type="button"
                    onClick={() =>
                      setSupabaseConfig((prev) => ({
                        ...prev,
                        projectId: cand.id,
                        supabaseUrl: cand.url,
                      }))
                    }
                    className={`rounded-xl border p-3.5 text-left transition-all ${
                      supabaseConfig.projectId === cand.id
                        ? 'border-zinc-900 bg-zinc-50 dark:border-zinc-100 dark:bg-zinc-800/60'
                        : 'border-zinc-200 hover:border-zinc-400 dark:border-zinc-800'
                    }`}
                  >
                    <p className="font-mono text-xs font-bold">{cand.id}</p>
                    <p className="mt-1 text-[11px] text-zinc-500">{cand.source}</p>
                  </button>
                ))}
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="block text-xs font-medium text-zinc-500">Project ID Terverifikasi</label>
                  <input
                    type="text"
                    value={supabaseConfig.projectId}
                    onChange={(e) =>
                      setSupabaseConfig((prev) => ({ ...prev, projectId: e.target.value.trim() }))
                    }
                    placeholder="rvaemgwtdvncfvaerhgr atau dbazoxwhhljpgtbnletc"
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 font-mono text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-zinc-500">Supabase Project URL</label>
                  <input
                    type="url"
                    value={supabaseConfig.supabaseUrl}
                    onChange={(e) =>
                      setSupabaseConfig((prev) => ({ ...prev, supabaseUrl: e.target.value.trim() }))
                    }
                    placeholder="https://xxxx.supabase.co"
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 font-mono text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-zinc-500">
                    Supabase Publishable / Anon Key (Jangan masukkan service_role key!)
                  </label>
                  <input
                    type="password"
                    value={supabaseConfig.publishableKey}
                    onChange={(e) =>
                      setSupabaseConfig((prev) => ({ ...prev, publishableKey: e.target.value.trim() }))
                    }
                    placeholder="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... atau sb_publishable_..."
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 font-mono text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>

              <div className="mt-4 flex flex-wrap gap-2.5">
                <button
                  type="button"
                  onClick={() => {
                    try {
                      const nextCfg: SupabaseVerificationConfig = {
                        ...supabaseConfig,
                        verifiedByUser: Boolean(
                          supabaseConfig.projectId &&
                            supabaseConfig.supabaseUrl &&
                            supabaseConfig.publishableKey
                        ),
                      };
                      saveVerificationConfig(nextCfg);
                      setSupabaseConfig(nextCfg);
                      if (nextCfg.verifiedByUser) {
                        void syncFromLiveSupabase(nextCfg);
                      } else {
                        setBackendMode('INACTIVE_PENDING_VERIFICATION');
                        showNotice('info', 'Integrasi Supabase ditandai BELUM AKTIF.');
                      }
                    } catch (err) {
                      showNotice('error', err instanceof Error ? err.message : 'Gagal menyimpan.');
                    }
                  }}
                  className="rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                >
                  Verifikasi &amp; Hubungkan ke Supabase
                </button>
              </div>

              {/* Login Supabase Auth */}
              <form onSubmit={handleSupabaseLogin} className="mt-6 border-t border-zinc-100 pt-4 dark:border-zinc-800">
                <h4 className="text-xs font-bold uppercase text-zinc-500">
                  Login Akun Staf via Supabase Auth (signInWithPassword)
                </h4>
                <div className="mt-2 grid gap-3 sm:grid-cols-3">
                  <input
                    type="email"
                    value={authEmail}
                    onChange={(e) => setAuthEmail(e.target.value)}
                    placeholder="Email staf..."
                    className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                  <input
                    type="password"
                    value={authPassword}
                    onChange={(e) => setAuthPassword(e.target.value)}
                    placeholder="Password..."
                    className="rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                  <button
                    type="submit"
                    disabled={authLoading}
                    className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-zinc-300 px-4 py-2 text-xs font-semibold hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
                  >
                    <LogIn className="size-3.5" />
                    {authLoading ? 'Memverifikasi...' : 'Login Supabase'}
                  </button>
                </div>
              </form>
            </div>

            {/* Ukuran Tampilan / Zoom */}
            <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">2. Pengaturan Ukuran Tampilan / Zoom Layar Android</h3>
              <p className="mt-1 text-xs text-zinc-500">
                Pilih skala tampilan termasuk 40%, 50%, dan 60% untuk memuat lebih banyak informasi di layar HP/Tablet tanpa memotong dialog.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                {ZOOM_PRESETS.map((z) => (
                  <button
                    key={z}
                    type="button"
                    onClick={() => handleSetZoom(z)}
                    className={`num rounded-lg px-3.5 py-2 text-xs font-semibold transition-colors ${
                      screenZoom === z
                        ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                        : 'border border-zinc-200 bg-zinc-50 text-zinc-700 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-300'
                    }`}
                  >
                    {z}%
                  </button>
                ))}
              </div>
            </div>

            {/* Pengaturan Pajak & Service */}
            <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">3. Parameter Outlet, Pajak &amp; Layanan (outlet_settings)</h3>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="block text-xs text-zinc-500">Nama Outlet</label>
                  <input
                    type="text"
                    value={outletSettings.name}
                    onChange={(e) => setOutletSettings((prev) => ({ ...prev, name: e.target.value }))}
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Base URL QR Menu Pelanggan</label>
                  <input
                    type="url"
                    value={outletSettings.public_web_base_url}
                    onChange={(e) =>
                      setOutletSettings((prev) => ({ ...prev, public_web_base_url: e.target.value }))
                    }
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 font-mono text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div className="flex items-center justify-between rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
                  <div>
                    <p className="text-xs font-semibold">Aktifkan Pajak PB1 ({outletSettings.tax_rate}%)</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={outletSettings.tax_enabled}
                    onChange={(e) =>
                      setOutletSettings((prev) => ({ ...prev, tax_enabled: e.target.checked }))
                    }
                  />
                </div>
                <div className="flex items-center justify-between rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
                  <div>
                    <p className="text-xs font-semibold">
                      Aktifkan Service Charge ({outletSettings.service_rate}%)
                    </p>
                  </div>
                  <input
                    type="checkbox"
                    checked={outletSettings.service_enabled}
                    onChange={(e) =>
                      setOutletSettings((prev) => ({ ...prev, service_enabled: e.target.checked }))
                    }
                  />
                </div>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 17. DUAL BLUETOOTH PRINTER ESC/POS
      // ======================================================================
      case 'printer':
        return (
          <div className="grid gap-6 lg:grid-cols-12">
            <div className="space-y-4 lg:col-span-7">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h3 className="text-base font-bold">
                      Konfigurasi Dual Printer Bluetooth Thermal ESC/POS
                    </h3>
                    <p className="text-xs text-zinc-500">
                      Mendukung 2 printer sekaligus (Printer Struk Kasir &amp; Printer Tiket Dapur). Pengujian fisik dengan perangkat printer thermal Bluetooth tetap diperlukan pada APK terinstal.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setIsScanningPrinters(true);
                      setTimeout(() => {
                        setIsScanningPrinters(false);
                        showNotice('success', 'Pemindaian perangkat Bluetooth SPP selesai.');
                      }, 700);
                    }}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-900 px-3.5 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900 whitespace-nowrap shrink-0"
                  >
                    <RefreshCw className={`size-3.5 ${isScanningPrinters ? 'animate-spin' : ''}`} />
                    {isScanningPrinters ? 'Memindai...' : 'Cari Perangkat Bluetooth'}
                  </button>
                </div>

                <div className="mt-4 flex items-center justify-between rounded-lg border border-zinc-200 bg-zinc-50 p-3 text-xs dark:border-zinc-800 dark:bg-zinc-950">
                  <span>
                    Status Izin Runtime Android 12+ (<code className="font-mono">BLUETOOTH_SCAN</code> &amp;{' '}
                    <code className="font-mono">BLUETOOTH_CONNECT</code>)
                  </span>
                  <button
                    type="button"
                    onClick={() => setBluetoothPermGranted((v) => !v)}
                    className={`rounded px-2.5 py-1 font-semibold ${
                      bluetoothPermGranted
                        ? 'bg-emerald-600 text-white'
                        : 'bg-red-600 text-white'
                    }`}
                  >
                    {bluetoothPermGranted ? 'Izin Diberikan' : 'Izin Ditolak'}
                  </button>
                </div>

                {/* Dua Peran Printer */}
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  {(['cashier', 'kitchen'] as const).map((role) => {
                    const current = printers[role];
                    return (
                      <div
                        key={role}
                        className="rounded-xl border border-zinc-200 p-4 dark:border-zinc-800"
                      >
                        <div className="flex items-center justify-between">
                          <h4 className="text-sm font-bold">
                            {role === 'cashier' ? '1. Printer Struk Kasir' : '2. Printer Tiket Dapur'}
                          </h4>
                          <span className="text-[11px] font-semibold">
                            {current?.connected ? 'Terhubung' : 'Terputus'}
                          </span>
                        </div>

                        {current ? (
                          <div className="mt-2 space-y-2 text-xs">
                            <p className="font-mono">
                              {current.name} ({current.address})
                            </p>
                            <label className="flex items-center gap-2">
                              <input
                                type="checkbox"
                                checked={current.autoPrint}
                                onChange={(e) => {
                                  const checked = e.target.checked;
                                  setPrinters((prev) => ({
                                    ...prev,
                                    [role]: prev[role] ? { ...prev[role]!, autoPrint: checked } : null,
                                  }));
                                }}
                              />
                              <span>Cetak otomatis saat transaksi selesai</span>
                            </label>
                            <div className="flex gap-2 pt-2">
                              <button
                                type="button"
                                onClick={() => {
                                  const sampleOrder = orders[0] || INITIAL_ORDERS[0];
                                  setThermalPreviewText(formatEscPosReceiptText(sampleOrder, role));
                                  showNotice(
                                    'success',
                                    `Tes cetak ${role === 'cashier' ? 'Struk Kasir' : 'Tiket Dapur'} dikirim ke ${current.name}.`
                                  );
                                }}
                                className="flex-1 rounded-lg bg-zinc-900 py-1.5 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
                              >
                                Tes Cetak
                              </button>
                              <button
                                type="button"
                                onClick={() =>
                                  setPrinters((prev) => ({ ...prev, [role]: null }))
                                }
                                className="rounded-lg border border-zinc-300 px-3 py-1.5 text-xs dark:border-zinc-700"
                              >
                                Putuskan
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="mt-3 space-y-1.5">
                            <p className="text-xs text-zinc-500">Pilih dari perangkat terdeteksi:</p>
                            {discoveredPrinters.map((dev) => (
                              <button
                                key={dev.address}
                                type="button"
                                onClick={() => {
                                  setPrinters((prev) => ({
                                    ...prev,
                                    [role]: {
                                      role,
                                      name: dev.name,
                                      address: dev.address,
                                      paperWidth: '58mm',
                                      autoPrint: true,
                                      connected: true,
                                    },
                                  }));
                                  showNotice('success', `${dev.name} ditetapkan sebagai printer ${role}.`);
                                }}
                                className="block w-full rounded border border-zinc-200 px-2.5 py-1.5 text-left font-mono text-[11px] hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-800"
                              >
                                {dev.name} ({dev.address})
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Pratinjau Output Kertas Thermal ESC/POS */}
            <div className="lg:col-span-5">
              <div className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
                <h3 className="text-sm font-bold">Output Stream ESC/POS Thermal 58mm</h3>
                <p className="mt-0.5 text-xs text-zinc-500">
                  Menampilkan byte stream teks ASCII/UTF-8 yang dikirim ke socket RFCOMM Bluetooth.
                </p>
                <pre className="mt-3 overflow-x-auto rounded-xl border border-zinc-200 bg-zinc-50 p-4 font-mono text-xs leading-relaxed text-zinc-900 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100">
                  {thermalPreviewText}
                </pre>
              </div>
            </div>
          </div>
        );

      // ======================================================================
      // 18. MANAGEMENT WEBSITE
      // ======================================================================
      case 'website-management':
        return (
          <div className="space-y-6">
            <div className="rounded-xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
              <h3 className="text-base font-bold">Management Website CMS (website_settings &amp; website_pages)</h3>
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <label className="block text-xs text-zinc-500">Nama Situs</label>
                  <input
                    type="text"
                    value={websiteCms.site_name}
                    onChange={(e) => setWebsiteCms((prev) => ({ ...prev, site_name: e.target.value }))}
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-xs text-zinc-500">Tagline Publik</label>
                  <input
                    type="text"
                    value={websiteCms.tagline}
                    onChange={(e) => setWebsiteCms((prev) => ({ ...prev, tagline: e.target.value }))}
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs text-zinc-500">Headline Hero Beranda</label>
                  <input
                    type="text"
                    value={websiteCms.hero_headline}
                    onChange={(e) =>
                      setWebsiteCms((prev) => ({ ...prev, hero_headline: e.target.value }))
                    }
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (backendMode === 'LIVE_SUPABASE') {
                    showNotice('error', 'Simpan CMS Website belum terhubung ke Supabase. Tidak ada perubahan disimpan.');
                    return;
                  }
                  showNotice('success', 'Pengaturan CMS Website berhasil diperbarui.');
                }}
                className="mt-4 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                Simpan Perubahan Website
              </button>
            </div>
          </div>
        );
    }
  };

  const currentModuleTitle =
    NAV_GROUPS.flatMap((g) => g.items).find((i) => i.id === activeModule)?.label || 'Dashboard';

  return (
    <div className="min-h-screen bg-zinc-100 text-zinc-900 transition-colors dark:bg-zinc-950 dark:text-zinc-100">
      {/* Top Studio Bar for Android Native Code Export & Viewport Testing */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-200 bg-white px-4 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-900">
        <div className="flex items-center gap-2">
          <span className="font-bold tracking-tight">Kereta Malam Cafe · Android Native Studio</span>
          <span className="text-zinc-400">·</span>
          <span className="font-mono text-[11px] text-zinc-500">
            {backendMode === 'LIVE_SUPABASE'
              ? `SUPABASE AKTIF (${supabaseConfig.projectId})`
              : backendMode === 'LOCAL_SANDBOX_UI'
                ? 'MODE SIMULASI UI LOKAL (BUKAN DATA PRODUKSI)'
                : 'SUPABASE BELUM AKTIF (MENUNGGU VERIFIKASI)'}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Viewport Switcher */}
          <div className="hidden items-center gap-1 rounded-lg bg-zinc-100 p-1 sm:flex dark:bg-zinc-800">
            <button
              type="button"
              onClick={() => setViewportMode('full')}
              className={`rounded-md px-2.5 py-1 text-[11px] font-medium whitespace-nowrap shrink-0 ${
                viewportMode === 'full' ? 'bg-white text-zinc-900 shadow-2xs dark:bg-zinc-900 dark:text-white' : 'text-zinc-500'
              }`}
            >
              Layar Penuh
            </button>
            <button
              type="button"
              onClick={() => setViewportMode('tablet')}
              className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-[11px] font-medium whitespace-nowrap shrink-0 ${
                viewportMode === 'tablet' ? 'bg-white text-zinc-900 shadow-2xs dark:bg-zinc-900 dark:text-white' : 'text-zinc-500'
              }`}
            >
              <Tablet className="size-3" />
              Tablet POS
            </button>
            <button
              type="button"
              onClick={() => setViewportMode('phone')}
              className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-[11px] font-medium whitespace-nowrap shrink-0 ${
                viewportMode === 'phone' ? 'bg-white text-zinc-900 shadow-2xs dark:bg-zinc-900 dark:text-white' : 'text-zinc-500'
              }`}
            >
              <Smartphone className="size-3" />
              HP Android
            </button>
          </div>

          {/* Quick Zoom Buttons including 40%, 50%, 60% */}
          <div className="flex items-center gap-1 rounded-lg border border-zinc-200 px-2 py-1 dark:border-zinc-800">
            <span className="text-[11px] text-zinc-500">Zoom:</span>
            {[40, 50, 60, 80, 100].map((z) => (
              <button
                key={z}
                type="button"
                onClick={() => handleSetZoom(z)}
                className={`num rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                  screenZoom === z
                    ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                    : 'text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100'
                }`}
              >
                {z}%
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setDarkMode((d) => !d)}
            className="inline-flex size-8 items-center justify-center rounded-lg border border-zinc-200 hover:bg-zinc-100 dark:border-zinc-800 dark:hover:bg-zinc-800"
            title="Toggle Light / Dark Mode"
          >
            {darkMode ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </button>

          <button
            type="button"
            onClick={() => setLoginScreenOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-300 bg-white px-2.5 py-1.5 text-xs font-semibold text-zinc-900 hover:bg-zinc-100 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100 dark:hover:bg-zinc-800 whitespace-nowrap shrink-0"
          >
            <LogIn className="size-3.5" />
            Halaman Login
          </button>

          <button
            type="button"
            onClick={() => setKotlinModalOpen(true)}
            className="inline-flex items-center gap-1.5 rounded-lg bg-zinc-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200 whitespace-nowrap shrink-0"
          >
            <Code2 className="size-3.5" />
            Kode Kotlin &amp; Unduh Project (.zip)
          </button>
        </div>
      </div>

      {/* Main Responsive Device Frame */}
      <div
        className={`mx-auto transition-all ${
          viewportMode === 'phone'
            ? 'my-4 max-w-[420px] overflow-hidden rounded-[32px] border-[6px] border-zinc-900 shadow-2xl dark:border-zinc-700'
            : viewportMode === 'tablet'
              ? 'my-4 max-w-[1060px] overflow-hidden rounded-[24px] border-[6px] border-zinc-900 shadow-2xl dark:border-zinc-700'
              : 'w-full'
        }`}
      >
        <div
          className="flex min-h-[calc(100vh-49px)] bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100"
          style={{ zoom: screenZoom / 100 }}
        >
          {/* Sidebar Navigation (Tablet / Desktop) */}
          {viewportMode !== 'phone' && !sidebarCollapsed && (
            <aside className="sticky top-0 flex h-dvh w-64 shrink-0 flex-col border-r border-zinc-200 bg-white p-3 dark:border-zinc-800 dark:bg-zinc-900">
              <div className="flex items-center justify-between px-2 py-2">
                <div>
                  <p className="text-base font-bold tracking-tight">Kereta Malam Cafe</p>
                  <p className="text-[11px] text-zinc-500">POS &amp; QR Ordering</p>
                </div>
                <button
                  type="button"
                  onClick={() => setSidebarCollapsed(true)}
                  className="rounded-lg p-1.5 text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                  title="Sembunyikan menu"
                >
                  <PanelLeftClose className="size-4" />
                </button>
              </div>

              <div className="mt-3 flex-1 space-y-4 overflow-y-auto pr-1">
                {NAV_GROUPS.map((group) => {
                  const visibleItems = group.items.filter((item) => canAccessModule(item.permission));
                  if (visibleItems.length === 0) return null;
                  return (
                    <div key={group.label}>
                      <p className="px-2.5 text-[10px] font-bold tracking-wider text-zinc-400 uppercase">
                        {group.label}
                      </p>
                      <div className="mt-1 space-y-0.5">
                        {visibleItems.map((item) => {
                          const Icon = item.icon;
                          const active = activeModule === item.id;
                          return (
                            <button
                              key={item.id}
                              type="button"
                              onClick={() => setActiveModule(item.id)}
                              className={`flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-xs font-medium transition-colors ${
                                active
                                  ? 'bg-zinc-900 font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900'
                                  : 'text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800/70'
                              }`}
                            >
                              <Icon className="size-4 shrink-0" />
                              <span className="truncate">{item.label}</span>
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="flex items-center justify-between border-t border-zinc-200 pt-3 dark:border-zinc-800">
                <div className="min-w-0 px-2.5 pb-2 text-[11px]">
                  <p className="font-semibold truncate">{currentStaff.full_name}</p>
                  <p className="font-mono text-zinc-500 uppercase">{currentStaff.role}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setLoginScreenOpen(true)}
                  className="inline-flex size-8 items-center justify-center rounded-lg border border-zinc-200 text-zinc-600 hover:bg-zinc-100 dark:border-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-800"
                  title="Keluar / Ganti Sesi Login"
                >
                  <LogOut className="size-3.5" />
                </button>
              </div>
            </aside>
          )}

          {/* Main Content Area */}
          <div className="flex min-w-0 flex-1 flex-col">
            {/* AppShell Header */}
            <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-zinc-200 bg-white/95 px-4 py-3 backdrop-blur-xs md:px-6 dark:border-zinc-800 dark:bg-zinc-900/95">
              <div className="flex items-center gap-3">
                {(viewportMode === 'phone' || sidebarCollapsed) && (
                  <button
                    type="button"
                    onClick={() => {
                      if (viewportMode === 'phone') setMobileDrawerOpen(true);
                      else setSidebarCollapsed(false);
                    }}
                    className="inline-flex size-9 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-800"
                  >
                    <Menu className="size-4" />
                  </button>
                )}
                <div>
                  <h1 className="text-base font-bold md:text-lg">{currentModuleTitle}</h1>
                  <p className="text-[11px] text-zinc-500">
                    {outletSettings.name} · Bahasa Indonesia · Rupiah (Rp)
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {backendMode === 'LOCAL_SANDBOX_UI' && (
                  <span className="rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-200">
                    Mode Simulasi UI Lokal
                  </span>
                )}
                <button
                  type="button"
                  onClick={() => setLoginScreenOpen(true)}
                  className="inline-flex items-center gap-1 rounded-lg border border-zinc-200 px-2.5 py-1 text-[11px] font-semibold hover:bg-zinc-100 dark:border-zinc-800 dark:hover:bg-zinc-800"
                >
                  <LogOut className="size-3.5" />
                  <span className="hidden sm:inline">Ganti Sesi</span>
                </button>
              </div>
            </header>

            {/* Feedback Notification Toast */}
            {feedback && (
              <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/35 p-4 backdrop-blur-[2px]" role="dialog" aria-modal="true" aria-label="Notifikasi">
                <div className="flex w-full max-w-md items-center justify-between gap-4 rounded-2xl border border-zinc-200 bg-white px-5 py-4 text-sm font-medium text-zinc-900 shadow-2xl dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100">
                  <div className="flex min-w-0 items-center gap-3">
                    <CheckCircle2 className="size-5 shrink-0" />
                    <span>{feedback.message}</span>
                  </div>
                  <button type="button" aria-label="Tutup notifikasi" onClick={() => setFeedback(null)} className="grid size-9 shrink-0 place-items-center rounded-full hover:bg-zinc-100 dark:hover:bg-zinc-800">
                    <X className="size-4" />
                  </button>
                </div>
              </div>
            )}

            <main className="flex-1 p-4 md:p-6">{renderModuleContent()}</main>

            {/* Bottom Navigation Bar khusus Mode HP Android (Sesuai NavigationBar di CafeFlowAppShell.kt) */}
            {viewportMode === 'phone' && (
              <nav className="sticky bottom-0 z-20 grid grid-cols-5 border-t border-zinc-200 bg-white/95 px-1 py-1.5 backdrop-blur-xs dark:border-zinc-800 dark:bg-zinc-900/95">
                {[
                  { id: 'dashboard' as ModuleId, label: 'Dashboard', icon: LayoutDashboard },
                  { id: 'kasir' as ModuleId, label: 'Kasir', icon: ScanLine },
                  { id: 'pesanan' as ModuleId, label: 'Pesanan', icon: ClipboardList },
                  { id: 'history' as ModuleId, label: 'History', icon: History },
                ].map((tab) => {
                  const Icon = tab.icon;
                  const active = activeModule === tab.id;
                  return (
                    <button
                      key={tab.id}
                      type="button"
                      onClick={() => setActiveModule(tab.id)}
                      className={`flex flex-col items-center justify-center gap-0.5 rounded-xl py-1.5 text-[10px] font-semibold ${
                        active
                          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'text-zinc-500 dark:text-zinc-400'
                      }`}
                    >
                      <Icon className="size-4" />
                      <span className="truncate">{tab.label}</span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={() => setMobileDrawerOpen(true)}
                  className="flex flex-col items-center justify-center gap-0.5 rounded-xl py-1.5 text-[10px] font-semibold text-zinc-500 dark:text-zinc-400"
                >
                  <Menu className="size-4" />
                  <span>Modul</span>
                </button>
              </nav>
            )}
          </div>
        </div>
      </div>

      {/* Minimal floating zoom controls; transparent and kept clear of primary navigation. */}
      <div className="fixed right-2 bottom-2 z-40 flex items-center gap-1 bg-transparent p-0">
        <button
          type="button"
          onClick={() => handleSetZoom(screenZoom - 10)}
          disabled={screenZoom <= 40}
          className="grid size-6 place-items-center rounded-full text-zinc-600 hover:bg-zinc-100 disabled:opacity-40 dark:text-zinc-300 dark:hover:bg-zinc-800"
          title="Perkecil tampilan"
        >
          <Minus className="size-3.5" />
        </button>
        <span className="num min-w-9 text-center text-xs font-semibold">{screenZoom}%</span>
        <button
          type="button"
          onClick={() => handleSetZoom(screenZoom + 10)}
          disabled={screenZoom >= 120}
          className="grid size-6 place-items-center rounded-full text-zinc-600 hover:bg-zinc-100 disabled:opacity-40 dark:text-zinc-300 dark:hover:bg-zinc-800"
          title="Perbesar tampilan"
        >
          <Plus className="size-3.5" />
        </button>
      </div>

      {/* Mobile Navigation Drawer */}
      {mobileDrawerOpen && (
        <div className="fixed inset-0 z-50 flex bg-black/60 backdrop-blur-xs">
          <div className="flex h-full w-72 flex-col bg-white p-4 dark:bg-zinc-900">
            <div className="flex items-center justify-between border-b border-zinc-200 pb-3 dark:border-zinc-800">
              <div>
                <p className="font-bold">Kereta Malam Cafe</p>
                <p className="text-xs text-zinc-500">Navigasi POS Android</p>
              </div>
              <button type="button" onClick={() => setMobileDrawerOpen(false)}>
                <X className="size-5" />
              </button>
            </div>
            <div className="mt-3 flex-1 space-y-3 overflow-y-auto">
              {NAV_GROUPS.map((group) => (
                <div key={group.label}>
                  <p className="px-2 text-[10px] font-bold text-zinc-400 uppercase">{group.label}</p>
                  <div className="mt-1 space-y-1">
                    {group.items
                      .filter((i) => canAccessModule(i.permission))
                      .map((item) => {
                        const Icon = item.icon;
                        return (
                          <button
                            key={item.id}
                            type="button"
                            onClick={() => {
                              setActiveModule(item.id);
                              setMobileDrawerOpen(false);
                            }}
                            className={`flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-medium ${
                              activeModule === item.id
                                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                                : 'text-zinc-700 dark:text-zinc-300'
                            }`}
                          >
                            <Icon className="size-4" />
                            <span>{item.label}</span>
                          </button>
                        );
                      })}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 1: KONFIRMASI TRANSAKSI FINAL KASIR                  */}
      {/* ==================================================================== */}
      {confirmCheckoutModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="text-lg font-bold">Konfirmasi Transaksi Final</h3>
            <p className="mt-1 text-xs text-zinc-500">
              Periksa kembali rincian pesanan sebelum disimpan. Tombol pembayaran dilindungi dari penekanan ganda (idempotency lock).
            </p>

            <div className="my-4 space-y-2 rounded-xl border border-zinc-200 bg-zinc-50 p-4 text-xs dark:border-zinc-800 dark:bg-zinc-950">
              <div className="flex justify-between">
                <span className="text-zinc-500">Meja / Layanan</span>
                <span className="font-semibold">
                  {tables.find((t) => t.id === selectedTableId)?.name ?? 'Takeaway'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-zinc-500">Metode Pembayaran</span>
                <span className="font-semibold uppercase">{paymentMethod}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-zinc-500">Jumlah Item</span>
                <span className="num font-semibold">
                  {cart.reduce((s, i) => s + i.quantity, 0)} item
                </span>
              </div>
              <div className="flex justify-between border-t border-zinc-200 pt-2 text-sm font-bold dark:border-zinc-800">
                <span>Total Akhir</span>
                <span className="num">{formatRupiah(cartGrandTotal)}</span>
              </div>
              {paymentMethod === 'cash' && (
                <>
                  <div className="flex justify-between">
                    <span className="text-zinc-500">Tunai Diterima</span>
                    <span className="num">{formatRupiah(cashReceivedAmount)}</span>
                  </div>
                  <div className="flex justify-between font-semibold">
                    <span>Kembalian</span>
                    <span className="num">{formatRupiah(cashChangeAmount)}</span>
                  </div>
                </>
              )}
            </div>

            <div className="flex items-center justify-end gap-2">
              <button
                type="button"
                disabled={isCheckoutProcessing}
                onClick={() => setConfirmCheckoutModalOpen(false)}
                className="min-h-[40px] rounded-xl border border-zinc-300 px-4 py-2 text-xs font-semibold dark:border-zinc-700"
              >
                Batal
              </button>
              <button
                type="button"
                disabled={isCheckoutProcessing}
                onClick={handleExecuteCheckout}
                className="min-h-[40px] rounded-xl bg-zinc-900 px-5 py-2 text-xs font-bold text-white hover:bg-zinc-800 disabled:opacity-40 dark:bg-zinc-100 dark:text-zinc-900"
              >
                {isCheckoutProcessing ? 'Menyimpan...' : 'Konfirmasi & Simpan Transaksi'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 2: KUSTOMISASI ITEM MENU                             */}
      {/* ==================================================================== */}
      {customizingProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold">Opsi Kustomisasi · {customizingProduct.name}</h3>
              <button type="button" onClick={() => setCustomizingProduct(null)}>
                <X className="size-4" />
              </button>
            </div>

            <div className="mt-4 space-y-3 text-xs">
              <div>
                <label className="font-semibold">Takaran Gula (Sugar Level)</label>
                <div className="mt-1.5 grid grid-cols-3 gap-1.5">
                  {['Normal Sugar', 'Less Sugar', 'No Sugar'].map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => setCustomSugar(s)}
                      className={`rounded-lg py-2 font-medium ${
                        customSugar === s
                          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'border border-zinc-200 dark:border-zinc-800'
                      }`}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="font-semibold">Takaran Es (Ice Level)</label>
                <div className="mt-1.5 grid grid-cols-3 gap-1.5">
                  {['Normal Ice', 'Less Ice', 'Hot / Warm'].map((ic) => (
                    <button
                      key={ic}
                      type="button"
                      onClick={() => setCustomIce(ic)}
                      className={`rounded-lg py-2 font-medium ${
                        customIce === ic
                          ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'border border-zinc-200 dark:border-zinc-800'
                      }`}
                    >
                      {ic}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="font-semibold">Catatan Khusus Barista / Dapur</label>
                <input
                  type="text"
                  value={customNote}
                  onChange={(e) => setCustomNote(e.target.value)}
                  placeholder="Contoh: Extra shot / pisah es..."
                  className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                />
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setCustomizingProduct(null)}
                className="rounded-xl border border-zinc-300 px-4 py-2 text-xs font-semibold dark:border-zinc-700"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={handleConfirmCustomProduct}
                className="rounded-xl bg-zinc-900 px-4 py-2 text-xs font-bold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                Masukkan ke Keranjang
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 3: DETAIL STRUK & CETAK BLUETOOTH ESC/POS            */}
      {/* ==================================================================== */}
      {receiptModalOrder && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between border-b border-zinc-100 pb-3 dark:border-zinc-800">
              <h3 className="text-base font-bold">Struk Pembayaran #{receiptModalOrder.order_no}</h3>
              <button type="button" onClick={() => setReceiptModalOrder(null)}>
                <X className="size-4" />
              </button>
            </div>

            <pre className="my-4 max-h-80 overflow-y-auto rounded-xl border border-zinc-200 bg-zinc-50 p-4 font-mono text-xs leading-relaxed dark:border-zinc-800 dark:bg-zinc-950">
              {formatEscPosReceiptText(receiptModalOrder, 'cashier')}
            </pre>

            <div className="flex flex-wrap items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => handlePrintOrder(receiptModalOrder, 'kitchen', true)}
                className="rounded-xl border border-zinc-300 px-3.5 py-2 text-xs font-semibold dark:border-zinc-700"
              >
                Cetak Tiket Dapur
              </button>
              <button
                type="button"
                onClick={() => handlePrintOrder(receiptModalOrder, 'cashier', true)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-zinc-900 px-4 py-2 text-xs font-bold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                <Printer className="size-3.5" />
                Cetak Struk Kasir (ESC/POS)
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 4: TAMBAH / EDIT PRODUK                              */}
      {/* ==================================================================== */}
      {isProductModalOpen && editingProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="text-base font-bold">
              {editingProduct.id ? 'Edit Produk' : 'Tambah Produk Baru'}
            </h3>
            <div className="mt-4 space-y-3 text-xs">
              <div>
                <label className="block text-zinc-500">Nama Produk</label>
                <input
                  type="text"
                  value={editingProduct.name}
                  onChange={(e) => setEditingProduct({ ...editingProduct, name: e.target.value })}
                  className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                />
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-zinc-500">Harga Jual (Rp)</label>
                  <input
                    type="number"
                    value={editingProduct.price}
                    onChange={(e) =>
                      setEditingProduct({ ...editingProduct, price: Number(e.target.value) || 0 })
                    }
                    className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-zinc-500">Harga Modal / HPP (Rp)</label>
                  <input
                    type="number"
                    value={editingProduct.cost_price}
                    onChange={(e) =>
                      setEditingProduct({ ...editingProduct, cost_price: Number(e.target.value) || 0 })
                    }
                    className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-zinc-500">Stok Saat Ini</label>
                  <input
                    type="number"
                    value={editingProduct.stock}
                    onChange={(e) =>
                      setEditingProduct({ ...editingProduct, stock: Number(e.target.value) || 0 })
                    }
                    className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
                <div>
                  <label className="block text-zinc-500">Batas Stok Menipis</label>
                  <input
                    type="number"
                    value={editingProduct.low_stock_threshold}
                    onChange={(e) =>
                      setEditingProduct({
                        ...editingProduct,
                        low_stock_threshold: Number(e.target.value) || 0,
                      })
                    }
                    className="num mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 dark:border-zinc-800 dark:bg-zinc-950"
                  />
                </div>
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setIsProductModalOpen(false)}
                className="rounded-xl border border-zinc-300 px-4 py-2 text-xs font-semibold dark:border-zinc-700"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!editingProduct.name.trim() || editingProduct.price <= 0) {
                    showNotice('error', 'Nama produk dan harga jual wajib valid.');
                    return;
                  }
                  if (backendMode === 'LIVE_SUPABASE') {
                    showNotice('error', 'Simpan produk belum terhubung ke Supabase. Tidak ada perubahan disimpan.');
                    return;
                  }
                  if (editingProduct.id) {
                    setProducts((prev) =>
                      prev.map((p) => (p.id === editingProduct.id ? editingProduct : p))
                    );
                  } else {
                    setProducts((prev) => [
                      ...prev,
                      { ...editingProduct, id: `prod-${Date.now()}` },
                    ]);
                  }
                  setIsProductModalOpen(false);
                  showNotice('success', `Produk "${editingProduct.name}" berhasil disimpan.`);
                }}
                className="rounded-xl bg-zinc-900 px-4 py-2 text-xs font-bold text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                Simpan Produk
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 5: KONFIRMASI PENGHAPUSAN PRODUK                     */}
      {/* ==================================================================== */}
      {deletingProduct && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs">
          <div className="w-full max-w-sm rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="text-base font-bold">Konfirmasi Hapus Produk</h3>
            <p className="mt-2 text-xs text-zinc-500">
              Apakah Anda yakin ingin menghapus <strong>{deletingProduct.name}</strong>? Tindakan ini memerlukan konfirmasi sesuai aturan keamanan.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDeletingProduct(null)}
                className="rounded-xl border border-zinc-300 px-4 py-2 text-xs font-semibold dark:border-zinc-700"
              >
                Batal
              </button>
              <button
                type="button"
                onClick={() => {
                  if (backendMode === 'LIVE_SUPABASE') {
                    showNotice('error', 'Hapus produk belum terhubung ke Supabase. Tidak ada perubahan disimpan.');
                    return;
                  }
                  setProducts((prev) => prev.filter((p) => p.id !== deletingProduct.id));
                  appendAuditLog('product.delete', 'products', deletingProduct.id, {
                    name: deletingProduct.name,
                  });
                  setDeletingProduct(null);
                  showNotice('info', `Produk ${deletingProduct.name} telah dihapus.`);
                }}
                className="rounded-xl bg-red-600 px-4 py-2 text-xs font-bold text-white hover:bg-red-700"
              >
                Ya, Hapus Produk
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* CENTERED DIALOG 6: HALAMAN LOGIN & VERIFIKASI SESI (LoginComposeScreen) */}
      {/* ==================================================================== */}
      {loginScreenOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs">
          <div className="max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-950">
            <div className="flex items-center justify-between border-b border-zinc-200 pb-4 dark:border-zinc-800">
              <div>
                <h2 className="text-xl font-bold tracking-tight">
                  KERETA MALAM CAFE — Halaman Login &amp; Sesi Staf
                </h2>
                <p className="text-xs text-zinc-500">
                  Sesuai implementasi <code className="font-mono">LoginComposeScreen</code> pada <code className="font-mono">AllScreens.kt</code> (Adaptif HP &amp; Tablet)
                </p>
              </div>
              <button
                type="button"
                onClick={() => setLoginScreenOpen(false)}
                className="inline-flex size-9 items-center justify-center rounded-lg border border-zinc-200 dark:border-zinc-800"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="mt-4 rounded-xl border border-zinc-200 bg-zinc-50 p-4 text-xs dark:border-zinc-800 dark:bg-zinc-900">
              <p className="font-bold text-amber-700 dark:text-amber-400">
                {backendMode === 'LIVE_SUPABASE'
                  ? `STATUS SUPABASE: TERVERIFIKASI (${supabaseConfig.projectId})`
                  : backendMode === 'LOCAL_SANDBOX_UI'
                    ? 'STATUS: MODE SIMULASI LOKAL TERLABEL (SUPABASE BELUM AKTIF)'
                    : 'STATUS SUPABASE: BELUM AKTIF (MENUNGGU VERIFIKASI PROJECT ID)'}
              </p>
              <p className="mt-1 text-zinc-600 dark:text-zinc-400">
                Login produksi hanya tersedia setelah konfigurasi backend diverifikasi oleh aplikasi. Jika layanan tidak tersedia, hubungi administrator sistem; jangan memasukkan kredensial rahasia ke layar ini.
              </p>
            </div>

            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {/* Panel 1: Login Supabase Auth */}
              <form
                onSubmit={async (e) => {
                  await handleSupabaseLogin(e);
                  setLoginScreenOpen(false);
                }}
                className="space-y-3 rounded-xl border border-zinc-200 p-5 dark:border-zinc-800"
              >
                <h3 className="text-sm font-bold">1. Login Akun Staf (Supabase Auth)</h3>
                <p className="text-xs text-zinc-500">
                  Memanggil <code className="font-mono">signInWithEmailPassword</code> dan memuat hak akses dari tabel <code className="font-mono">staff_profiles</code>.
                </p>
                <div>
                  <label className="block text-xs font-medium">Email Staf</label>
                  <input
                    type="email"
                    value={authEmail}
                    onChange={(e) => setAuthEmail(e.target.value)}
                    placeholder="kasir@keretamalam.id"
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-900"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium">Password</label>
                  <input
                    type="password"
                    value={authPassword}
                    onChange={(e) => setAuthPassword(e.target.value)}
                    placeholder="••••••••"
                    className="mt-1 w-full rounded-lg border border-zinc-200 bg-zinc-50 px-3 py-2 text-xs dark:border-zinc-800 dark:bg-zinc-900"
                  />
                </div>
                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full rounded-xl bg-zinc-900 py-2.5 text-xs font-bold text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900"
                >
                  {authLoading ? 'Memverifikasi...' : 'Masuk'}
                </button>
              </form>

              {/* Panel 2: Mode Simulasi Lokal Terlabel */}
              <div className="space-y-3 rounded-xl border border-zinc-200 p-5 dark:border-zinc-800">
                <h3 className="text-sm font-bold">2. Mode Simulasi Operasional Lokal</h3>
                <p className="text-xs text-zinc-500">
                  Pilih profil peran staf untuk menguji seluruh 18 modul POS &amp; RBAC secara lokal (berlabel <code className="font-mono">[SIMULASI LOKAL]</code>):
                </p>
                <div className="space-y-2">
                  {staffList.map((st) => (
                    <button
                      key={st.user_id}
                      type="button"
                      onClick={() => {
                        setCurrentStaff(st);
                        setBackendMode('LOCAL_SANDBOX_UI');
                        setLoginScreenOpen(false);
                        showNotice(
                          'info',
                          `Masuk Mode Simulasi Lokal sebagai ${st.full_name} (${st.role.toUpperCase()}).`
                        );
                      }}
                      className="flex w-full items-center justify-between rounded-xl border border-zinc-200 px-3.5 py-2.5 text-left text-xs font-semibold hover:bg-zinc-100 dark:border-zinc-800 dark:hover:bg-zinc-900"
                    >
                      <span>{st.full_name}</span>
                      <span className="rounded bg-zinc-200 px-2 py-0.5 font-mono text-[10px] uppercase dark:bg-zinc-800">
                        {st.role}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ==================================================================== */}
      {/* MODAL SOURCE CODE KOTLIN & EXPORT ANDROID STUDIO (.ZIP)              */}
      {/* ==================================================================== */}
      <KotlinProjectModal
        open={kotlinModalOpen}
        onClose={() => setKotlinModalOpen(false)}
        darkMode={darkMode}
      />
    </div>
  );
}
