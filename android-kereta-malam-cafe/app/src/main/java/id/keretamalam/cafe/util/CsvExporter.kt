package id.keretamalam.cafe.util

import android.content.Context
import android.net.Uri
import id.keretamalam.cafe.data.model.Expense
import id.keretamalam.cafe.data.model.OrderWithItems
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Utilitas Ekspor CSV menggunakan Android Storage Access Framework (SAF).
 * Bekerja di seluruh versi Android (API 26–35) tanpa izin WRITE_EXTERNAL_STORAGE usang.
 */
object CsvExporter {

    private fun escapeCsvCell(value: Any?): String {
        val raw = value?.toString().orEmpty().replace("\"", "\"\"")
        return "\"$raw\""
    }

    fun writeSalesReportToUri(
        context: Context,
        targetUri: Uri,
        periodLabel: String,
        orders: List<OrderWithItems>
    ): Result<Unit> = runCatching {
        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("Laporan Penjualan Kereta Malam Cafe"))
        rows.add(listOf("Periode", periodLabel))
        rows.add(emptyList())
        rows.add(
            listOf(
                "No Order",
                "Waktu",
                "Sumber",
                "Meja",
                "Pelanggan",
                "Status",
                "Metode Bayar",
                "Subtotal (Rp)",
                "Diskon (Rp)",
                "Pajak/Service (Rp)",
                "Total (Rp)"
            )
        )
        orders.forEach { o ->
            rows.add(
                listOf(
                    "#${o.orderNo}",
                    o.createdAt,
                    o.source.uppercase(),
                    o.restaurantTable?.name ?: "Takeaway",
                    o.customerName ?: "-",
                    o.status.labelId,
                    o.paymentMethod ?: "-",
                    o.subtotal.toLong(),
                    o.discount.toLong(),
                    o.tax.toLong(),
                    o.total.toLong()
                )
            )
        }
        writeCsvRows(context, targetUri, rows)
    }

    fun writeFinancialReportToUri(
        context: Context,
        targetUri: Uri,
        fromDate: String,
        toDate: String,
        netSales: Double,
        cogs: Double,
        totalExpenses: Double,
        netProfit: Double,
        expenses: List<Expense>
    ): Result<Unit> = runCatching {
        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf("Laporan Keuangan & Laba Rugi Kereta Malam Cafe"))
        rows.add(listOf("Periode", fromDate, "sampai", toDate))
        rows.add(listOf("Penjualan Bersih (Rp)", netSales.toLong()))
        rows.add(listOf("HPP / COGS (Rp)", cogs.toLong()))
        rows.add(listOf("Total Pengeluaran (Rp)", totalExpenses.toLong()))
        rows.add(listOf("Laba / (Rugi) Bersih (Rp)", netProfit.toLong()))
        rows.add(emptyList())
        rows.add(listOf("Tanggal", "Kategori", "Keterangan", "Metode Pembayaran", "Nominal (Rp)"))
        expenses.forEach { e ->
            rows.add(listOf(e.expenseDate, e.category, e.description, e.paymentMethod, e.amount.toLong()))
        }
        writeCsvRows(context, targetUri, rows)
    }

    private fun writeCsvRows(context: Context, targetUri: Uri, rows: List<List<Any?>>) {
        val outputStream = context.contentResolver.openOutputStream(targetUri)
            ?: throw IllegalStateException("Gagal membuka lokasi penyimpanan file CSV.")
        outputStream.use { stream ->
            OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                // UTF-8 BOM agar Excel / Google Sheets membaca kolom Rupiah dengan benar
                writer.write("\uFEFF")
                rows.forEach { row ->
                    writer.write(row.joinToString(",") { escapeCsvCell(it) })
                    writer.write("\r\n")
                }
                writer.flush()
            }
        }
    }
}
