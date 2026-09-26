package com.medibridge.moduleB_safety.logic.fallback

import com.medibridge.moduleB_safety.logic.AiSafetyComparisonInput
import com.medibridge.moduleB_safety.logic.AiSafetyResponse
import com.medibridge.moduleB_safety.logic.FindingType

/**
 * Deterministic fallback generator for AI safety explanations.
 *
 * Provides polite, calm, patient-friendly, and non-alarming summaries based on
 * structured findings detected by the deterministic SafetyEngine.
 */
object FallbackSafetyAnalyzer {

    fun generatePoliteExplanation(input: AiSafetyComparisonInput): AiSafetyResponse {
        val findings = input.findings

        // Case 1: Unverified medication
        if (!input.crossVerified || findings.any { it.findingType == FindingType.UNVERIFIED }) {
            return AiSafetyResponse(
                flagged = true,
                severity = "MODERATE",
                title = "Medication details to verify",
                message = "We could not verify '${input.candidateMedicine.name}' against our configured safety knowledge base. Please check the spelling or ask a pharmacist to confirm the active formula.",
                recommendation = "Professional verification recommended before taking.",
                isFallback = true
            )
        }

        // Case 2: Multiple distinct findings
        if (input.hasMultipleFindings) {
            val highSeverity = findings.any { it.severity == "HIGH" }
            val medicineBList = findings.mapNotNull { it.medicineB?.name }.distinct().joinToString(", ")
            return AiSafetyResponse(
                flagged = true,
                severity = if (highSeverity) "HIGH" else "MODERATE",
                title = "Multiple medication points to review",
                message = "We identified multiple safety notes for ${input.candidateMedicine.name} against your current medications ($medicineBList). Please discuss these with your healthcare provider to ensure a coordinated regimen.",
                recommendation = "Comprehensive professional review recommended.",
                isFallback = true
            )
        }

        // Case 3: Single finding
        val singleFinding = findings.firstOrNull()
        if (singleFinding != null) {
            val medAName = singleFinding.medicineA.name
            val medBName = singleFinding.medicineB?.name ?: "an active medication"

            return when (singleFinding.findingType) {
                FindingType.DUPLICATE -> AiSafetyResponse(
                    flagged = true,
                    severity = singleFinding.severity,
                    title = "Possible duplicate prescription",
                    message = "We noticed $medAName appears to match your existing prescription for $medBName. Please confirm with your clinic or pharmacy whether this is a renewal or duplicate dose.",
                    recommendation = "Verify if this replaces your existing prescription.",
                    isFallback = true
                )

                FindingType.OVERLAP -> AiSafetyResponse(
                    flagged = true,
                    severity = singleFinding.severity,
                    title = "Therapeutic class overlap to review",
                    message = "We noticed $medAName shares a similar therapeutic purpose or pharmacological class with $medBName. Your physician can confirm if taking both simultaneously is intended.",
                    recommendation = "Consult your prescribing doctor or pharmacist.",
                    isFallback = true
                )

                FindingType.INTERACTION -> AiSafetyResponse(
                    flagged = true,
                    severity = singleFinding.severity,
                    title = "Medication interaction to review",
                    message = "We noticed a potential interaction between $medAName and $medBName. Please confirm this combination with your doctor or pharmacist so appropriate monitoring can be advised.",
                    recommendation = "Professional review recommended before co-administration.",
                    isFallback = true
                )

                FindingType.UNVERIFIED -> AiSafetyResponse(
                    flagged = true,
                    severity = "MODERATE",
                    title = "Medication verification required",
                    message = "We could not verify $medAName against the local reference database. Please have a clinician confirm this medicine.",
                    recommendation = "Professional verification recommended.",
                    isFallback = true
                )

                FindingType.SAFE -> AiSafetyResponse(
                    flagged = false,
                    severity = "LOW",
                    title = "No known conflicts noted",
                    message = "All cross-checks with your current medications completed without detecting duplicate therapies, class overlaps, or configured interactions.",
                    recommendation = "Take according to prescribed instructions.",
                    isFallback = true
                )
            }
        }

        // Default safe case
        return AiSafetyResponse(
            flagged = false,
            severity = "LOW",
            title = "No known conflicts noted",
            message = "We cross-referenced '${input.candidateMedicine.name}' against your active medication history and found no known conflicts in our configured rules.",
            recommendation = "Take according to prescribed instructions.",
            isFallback = true
        )
    }
}
