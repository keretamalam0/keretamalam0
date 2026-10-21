package id.keretamalam.cafe.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import id.keretamalam.cafe.data.model.OrderWithItems
import id.keretamalam.cafe.data.model.RupiahFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class PrinterRole(val key: String, val labelId: String) {
    CASHIER("cashier", "Printer Struk Kasir"),
    KITCHEN("kitchen", "Printer Tiket Dapur")
}

data class SavedBluetoothPrinter(
    val role: PrinterRole,
    val name: String,
    val address: String,
    val autoPrint: Boolean = false
)

/**
 * Modul Native Android untuk Dual Printer Bluetooth Thermal ESC/POS (58mm / 80mm).
 *
 * Fitur:
 * 1. Penanganan izin Bluetooth sesuai versi Android (API <= 30 vs Android 12+ API 31+ BLUETOOTH_SCAN & BLUETOOTH_CONNECT).
 * 2. Mendukung konfigurasi dua printer terpisah: Printer Struk Kasir & Printer Tiket Dapur.
 * 3. Menggunakan RFCOMM SPP UUID standar (`00001101-0000-1000-8000-00805F9B34FB`).
 * 4. Mencegah satu transaksi tercetak berkali-kali tanpa sengaja (`printedOrderReceipts` & `printedKitchenTickets` + `Mutex`).
 * 5. Menjaga format Rupiah (Rp), karakter Bahasa Indonesia, nama produk, kuantitas, dan total tetap rapi.
 *
 * Catatan: Pengujian fisik dengan perangkat printer thermal ESC/POS tetap diperlukan saat APK diinstal di HP/Tablet.
 */
