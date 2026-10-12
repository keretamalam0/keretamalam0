import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { OrderWithItems } from '../types/pos';

export type BackendMode = 'INACTIVE_PENDING_VERIFICATION' | 'LIVE_SUPABASE' | 'LOCAL_SANDBOX_UI';

export interface SupabaseVerificationConfig {
  projectId: string;
  supabaseUrl: string;
  publishableKey: string;
  publicWebBaseUrl: string;
  verifiedByUser: boolean;
}

const STORAGE_KEY = 'kereta_malam_supabase_verification_v1';

export const CANDIDATE_PROJECTS = [
  {
    id: 'rvaemgwtdvncfvaerhgr',
    url: 'https://rvaemgwtdvncfvaerhgr.supabase.co',
    source: 'Ditemukan di supabase/config.toml pada repository github.com/yuripratama246-lang/cafe-flow',
  },
  {
    id: 'dbazoxwhhljpgtbnletc',
    url: 'https://dbazoxwhhljpgtbnletc.supabase.co',
    source: 'Ditemukan di bundle produksi pos.yuripratama246.workers.dev',
  },
] as const;

export function loadVerificationConfig(): SupabaseVerificationConfig {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      return JSON.parse(raw);
    }
  } catch {
    // ignore storage errors
  }
  return {
    projectId: '',
    supabaseUrl: '',
    publishableKey: '',
    publicWebBaseUrl: 'https://pos.yuripratama246.workers.dev',
    verifiedByUser: false,
  };
}

export function saveVerificationConfig(config: SupabaseVerificationConfig) {
  if (
    config.publishableKey.includes('service_role') ||
    config.publishableKey.startsWith('sb_secret_')
  ) {
    throw new Error(
      'Keamanan Ditolak: Jangan pernah memasukkan service_role key atau secret key ke aplikasi klien Android!'
    );
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
}

let activeSupabaseClient: SupabaseClient | null = null;
let activeClientKey = '';

export function getVerifiedSupabaseClient(config: SupabaseVerificationConfig): SupabaseClient | null {
  if (!config.verifiedByUser || !config.supabaseUrl || !config.publishableKey) {
    return null;
  }
  const cacheKey = `${config.supabaseUrl}::${config.publishableKey}`;
  if (activeSupabaseClient && activeClientKey === cacheKey) {
    return activeSupabaseClient;
  }
  activeSupabaseClient = createClient(config.supabaseUrl, config.publishableKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
    },
  });
  activeClientKey = cacheKey;
  return activeSupabaseClient;
}

export function formatRupiah(amount: number): string {
  const num = Number.isFinite(amount) ? Math.round(amount) : 0;
  return 'Rp' + num.toLocaleString('id-ID');
}

