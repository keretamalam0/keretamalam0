export type StaffRole = 'super_admin' | 'owner' | 'manager' | 'cashier';

export type PermissionKey =
  | 'dashboard'
  | 'orders'
  | 'payments'
  | 'products'
  | 'inventory'
  | 'tables'
  | 'reports'
  | 'customers'
  | 'promotions'
  | 'settings';

export interface StaffProfile {
  user_id: string;
  owner_id: string;
  full_name: string;
  email?: string;
  role: StaffRole;
  is_active: boolean;
  permissions: Record<PermissionKey, boolean>;
}

export interface Category {
  id: string;
  name: string;
  sort_order: number;
}

export interface ProductAddon {
  id: string;
  product_id: string;
  name: string;
  price: number;
  is_active: boolean;
}

export interface Product {
  id: string;
  name: string;
  description: string | null;
  price: number;
  cost_price: number;
  image_url: string | null;
  category_id: string | null;
  stock: number;
  low_stock_threshold: number;
  show_on_menu: boolean;
  is_active: boolean;
  created_at?: string;
}

export interface RestaurantTable {
  id: string;
  name: string;
  code: string;
}

export type OrderStatus = 'new' | 'preparing' | 'ready' | 'completed' | 'cancelled';
export type PaymentStatus = 'unpaid' | 'awaiting_confirmation' | 'paid' | 'rejected';

export interface OrderItem {
  id: string;
  order_id: string;
  product_id: string | null;
  product_name: string;
  unit_price: number;
  quantity: number;
  note: string | null;
  line_total: number;
  cost_price_snapshot?: number | null;
}

export interface PaymentRecord {
  id?: string;
  method: string;
  amount: number;
  cash_received: number | null;
  change_amount: number | null;
}

export interface OrderWithItems {
  id: string;
  order_no: number;
  source: 'pos' | 'qr';
  status: OrderStatus;
  table_id: string | null;
  customer_name: string | null;
  notes: string | null;
  subtotal: number;
  discount: number;
  tax: number;
  total: number;
  is_paid: boolean;
  payment_method: string | null;
  payment_status: PaymentStatus;
  created_at: string;
  updated_at: string;
  order_items: OrderItem[];
  payments?: PaymentRecord[];
  restaurant_tables?: { name: string } | null;
}

export interface Promotion {
  id: string;
  name: string;
  type: 'percentage' | 'fixed';
  value: number;
  min_purchase: number;
  min_quantity: number;
  scope: 'order' | 'product';
  product_id: string | null;
  active: boolean;
  campaign_type?: 'promotion' | 'voucher' | 'bundle' | 'campaign';
  eligible_for?: 'all' | 'product';
  allow_customer?: boolean;
  allow_cashier?: boolean;
}

export interface OutletSettings {
  name: string;
  address: string;
  phone: string;
  tax_rate: number;
  service_rate: number;
  tax_enabled: boolean;
  service_enabled: boolean;
  rounding: number;
  cash_enabled: boolean;
  card_enabled: boolean;
  qris_enabled: boolean;
  transfer_enabled: boolean;
  ewallet_enabled: boolean;
  app_language: 'id' | 'en' | 'zh' | 'th' | 'vi';
  public_web_base_url: string;
}

export interface StaffShift {
  id: string;
  staff_user_id: string;
  staff_name: string;
  opening_cash: number;
  closing_cash: number | null;
  expected_cash: number | null;
  status: 'open' | 'closed';
  notes: string | null;
  audit_verified?: boolean;
  audit_note?: string | null;
  opened_at: string;
  closed_at: string | null;
}

export interface AuditLog {
  id: string;
  actor_user_id: string;
  actor_name: string;
  action: string;
  entity_type: string;
  entity_id: string;
  details: string;
  created_at: string;
}

export interface Expense {
  id: string;
  amount: number;
  category: string;
  description: string;
  expense_date: string;
  payment_method: string;
  created_at: string;
}

export interface InventoryMovement {
  id: string;
  product_id: string;
  product_name: string;
  warehouse_name: string;
  type: 'in' | 'out' | 'adjustment';
  quantity: number;
  reason: string;
  actor_name: string;
  created_at: string;
}

export type PrinterRole = 'cashier' | 'kitchen';

export interface SavedPrinterDevice {
  role: PrinterRole;
  name: string;
  address: string;
  paperWidth: '58mm' | '80mm';
  autoPrint: boolean;
  connected: boolean;
}

export interface WebsiteCmsData {
  site_name: string;
  tagline: string;
  hero_headline: string;
  about_summary: string;
  whatsapp: string;
  email: string;
  instagram: string;
  address: string;
  opening_hours: string;
  pages: Array<{ id: string; slug: string; title: string; status: 'published' | 'draft'; updated_at: string }>;
  locations: Array<{ id: string; name: string; address: string; phone: string; hours: string; is_active: boolean }>;
}

export interface CartItem {
  cartItemId: string;
  product: Product;
  quantity: number;
  note: string;
  sugarLevel: string;
  iceLevel: string;
  cupSize: string;
  addons: Array<{ name: string; price: number }>;
}

export type ModuleId =
  | 'dashboard'
  | 'ai'
  | 'kasir'
  | 'pesanan'
  | 'history'
  | 'shift-kasir'
  | 'audit-shift'
  | 'activity-log'
  | 'produk'
  | 'meja'
  | 'inventory'
  | 'promosi'
  | 'keuangan'
  | 'laporan'
  | 'pegawai'
  | 'pengaturan'
  | 'printer'
  | 'website-management';