class EscPosBluetoothManager(private val context: Context) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PREF_PRINTER = "kereta_malam_printer_prefs"
    }

    private val printMutex = Mutex()
    private val printedReceipts = ConcurrentHashMap.newKeySet<String>()
    private val printedKitchenTickets = ConcurrentHashMap.newKeySet<String>()

    private val prefs = context.getSharedPreferences(PREF_PRINTER, Context.MODE_PRIVATE)

    fun requiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return requiredRuntimePermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun listPairedPrinters(): Result<List<BluetoothDevice>> {
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Izin Bluetooth belum diberikan. Berikan izin BLUETOOTH_CONNECT & BLUETOOTH_SCAN."))
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = bluetoothManager?.adapter
            ?: return Result.failure(IllegalStateException("Perangkat Android ini tidak memiliki adapter Bluetooth."))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth sedang mati. Nyalakan Bluetooth terlebih dahulu."))
        }

        return Result.success(adapter.bondedDevices?.toList().orEmpty())
    }

    @SuppressLint("MissingPermission")
    fun forgetPairedDevice(address: String): Result<Unit> {
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Izin Bluetooth belum diberikan."))
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Result.failure(IllegalStateException("Bluetooth tidak tersedia."))
        val device = runCatching { bluetoothManager.adapter.getRemoteDevice(address) }.getOrElse {
            return Result.failure(it)
        }
        return runCatching {
            val removeBond = device.javaClass.getMethod("removeBond")
            val accepted = removeBond.invoke(device) as? Boolean ?: false
            check(accepted) { "Android menolak permintaan melupakan perangkat. Hapus pasangan lewat Pengaturan Bluetooth Android." }
        }
    }

    fun savePrinter(role: PrinterRole, name: String, address: String, autoPrint: Boolean) {
        prefs.edit()
            .putString("${role.key}_name", name)
            .putString("${role.key}_address", address)
            .putBoolean("${role.key}_auto", autoPrint)
            .apply()
    }

    fun clearPrinter(role: PrinterRole) {
        prefs.edit()
            .remove("${role.key}_name")
            .remove("${role.key}_address")
            .remove("${role.key}_auto")
            .apply()
    }

    fun getSavedPrinter(role: PrinterRole): SavedBluetoothPrinter? {
        val address = prefs.getString("${role.key}_address", null) ?: return null
        val name = prefs.getString("${role.key}_name", "Printer Thermal") ?: "Printer Thermal"
        val auto = prefs.getBoolean("${role.key}_auto", false)
        return SavedBluetoothPrinter(role = role, name = name, address = address, autoPrint = auto)
    }

    private fun formatWibTimestamp(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val zone = java.time.ZoneId.of("Asia/Jakarta")
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        val instant = runCatching { java.time.Instant.parse(raw) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        if (instant != null) return formatter.withZone(zone).format(instant)
        val local = runCatching { java.time.LocalDateTime.parse(raw.replace(' ', 'T')) }.getOrNull()
        return local?.format(formatter) ?: raw
    }

    suspend fun testPrint(role: PrinterRole): Result<Unit> = withContext(Dispatchers.IO) {
        val printer = getSavedPrinter(role)
            ?: return@withContext Result.failure(IllegalStateException("${role.labelId} belum dipilih."))
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("id", "ID")).format(Date())
        val payload = buildString {
            appendLine("KERETA MALAM CAFE")
            appendLine(if (role == PrinterRole.KITCHEN) "TEST TIKET DAPUR" else "TEST STRUK KASIR")
            appendLine("--------------------------------")
            appendLine("Printer : ${printer.name}")
            appendLine("MAC     : ${printer.address}")
            appendLine("Status  : Koneksi Bluetooth OK")
            appendLine("Waktu   : $dateStr")
            appendLine("--------------------------------")
        }
        sendEscPosPayload(printer.address, payload)
    }

    suspend fun printCashierReceipt(
        order: OrderWithItems,
        forceReprint: Boolean = false
    ): Result<Unit> = printMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!forceReprint && printedReceipts.contains(order.id)) {
                return@withContext Result.failure(
                    IllegalStateException("Struk Order #${order.orderNo} sudah dicetak. Gunakan tombol Cetak Ulang jika ingin mencetak lagi.")
                )
            }
            val printer = getSavedPrinter(PrinterRole.CASHIER)
                ?: return@withContext Result.failure(IllegalStateException("Printer Struk Kasir belum dikonfigurasi."))

            val payment = order.payments.firstOrNull()
            val payload = buildString {
                appendLine("KERETA MALAM CAFE")
                appendLine("STRUK PEMBAYARAN")
                appendLine("--------------------------------")
                appendLine("Order #${order.orderNo}")
                appendLine("Waktu: ${formatWibTimestamp(order.createdAt)} WIB")
                order.restaurantTable?.name?.let { appendLine("Meja : $it") }
                order.customerName?.let { appendLine("Plgn : $it") }
                appendLine("--------------------------------")
                order.orderItems.forEach { item ->
                    val options = item.note.orEmpty().takeIf { it.startsWith("KMOPT|") }
                        ?.removePrefix("KMOPT|")?.split(";")?.mapNotNull { raw ->
                            val parts = raw.split("|", limit = 3)
                            if (parts.size != 3) null else Triple(parts[0], parts[1], parts[2].toDoubleOrNull() ?: 0.0)
                        }.orEmpty()
                    val surcharge = options.sumOf { it.third }
                    appendLine(item.productName)
                    appendLine("  ${item.quantity} x ${RupiahFormatter.format((item.unitPrice - surcharge).coerceAtLeast(0.0))}")
                    options.filter { it.third > 0 }.forEach { (group, name, delta) ->
                        val label = when (group) { "size" -> "Cup"; "extra" -> "Tambahan"; "sugar" -> "Gula"; "ice" -> "Es/suhu"; "portion" -> "Porsi"; "spice" -> "Pedas"; "rice" -> "Nasi"; "cooking" -> "Kematangan"; "side" -> "Lauk"; "sauce" -> "Saus"; "sambal" -> "Sambal"; "topping" -> "Topping"; else -> group }
                        appendLine("  $label $name +${RupiahFormatter.format(delta)}")
                    }
                    appendLine("  Total: ${RupiahFormatter.format(item.lineTotal)}")
                }
                appendLine("--------------------------------")
                appendLine("Subtotal : ${RupiahFormatter.format(order.subtotal)}")
                if (order.discount > 0) {
                    appendLine("Diskon   : -${RupiahFormatter.format(order.discount)}")
                }
                if (order.tax > 0) {
                    appendLine("Pajak/Svc: ${RupiahFormatter.format(order.tax)}")
                }
                appendLine("TOTAL    : ${RupiahFormatter.format(order.total)}")
                payment?.let {
                    appendLine("Bayar    : ${it.method.uppercase()}")
                    it.cashReceived?.let { cash -> appendLine("Tunai    : ${RupiahFormatter.format(cash)}") }
                    it.changeAmount?.let { chg -> appendLine("Kembali  : ${RupiahFormatter.format(chg)}") }
                }
                appendLine("--------------------------------")
                appendLine("Terima kasih atas kunjungan Anda!")
            }

            val result = sendEscPosPayload(printer.address, payload)
            if (result.isSuccess) {
                printedReceipts.add(order.id)
            }
            result
        }
    }

    suspend fun printKitchenTicket(
        order: OrderWithItems,
        forceReprint: Boolean = false
    ): Result<Unit> = printMutex.withLock {
        withContext(Dispatchers.IO) {
            if (!forceReprint && printedKitchenTickets.contains(order.id)) {
                return@withContext Result.failure(
                    IllegalStateException("Tiket Dapur Order #${order.orderNo} sudah dicetak.")
                )
            }
            val printer = getSavedPrinter(PrinterRole.KITCHEN)
                ?: return@withContext Result.failure(IllegalStateException("Printer Tiket Dapur belum dikonfigurasi."))

            val payload = buildString {
                appendLine("KERETA MALAM CAFE")
                appendLine("TIKET PESANAN DAPUR / BAR")
                appendLine("--------------------------------")
                appendLine("ORDER #${order.orderNo}")
                order.restaurantTable?.name?.let { appendLine("MEJA : $it") }
                appendLine("Waktu: ${order.createdAt}")
                appendLine("--------------------------------")
                order.orderItems.forEach { item ->
                    appendLine("${item.quantity}x ${item.productName}")
                    if (!item.note.isNullOrBlank()) {
                        appendLine("   Catatan: ${item.note}")
                    }
                }
                if (!order.notes.isNullOrBlank()) {
                    appendLine("--------------------------------")
                    appendLine("CATATAN ORDER: ${order.notes}")
                }
                appendLine("--------------------------------")
                appendLine("Mohon segera disiapkan.")
            }

            val result = sendEscPosPayload(printer.address, payload)
            if (result.isSuccess) {
                printedKitchenTickets.add(order.id)
            }
            result
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendEscPosPayload(macAddress: String, textContent: String): Result<Unit> {
        if (!hasBluetoothPermissions()) {
            return Result.failure(SecurityException("Izin Bluetooth belum diberikan."))
        }
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
            ?: return Result.failure(IllegalStateException("Adapter Bluetooth tidak tersedia."))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth tidak aktif."))
        }

        val device = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalStateException("Alamat MAC Printer tidak valid: $macAddress"))
        }

        var socket: BluetoothSocket? = null
        return try {
            adapter.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val outputStream = socket.outputStream

            // ESC/POS Initialize (\x1B@), Left Align (\x1Ba\x00), ASCII safe Indonesian text, Feed & Partial Cut (\x1DVA\x00)
            val initBytes = byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x00)
            val sanitizedText = textContent.replace(Regex("[^\\x00-\\x7F\\n\\r\\t]"), "?")
            val textBytes = sanitizedText.toByteArray(Charset.forName("US-ASCII"))
            val feedAndCutBytes = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x41, 0x00)

            outputStream.write(initBytes)
            outputStream.write(textBytes)
            outputStream.write(feedAndCutBytes)
            outputStream.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IOException("Gagal terhubung/mencetak ke printer (${device.name ?: macAddress}): ${e.message}", e))
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }
}