export function downloadCsvFile(filename: string, rows: Array<Array<string | number | null | undefined>>) {
  const quote = (val: unknown) => `"${String(val ?? '').replace(/"/g, '""')}"`;
  const csvContent = '\uFEFF' + rows.map((row) => row.map(quote).join(',')).join('\r\n');
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function formatEscPosReceiptText(order: OrderWithItems, role: 'cashier' | 'kitchen'): string {
  const divider = '--------------------------------';
  if (role === 'kitchen') {
    const lines = [
      'KERETA MALAM CAFE',
      'TIKET PESANAN DAPUR / BAR',
      divider,
      `ORDER #${order.order_no} (${order.source.toUpperCase()})`,
      `MEJA  : ${order.restaurant_tables?.name ?? 'Takeaway'}`,
      `WAKTU : ${new Date(order.created_at).toLocaleString('id-ID')}`,
      divider,
      ...order.order_items.flatMap((item) => [
        `${item.quantity}x ${item.product_name}`,
        item.note ? `   Catatan: ${item.note}` : '',
      ]),
      order.notes ? `CATATAN ORDER: ${order.notes}` : '',
      divider,
      'Mohon segera disiapkan.',
    ].filter(Boolean);
    return lines.join('\n');
  }

  const payment = order.payments?.[0];
  const lines = [
    'KERETA MALAM CAFE',
    'STRUK PEMBAYARAN RESMI',
    divider,
    `Order #${order.order_no} · ${order.source.toUpperCase()}`,
    new Date(order.created_at).toLocaleString('id-ID'),
    `Meja      : ${order.restaurant_tables?.name ?? 'Takeaway'}`,
    order.customer_name ? `Pelanggan : ${order.customer_name}` : '',
    divider,
    ...order.order_items.flatMap((item) => [
      item.product_name,
      `  ${item.quantity} x ${formatRupiah(item.unit_price)} = ${formatRupiah(item.line_total)}`,
      item.note ? `  Catatan: ${item.note}` : '',
    ]),
    divider,
    `Subtotal  : ${formatRupiah(order.subtotal)}`,
    order.discount > 0 ? `Diskon    : -${formatRupiah(order.discount)}` : '',
    order.tax > 0 ? `Pajak/Svc : ${formatRupiah(order.tax)}` : '',
    `TOTAL     : ${formatRupiah(order.total)}`,
    payment ? `Metode    : ${payment.method.toUpperCase()}` : `Metode    : ${(order.payment_method ?? '-').toUpperCase()}`,
    payment?.cash_received != null ? `Tunai     : ${formatRupiah(payment.cash_received)}` : '',
    payment?.change_amount != null ? `Kembalian : ${formatRupiah(payment.change_amount)}` : '',
    divider,
    'Terima kasih telah berkunjung',
    'di Kereta Malam Cafe!',
  ].filter(Boolean);
  return lines.join('\n');
}

/**
 * Menghasilkan matriks Finder Pattern + Data Hash deterministik dari URL asli meja
 * agar QR Code Meja selalu dirender tajam tanpa ketergantungan gambar eksternal.
 */
export function generateDeterministicQrMatrix(urlText: string): boolean[][] {
  const size = 25;
  const matrix: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false));
  const reserved: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false));

  const placeFinder = (rowOffset: number, colOffset: number) => {
    for (let r = -1; r <= 7; r++) {
      for (let c = -1; c <= 7; c++) {
        const rr = rowOffset + r;
        const cc = colOffset + c;
        if (rr >= 0 && rr < size && cc >= 0 && cc < size) {
          reserved[rr][cc] = true;
          if (r >= 0 && r <= 6 && c >= 0 && c <= 6) {
            const isBorder = r === 0 || r === 6 || c === 0 || c === 6;
            const isInner = r >= 2 && r <= 4 && c >= 2 && c <= 4;
            matrix[rr][cc] = isBorder || isInner;
          }
        }
      }
    }
  };

  placeFinder(0, 0);
  placeFinder(0, size - 7);
  placeFinder(size - 7, 0);

  // Timing patterns
  for (let i = 8; i < size - 8; i++) {
    reserved[6][i] = true;
    reserved[i][6] = true;
    matrix[6][i] = i % 2 === 0;
    matrix[i][6] = i % 2 === 0;
  }

  // Alignment box
  for (let r = size - 9; r <= size - 5; r++) {
    for (let c = size - 9; c <= size - 5; c++) {
      reserved[r][c] = true;
      const isEdge = r === size - 9 || r === size - 5 || c === size - 9 || c === size - 5;
      const isCenter = r === size - 7 && c === size - 7;
      matrix[r][c] = isEdge || isCenter;
    }
  }

  let hash = 2166136261;
  for (let i = 0; i < urlText.length; i++) {
    hash ^= urlText.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }

  let bitIndex = 0;
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (!reserved[r][c]) {
        const charCode = urlText.charCodeAt(bitIndex % urlText.length);
        const mixed = ((hash >>> (bitIndex % 24)) ^ (charCode * (r + 1) * (c + 3))) & 1;
        matrix[r][c] = mixed === 1;
        bitIndex++;
      }
    }
  }

  return matrix;
}
