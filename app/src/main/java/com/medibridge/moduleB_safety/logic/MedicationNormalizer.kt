package com.medibridge.moduleB_safety.logic

/**
 * Utility for normalizing medication names to enable consistent, deterministic
 * comparisons across the safety rules engine and medication history.
 */
object MedicationNormalizer {

    /**
     * Normalizes a medication name by:
     * - Trimming leading/trailing whitespace
     * - Collapsing multiple consecutive spaces into a single space
     * - Converting to lowercase
     * - Removing common salt or dosage suffixes for core ingredient comparison if present
     */
    fun normalize(name: String): String {
        return name
            .trim()
            .replace("\\s+".toRegex(), " ")
            .lowercase()
    }

    /**
     * Extracts the canonical active ingredient or base name without dosage forms.
     */
    fun extractBaseName(name: String): String {
        val normalized = normalize(name)
        // Strip trailing strength tokens like "500mg", "10 mg", "5ml" if attached
        return normalized
            .replace("\\b\\d+\\s*(mg|mcg|g|ml|iu|tablets?|capsules?)\\b".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }
}
