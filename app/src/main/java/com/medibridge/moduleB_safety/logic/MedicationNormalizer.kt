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
        return normalized
            .replace("\\b\\d+\\s*(mg|mcg|g|ml|iu|tablets?|capsules?)\\b".toRegex(), "")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    val INDIAN_BRAND_MAP: Map<String, String> = mapOf(
        "calpol" to "paracetamol",
        "dolo" to "paracetamol",
        "crocin" to "paracetamol",
        "pacimol" to "paracetamol",
        "pyrigesic" to "paracetamol",
        "glycomet" to "metformin",
        "gluconorm" to "metformin",
        "obimet" to "metformin",
        "cetapin" to "metformin",
        "telma" to "telmisartan",
        "telmikind" to "telmisartan",
        "tazloc" to "telmisartan",
        "telpres" to "telmisartan",
        "telvas" to "telmisartan",
        "pan" to "pantoprazole",
        "pantocid" to "pantoprazole",
        "pantosec" to "pantoprazole",
        "pantodac" to "pantoprazole",
        "omez" to "omeprazole",
        "omecip" to "omeprazole",
        "ecosprin" to "aspirin",
        "disprin" to "aspirin",
        "delisprin" to "aspirin",
        "atorva" to "atorvastatin",
        "storvas" to "atorvastatin",
        "tonact" to "atorvastatin",
        "lipitor" to "atorvastatin",
        "rosuvas" to "rosuvastatin",
        "rosave" to "rosuvastatin",
        "razel" to "rosuvastatin",
        "amlong" to "amlodipine",
        "stamlo" to "amlodipine",
        "amlovas" to "amlodipine",
        "amtas" to "amlodipine",
        "azithral" to "azithromycin",
        "azee" to "azithromycin",
        "zithrox" to "azithromycin",
        "augmentin" to "amoxicillin",
        "clavam" to "amoxicillin",
        "moxikind" to "amoxicillin",
        "novamox" to "amoxicillin",
        "montair" to "montelukast",
        "telekast" to "montelukast",
        "montek" to "montelukast",
        "cetzine" to "cetirizine",
        "alerid" to "cetirizine",
        "okacet" to "cetirizine"
    )

    fun canonicalGeneric(name: String): String {
        val base = extractBaseName(name)
        val cleanToken = base.replace(Regex("""[-_/]"""), " ")
            .split(" ")
            .firstOrNull { it.isNotBlank() } ?: base
        return INDIAN_BRAND_MAP[cleanToken] ?: INDIAN_BRAND_MAP[base] ?: base
    }
}
