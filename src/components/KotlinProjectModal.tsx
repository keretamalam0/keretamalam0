import React, { useState } from 'react';
import JSZip from 'jszip';
import {
  Check,
  Code2,
  Copy,
  Download,
  FileCode,
  FolderTree,
  ShieldCheck,
  Terminal,
  X,
} from 'lucide-react';
import { ANDROID_PROJECT_FILES } from '../data/androidProjectFiles';

interface KotlinProjectModalProps {
  open: boolean;
  onClose: () => void;
  darkMode: boolean;
}

export const KotlinProjectModal: React.FC<KotlinProjectModalProps> = ({
  open,
  onClose,
}) => {
  const [selectedPath, setSelectedPath] = useState<string>(
    ANDROID_PROJECT_FILES.find((f) => f.path.endsWith('MainActivity.kt'))?.path ||
      ANDROID_PROJECT_FILES[0]?.path ||
      ''
  );
  const [copied, setCopied] = useState(false);
  const [isZipping, setIsZipping] = useState(false);
  const [activeTab, setActiveTab] = useState<'code' | 'audit' | 'build'>('code');

  if (!open) return null;

  const activeFile =
    ANDROID_PROJECT_FILES.find((f) => f.path === selectedPath) || ANDROID_PROJECT_FILES[0];

  const handleCopyCode = async () => {
    if (!activeFile) return;
    await navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleDownloadZip = async () => {
    setIsZipping(true);
    try {
      const zip = new JSZip();
      const folder = zip.folder('android-kereta-malam-cafe');
      if (folder) {
        ANDROID_PROJECT_FILES.forEach((file) => {
          if (file.base64Content) {
            folder.file(file.path, file.base64Content, {
              base64: true,
              unixPermissions: file.executable ? '755' : '644',
            });
          } else {
            folder.file(file.path, file.content, {
              unixPermissions: file.executable ? '755' : '644',
            });
          }
        });
      }
      const blob = await zip.generateAsync({
        type: 'blob',
        platform: 'UNIX',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'kereta-malam-cafe-android-native.zip';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } finally {
      setIsZipping(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-xs">
      <div className="flex h-[88vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl border border-zinc-200 bg-white text-zinc-900 shadow-2xl dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100">
        {/* Header */}
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
          <div>
            <h2 className="text-lg font-bold tracking-tight">
              Project Android Native — Kotlin + Jetpack Compose (MVVM)
            </h2>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              Package: <span className="font-mono">id.keretamalam.cafe</span> · Min SDK 26 · Target SDK 35 · {ANDROID_PROJECT_FILES.length} File Sumber Lengkap
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadZip}
              disabled={isZipping}
              className="inline-flex min-h-[40px] items-center gap-2 rounded-lg bg-zinc-900 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-zinc-800 disabled:opacity-50 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200 whitespace-nowrap shrink-0"
            >
              <Download className="size-4" />
              {isZipping ? 'Membuat ZIP...' : 'Unduh Project Android Studio (.zip)'}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex min-h-[40px] min-w-[40px] items-center justify-center rounded-lg border border-zinc-200 text-zinc-600 hover:bg-zinc-100 dark:border-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-900"
              aria-label="Tutup"
            >
              <X className="size-4" />
            </button>
          </div>
        </div>

        {/* Sub-nav Tabs */}
        <div className="flex items-center gap-2 border-b border-zinc-200 bg-zinc-50 px-6 py-2 dark:border-zinc-800 dark:bg-zinc-900/60">
          <button
            type="button"
            onClick={() => setActiveTab('code')}
            className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'code'
                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
            }`}
          >
            <Code2 className="size-3.5" />
            Source Code Kotlin & Gradle ({ANDROID_PROJECT_FILES.length} File)
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('audit')}
            className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'audit'
                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
            }`}
          >
            <ShieldCheck className="size-3.5" />
            Audit Skema Supabase & Status Modul
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('build')}
            className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'build'
                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-100'
            }`}
          >
            <Terminal className="size-3.5" />
            Panduan Build APK Debug
          </button>
        </div>

        {/* Body */}
        {activeTab === 'code' && (
          <div className="flex min-h-0 flex-1 overflow-hidden">
            {/* File Tree */}
            <div className="w-72 shrink-0 overflow-y-auto border-r border-zinc-200 bg-zinc-50/50 p-3 dark:border-zinc-800 dark:bg-zinc-900/30">
              <div className="mb-2 flex items-center gap-2 px-2 text-xs font-semibold text-zinc-500 dark:text-zinc-400">
                <FolderTree className="size-3.5" />
                <span>android-kereta-malam-cafe/</span>
              </div>
              <div className="space-y-1">
                {ANDROID_PROJECT_FILES.map((file) => {
                  const isSelected = file.path === activeFile?.path;
                  return (
                    <button
                      key={file.path}
                      type="button"
                      onClick={() => setSelectedPath(file.path)}
                      className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs transition-colors ${
                        isSelected
                          ? 'bg-zinc-900 font-semibold text-white dark:bg-zinc-100 dark:text-zinc-900'
                          : 'text-zinc-700 hover:bg-zinc-200/70 dark:text-zinc-300 dark:hover:bg-zinc-800/70'
                      }`}
                    >
                      <FileCode className="size-3.5 shrink-0" />
                      <span className="truncate font-mono">{file.path}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Code Viewer */}
            <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
              <div className="flex items-center justify-between border-b border-zinc-200 bg-zinc-100/70 px-4 py-2.5 dark:border-zinc-800 dark:bg-zinc-900">
                <span className="truncate font-mono text-xs font-medium text-zinc-700 dark:text-zinc-300">
                  android-kereta-malam-cafe/{activeFile?.path}
                </span>
                <button
                  type="button"
                  onClick={handleCopyCode}
                  className="inline-flex items-center gap-1.5 rounded-md border border-zinc-300 bg-white px-3 py-1 text-xs font-medium text-zinc-800 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-200 whitespace-nowrap shrink-0"
                >
                  {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
                  {copied ? 'Tersalin' : 'Salin Kode'}
                </button>
              </div>
              <pre className="min-h-0 flex-1 overflow-auto bg-zinc-950 p-4 font-mono text-xs leading-relaxed text-zinc-100">
                <code>{activeFile?.content}</code>
              </pre>
            </div>
          </div>
        )}

        {activeTab === 'audit' && (
          <div className="min-h-0 flex-1 space-y-6 overflow-y-auto p-6 text-sm">
            <div className="rounded-xl border border-zinc-200 p-5 dark:border-zinc-800">
              <h3 className="text-base font-bold">
                1. Temuan Pemeriksaan Referensi & Konflik Project ID Supabase
              </h3>
              <p className="mt-2 text-zinc-600 dark:text-zinc-400">
                Kami telah memeriksa kedua sumber referensi yang Anda cantumkan dan menemukan perbedaan Project ID berikut:
              </p>
              <div className="mt-4 grid gap-4 md:grid-cols-2">
                <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-4 dark:border-zinc-800 dark:bg-zinc-900">
                  <p className="text-xs font-semibold text-zinc-500">
                    SUMBER 1 · REPOSITORY GITHUB (supabase/config.toml)
                  </p>
                  <p className="mt-1 font-mono text-sm font-bold">rvaemgwtdvncfvaerhgr</p>
                  <p className="mt-1 font-mono text-xs text-zinc-500">
                    https://rvaemgwtdvncfvaerhgr.supabase.co
                  </p>
                </div>
                <div className="rounded-lg border border-zinc-200 bg-zinc-50 p-4 dark:border-zinc-800 dark:bg-zinc-900">
                  <p className="text-xs font-semibold text-zinc-500">
                    SUMBER 2 · WEBSITE DEPLOYMENT (pos.yuripratama246.workers.dev)
                  </p>
                  <p className="mt-1 font-mono text-sm font-bold">dbazoxwhhljpgtbnletc</p>
                  <p className="mt-1 font-mono text-xs text-zinc-500">
                    https://dbazoxwhhljpgtbnletc.supabase.co
                  </p>
                </div>
              </div>
              <p className="mt-3 text-xs text-zinc-600 dark:text-zinc-400">
                Sesuai instruksi Anda, aplikasi <strong>tidak memilih salah satunya secara asumsi</strong>. Status integrasi Supabase ditandai sebagai <strong>BELUM AKTIF</strong> sampai Anda memverifikasi Project ID, URL, dan Publishable/Anon Key pada menu <strong>Pengaturan Outlet</strong> atau file <code className="font-mono">local.properties</code>.
              </p>
            </div>

            <div className="rounded-xl border border-zinc-200 p-5 dark:border-zinc-800">
              <h3 className="text-base font-bold">
                2. Tabel, Trigger, dan RPC yang Diverifikasi dari Migrasi SQL Cafe Flow
              </h3>
              <div className="mt-3 grid gap-4 md:grid-cols-2">
                <div>
                  <p className="font-semibold">Tabel PostgreSQL / PostgREST:</p>
                  <ul className="mt-2 list-disc space-y-1 pl-5 font-mono text-xs text-zinc-600 dark:text-zinc-400">
                    <li>staff_profiles (user_id, owner_id, full_name, role, is_active, permissions)</li>
                    <li>categories (id, name, sort_order)</li>
                    <li>products (id, name, price, cost_price, category_id, stock, low_stock_threshold, show_on_menu, is_active)</li>
                    <li>product_addons &amp; product_customization_options</li>
                    <li>restaurant_tables (id, name, code)</li>
                    <li>orders &amp; order_items (cost_price_snapshot) &amp; payments</li>
                    <li>promotions, outlet_settings, staff_shifts, audit_logs, expenses</li>
                    <li>inventory_movements, warehouses, warehouse_stocks, stock_transfers</li>
                    <li>printer_settings, website_pages, website_locations, website_contacts, website_settings</li>
                  </ul>
                </div>
                <div>
                  <p className="font-semibold">RPC &amp; Trigger Database:</p>
                  <ul className="mt-2 list-disc space-y-1 pl-5 font-mono text-xs text-zinc-600 dark:text-zinc-400">
                    <li>RPC create_pos_order(p_order, p_items, p_payment) dengan idempotency_key</li>
                    <li>RPC create_qr_order(p_order, p_items)</li>
                    <li>RPC approve_qr_order_with_method(p_order_id, p_payment_method, p_cash_received)</li>
                    <li>RPC reject_qr_order(p_order_id)</li>
                    <li>Trigger trg_deduct_stock (AFTER INSERT ON order_items) — pengurangan stok atomik di server</li>
                    <li>Trigger trg_restore_stock (BEFORE UPDATE ON orders saat status = cancelled)</li>
                    <li>Trigger trg_add_loyalty_points_on_checkout</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'build' && (
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-6 text-sm">
            <div className="rounded-xl border border-zinc-200 p-5 dark:border-zinc-800">
              <h3 className="text-base font-bold">
                Langkah Build APK Debug di Android Studio / Terminal Lokal
              </h3>
              <p className="mt-2 text-zinc-600 dark:text-zinc-400">
                Lingkungan container preview di browser tidak menyertakan Android SDK (<code className="font-mono">ANDROID_HOME</code>), sehingga file biner <code className="font-mono">.apk</code> belum dikompilasi di container ini. Seluruh source code project Android Native Kotlin + Jetpack Compose sudah lengkap di direktori <code className="font-mono">/android-kereta-malam-cafe</code> dan dapat diunduh sebagai <code className="font-mono">.zip</code>.
              </p>
              <pre className="mt-4 overflow-x-auto rounded-xl bg-zinc-950 p-4 font-mono text-xs text-zinc-100">
{`# === DI WINDOWS (Command Prompt / PowerShell) ===
cd android-kereta-malam-cafe
copy local.properties.example local.properties
# Pastikan sdk.dir di local.properties atau ANDROID_HOME mengarah ke Android SDK Anda, serta JAVA_HOME ke JDK 17
gradlew.bat assembleDebug

# === DI LINUX / MACOS ===
cd android-kereta-malam-cafe
cp local.properties.example local.properties
chmod +x gradlew
./gradlew assembleDebug

# Lokasi output APK Debug yang siap diinstal:
# app/build/outputs/apk/debug/app-debug.apk`}
              </pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
