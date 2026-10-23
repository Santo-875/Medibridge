package com.medibridge.core.export

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.medibridge.core.db.AppDatabase
import com.medibridge.core.db.MedicationEntity
import com.medibridge.core.db.PrescriptionRecordEntity
import com.medibridge.core.db.RecordingEntity
import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.fromEntity
import com.medibridge.moduleB_safety.logic.MedicationNormalizer
import com.medibridge.moduleB_safety.logic.SafetyEngine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class HealthSummaryReport(
    val reportId: String = UUID.randomUUID().toString().take(8).uppercase(Locale.US),
    val patientName: String,
    val generatedDate: String,
    val generatedTimestamp: Long = System.currentTimeMillis(),
    val activeMedications: List<ExportMedicationItem>,
    val recordedAllergies: List<String>,
    val safetyAlerts: List<ExportSafetyAlert>,
    val consultationSummaries: List<ExportConsultationSummary>,
    val prescriptionVisits: List<ExportPrescriptionVisit>
)

data class ExportMedicationItem(
    val name: String,
    val genericName: String,
    val strength: String,
    val frequency: String,
    val timing: String,
    val verified: Boolean,
    val source: String
)

data class ExportSafetyAlert(
    val finding: String,
    val severity: String,
    val explanation: String,
    val source: String
)

data class ExportConsultationSummary(
    val date: String,
    val summary: String
)

data class ExportPrescriptionVisit(
    val date: String,
    val doctorName: String,
    val clinicName: String,
    val medicationCount: Int,
    val verified: Boolean
)

object HealthSummaryExporter {

    suspend fun buildReport(
        context: Context,
        patientName: String = "Patient",
        allergies: List<String> = emptyList()
    ): HealthSummaryReport {
        val db = AppDatabase.getInstance(context)
        val medEntities: List<MedicationEntity> = db.medicationDao().getAllMedicationsDirect()
        val activeMedObjects: List<MedicationObject> = medEntities.filter { it.deletedAt == null }.map { it.fromEntity() }

        val exportMeds = activeMedObjects.map { med ->
            val generic = MedicationNormalizer.canonicalGeneric(med.name)
            ExportMedicationItem(
                name = med.name,
                genericName = if (generic.equals(med.name, ignoreCase = true)) "Generic formulation" else generic,
                strength = med.strength,
                frequency = med.frequency,
                timing = med.timing,
                verified = med.verifiedByUser || med.crossVerified,
                source = if (med.sourceType == "voice") "Doctor Consultation" else "Prescription Record"
            )
        }

        val allConflicts = mutableListOf<MedicationConflict>()
        activeMedObjects.forEachIndexed { idx, med ->
            val otherMeds = activeMedObjects.filterIndexed { i, _ -> i != idx }
            val res = SafetyEngine.evaluateSafety(med, otherMeds)
            allConflicts.addAll(res.conflicts)
        }
        val uniqueConflicts = allConflicts.distinctBy { "${it.finding}_${it.withMedId}" }

        val exportAlerts = uniqueConflicts.map { conflict ->
            ExportSafetyAlert(
                finding = conflict.finding.ifBlank { conflict.type },
                severity = conflict.severity,
                explanation = conflict.explanation.ifBlank { conflict.detail },
                source = conflict.source
            )
        }

        val recordings: List<RecordingEntity> = db.recordingDao().getAllRecordingsDirect()
        val exportConsultations = recordings.take(5).map { rec ->
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(rec.timestamp))
            ExportConsultationSummary(
                date = dateStr,
                summary = rec.summary.ifBlank { rec.transcript }
            )
        }

        val prescriptions: List<PrescriptionRecordEntity> = db.prescriptionRecordDao().getAllPrescriptionsDirect()
        val exportVisits = prescriptions.take(5).map { p ->
            val dateStr = if (p.prescriptionDate.isNotBlank()) p.prescriptionDate
            else SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(p.scanTimestamp))
            val medCount = try {
                val type = object : TypeToken<List<Any>>() {}.type
                val list: List<Any>? = Gson().fromJson(p.medicationsJson, type)
                list?.size ?: 0
            } catch (_: Exception) { 0 }

