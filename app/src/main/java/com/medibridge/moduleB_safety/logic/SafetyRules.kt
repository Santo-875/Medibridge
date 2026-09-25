package com.medibridge.moduleB_safety.logic

/**
 * Local deterministic knowledge base and safety rules for prototype cross-verification.
 *
 * NOTE: This is explicitly a prototype/demonstration safety rules engine,
 * not a comprehensive clinical decision-support system.
 */
object SafetyRules {

    /**
     * Set of medications recognized by the local verification knowledge base.
     */
    val RECOGNIZED_DRUGS: Set<String> = setOf(
        "metformin",
        "amlodipine",
        "lisinopril",
        "vitamin d3",
        "atorvastatin",
        "aspirin",
        "ibuprofen",
        "paracetamol",
        "acetaminophen",
        "omeprazole",
        "pantoprazole",
        "warfarin",
        "enalapril",
        "naproxen",
        "amoxicillin",
        "metoprolol"
    )

    /**
     * Therapeutic class / active ingredient family mapping used for overlap detection.
     */
    val THERAPEUTIC_CLASSES: Map<String, String> = mapOf(
        "paracetamol" to "Analgesic / Antipyretic",
        "acetaminophen" to "Analgesic / Antipyretic",
        "ibuprofen" to "NSAID (Non-Steroidal Anti-Inflammatory)",
        "naproxen" to "NSAID (Non-Steroidal Anti-Inflammatory)",
        "aspirin" to "NSAID / Antiplatelet",
        "lisinopril" to "ACE Inhibitor (Antihypertensive)",
        "enalapril" to "ACE Inhibitor (Antihypertensive)",
        "ramipril" to "ACE Inhibitor (Antihypertensive)",
        "amlodipine" to "Calcium Channel Blocker (Antihypertensive)",
        "nifedipine" to "Calcium Channel Blocker (Antihypertensive)",
        "metformin" to "Biguanide (Oral Antidiabetic)",
        "atorvastatin" to "Statin (HMG-CoA Reductase Inhibitor)",
        "rosuvastatin" to "Statin (HMG-CoA Reductase Inhibitor)",
        "omeprazole" to "Proton Pump Inhibitor (Acid Reducer)",
        "pantoprazole" to "Proton Pump Inhibitor (Acid Reducer)",
        "warfarin" to "Anticoagulant (Blood Thinner)",
        "amoxicillin" to "Penicillin Antibiotic",
        "metoprolol" to "Beta-Blocker (Cardiovascular)",
        "vitamin d3" to "Vitamin Supplement"
    )

    /**
     * Interaction rule data class.
     */
    data class InteractionRule(
        val drugA: String,
        val drugB: String,
        val severity: String,
        val detail: String
    )

    /**
     * Canonical order-independent pair key for two drugs.
     */
    fun canonicalPairKey(drug1: String, drug2: String): String {
        val norm1 = MedicationNormalizer.normalize(drug1)
        val norm2 = MedicationNormalizer.normalize(drug2)
        return if (norm1 < norm2) "$norm1+$norm2" else "$norm2+$norm1"
    }

    /**
     * Configured deterministic drug-drug interactions.
     * Evaluated in an order-independent manner.
     */
    val INTERACTIONS: Map<String, InteractionRule> = listOf(
        InteractionRule(
            drugA = "amlodipine",
            drugB = "lisinopril",
            severity = "MODERATE",
            detail = "May enhance hypotensive effect when co-administered. Monitor blood pressure closely."
        ),
        InteractionRule(
            drugA = "aspirin",
            drugB = "warfarin",
            severity = "HIGH",
            detail = "Increased risk of bleeding due to combined anticoagulant and antiplatelet activity."
        ),
        InteractionRule(
            drugA = "ibuprofen",
            drugB = "lisinopril",
            severity = "HIGH",
            detail = "NSAID may attenuate antihypertensive effect of Lisinopril and increase risk of renal impairment."
        ),
        InteractionRule(
            drugA = "ibuprofen",
            drugB = "warfarin",
            severity = "HIGH",
            detail = "Concurrent use increases risk of gastrointestinal bleeding."
        ),
        InteractionRule(
            drugA = "atorvastatin",
            drugB = "clarithromycin",
            severity = "HIGH",
            detail = "Clarithromycin inhibits CYP3A4, increasing Atorvastatin exposure and risk of myopathy."
        )
    ).associateBy { canonicalPairKey(it.drugA, it.drugB) }

    /**
     * Checks if a medication is recognized in the knowledge base.
     */
    fun isRecognized(drugName: String): Boolean {
        val norm = MedicationNormalizer.normalize(drugName)
        val base = MedicationNormalizer.extractBaseName(drugName)
        return norm in RECOGNIZED_DRUGS || base in RECOGNIZED_DRUGS
    }

    /**
     * Finds the therapeutic class for a medication if known.
     */
    fun getTherapeuticClass(drugName: String): String? {
        val norm = MedicationNormalizer.normalize(drugName)
        val base = MedicationNormalizer.extractBaseName(drugName)
        return THERAPEUTIC_CLASSES[norm] ?: THERAPEUTIC_CLASSES[base]
    }

    /**
     * Checks if an order-independent interaction rule exists between two medications.
     */
    fun findInteraction(drug1: String, drug2: String): InteractionRule? {
        val key = canonicalPairKey(drug1, drug2)
        return INTERACTIONS[key]
    }
}