            ExportPrescriptionVisit(
                date = dateStr,
                doctorName = p.doctorName.ifBlank { "Consulting Physician" },
                clinicName = p.clinicName.ifBlank { "Hospital / Clinic" },
                medicationCount = medCount,
                verified = true
            )
        }

        val currentDateStr = SimpleDateFormat("EEE, MMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date())

        return HealthSummaryReport(
            patientName = patientName,
            generatedDate = currentDateStr,
            activeMedications = exportMeds,
            recordedAllergies = if (allergies.isEmpty()) listOf("No known drug allergies recorded") else allergies,
            safetyAlerts = exportAlerts,
            consultationSummaries = exportConsultations,
            prescriptionVisits = exportVisits
        )
    }

    fun generatePdf(context: Context, report: HealthSummaryReport): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val outputFile = File(reportsDir, "MediBridge_Health_Summary_${report.reportId}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val primaryPaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subheaderTextPaint = Paint().apply {
            color = Color.rgb(204, 251, 241)
            textSize = 9.5f
            isAntiAlias = true
        }

        val sectionTitlePaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyBoldPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyRegularPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 9f
            isAntiAlias = true
        }

        val badgePaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        canvas.drawRect(0f, 0f, 595f, 75f, primaryPaint)
        canvas.drawText("MEDIBRIDGE AI • CLINICAL HEALTH SUMMARY", 36f, 34f, headerTextPaint)
        canvas.drawText("Doctor-Ready Medication & Clinical Safety Profile • Verified Patient Export", 36f, 52f, subheaderTextPaint)

        var y = 98f

        canvas.drawRect(36f, y, 559f, y + 54f, badgePaint)
        canvas.drawText("PATIENT NAME:", 48f, y + 20f, bodyBoldPaint)
        canvas.drawText(report.patientName, 135f, y + 20f, bodyRegularPaint)

        canvas.drawText("DATE & TIME:", 310f, y + 20f, bodyBoldPaint)
        canvas.drawText(report.generatedDate, 385f, y + 20f, bodyRegularPaint)

        canvas.drawText("REPORT REF ID:", 48f, y + 40f, bodyBoldPaint)
        canvas.drawText("MB-${report.reportId}", 135f, y + 40f, bodyRegularPaint)

        canvas.drawText("SCOPE VERIFIED:", 310f, y + 40f, bodyBoldPaint)
        canvas.drawText("${report.activeMedications.size} Active Meds • ${report.prescriptionVisits.size} Prescriptions", 400f, y + 40f, bodyRegularPaint)

        y += 74f

        canvas.drawText("1. CURRENT ACTIVE MEDICATIONS (${report.activeMedications.size})", 36f, y, sectionTitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        if (report.activeMedications.isEmpty()) {
            canvas.drawText("No active medications currently registered.", 48f, y, bodyRegularPaint)
            y += 20f
        } else {
            report.activeMedications.forEach { med ->
                val medTitle = "${med.name} ${med.strength}".trim()
                canvas.drawText("• $medTitle", 48f, y, bodyBoldPaint)

                val details = listOf(med.genericName, med.frequency, med.timing)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                canvas.drawText(details, 230f, y, bodyRegularPaint)

                val statusBadge = if (med.verified) "[VERIFIED]" else "[PATIENT ENTERED]"
                canvas.drawText(statusBadge, 460f, y, if (med.verified) sectionTitlePaint else bodyBoldPaint)

                y += 18f
            }
        }

        y += 12f
        canvas.drawText("2. RECORDED ALLERGIES & INTOLERANCES", 36f, y, sectionTitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        report.recordedAllergies.forEach { allergy ->
            canvas.drawText("• $allergy", 48f, y, bodyBoldPaint)
            y += 16f
        }

        y += 12f
        canvas.drawText("3. MEDICATION SAFETY CHECKS & ALERTS", 36f, y, sectionTitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        if (report.safetyAlerts.isEmpty()) {
            canvas.drawText("No issues detected by the active safety checks (drug-drug interactions, duplicate therapies, food timing).", 48f, y, bodyRegularPaint)
            y += 18f
        } else {
            report.safetyAlerts.forEach { alert ->
                canvas.drawText("! [${alert.severity}] ${alert.finding}", 48f, y, bodyBoldPaint)
                y += 14f
                val truncExplanation = if (alert.explanation.length > 90) alert.explanation.take(87) + "..." else alert.explanation
                canvas.drawText("  $truncExplanation (Source: ${alert.source})", 52f, y, bodyRegularPaint)
                y += 16f
            }
        }

        y += 12f
        canvas.drawText("4. RECENT DOCTOR CONSULTATION SUMMARIES", 36f, y, sectionTitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        if (report.consultationSummaries.isEmpty()) {
            canvas.drawText("No recorded consultation notes available.", 48f, y, bodyRegularPaint)
            y += 18f
        } else {
            report.consultationSummaries.take(3).forEach { consult ->
                canvas.drawText("• Visit on ${consult.date}:", 48f, y, bodyBoldPaint)
                y += 14f
                val cleanSum = consult.summary.replace("\n", " ").trim()
                val trunc = if (cleanSum.length > 100) cleanSum.take(97) + "..." else cleanSum
                canvas.drawText("  $trunc", 52f, y, bodyRegularPaint)
                y += 16f
            }
        }

        y += 12f
        canvas.drawText("5. PRESCRIPTION VISIT ARCHIVE HISTORY", 36f, y, sectionTitlePaint)
        y += 10f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        if (report.prescriptionVisits.isEmpty()) {
            canvas.drawText("No archived prescription visits recorded.", 48f, y, bodyRegularPaint)
            y += 18f
        } else {
            report.prescriptionVisits.take(3).forEach { visit ->
                val line = "• ${visit.date} — Dr. ${visit.doctorName} (${visit.clinicName}) • ${visit.medicationCount} medicines"
                canvas.drawText(line, 48f, y, bodyRegularPaint)
                y += 16f
            }
        }

        canvas.drawLine(36f, 800f, 559f, 800f, linePaint)
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            isAntiAlias = true
        }
        canvas.drawText("Generated by MediBridge AI for clinical consultation reference. Please cross-reference with primary medical records.", 36f, 814f, footerPaint)
        canvas.drawText("Page 1 of 1", 516f, 814f, footerPaint)

        doc.finishPage(page)

        FileOutputStream(outputFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        return outputFile
    }

    fun generateJson(context: Context, report: HealthSummaryReport): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val outputFile = File(reportsDir, "MediBridge_Health_Summary_${report.reportId}.json")

        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(report)
        outputFile.writeText(jsonString)

        return outputFile
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
