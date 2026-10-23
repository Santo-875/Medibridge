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
        "metoprolol",
        "telmisartan",
        "pantoprazole",
        "rosuvastatin",
        "azithromycin",
        "montelukast",
        "cetirizine",
        "rabeprazole",
        "domperidone"
    )

    val THERAPEUTIC_CLASSES: Map<String, String> = mapOf(
        "paracetamol" to "Analgesic / Antipyretic",
        "acetaminophen" to "Analgesic / Antipyretic",
        "ibuprofen" to "NSAID (Non-Steroidal Anti-Inflammatory)",
        "naproxen" to "NSAID (Non-Steroidal Anti-Inflammatory)",
        "aspirin" to "NSAID / Antiplatelet",
        "lisinopril" to "ACE Inhibitor (Antihypertensive)",
        "enalapril" to "ACE Inhibitor (Antihypertensive)",
        "ramipril" to "ACE Inhibitor (Antihypertensive)",
        "telmisartan" to "ARB (Angiotensin II Receptor Blocker)",
        "amlodipine" to "Calcium Channel Blocker (Antihypertensive)",
        "nifedipine" to "Calcium Channel Blocker (Antihypertensive)",
        "metformin" to "Biguanide (Oral Antidiabetic)",
        "atorvastatin" to "Statin (HMG-CoA Reductase Inhibitor)",
        "rosuvastatin" to "Statin (HMG-CoA Reductase Inhibitor)",
        "omeprazole" to "Proton Pump Inhibitor (Acid Reducer)",
        "pantoprazole" to "Proton Pump Inhibitor (Acid Reducer)",
        "rabeprazole" to "Proton Pump Inhibitor (Acid Reducer)",
        "domperidone" to "Prokinetic / Antiemetic",
        "warfarin" to "Anticoagulant (Blood Thinner)",
        "amoxicillin" to "Penicillin Antibiotic",
        "azithromycin" to "Macrolide Antibiotic",
        "montelukast" to "Leukotriene Receptor Antagonist",
        "cetirizine" to "Antihistamine",
        "metoprolol" to "Beta-Blocker (Cardiovascular)",
        "vitamin d3" to "Vitamin Supplement"
    )

    data class InteractionRule(
        val drugA: String,
        val drugB: String,
        val severity: String,
        val detail: String,
        val explanation: String = detail,
        val source: String = "MediBridge Clinical Rules v2",
        val nextStep: String = "Consult your prescribing doctor or clinical pharmacist before taking together."
    )

    fun canonicalPairKey(drug1: String, drug2: String): String {
        val norm1 = MedicationNormalizer.canonicalGeneric(drug1)
        val norm2 = MedicationNormalizer.canonicalGeneric(drug2)
        return if (norm1 < norm2) "$norm1+$norm2" else "$norm2+$norm1"
    }

    val INTERACTIONS: Map<String, InteractionRule> = listOf(
        InteractionRule(
            drugA = "amlodipine",
            drugB = "lisinopril",
            severity = "MODERATE",
            detail = "May enhance hypotensive effect when co-administered. Monitor blood pressure closely.",
            explanation = "Both lower blood pressure through complementary pathways. Concurrent use can cause sudden lightheadedness or postural hypotension.",
            source = "AHA/ACC Hypertension Guidelines",
            nextStep = "Monitor sitting and standing blood pressure during initial titration."
        ),
        InteractionRule(
            drugA = "aspirin",
            drugB = "warfarin",
            severity = "HIGH",
            detail = "Increased risk of bleeding due to combined anticoagulant and antiplatelet activity.",
            explanation = "Aspirin impairs platelet aggregation while Warfarin blocks clotting factors. Combining them markedly elevates gastrointestinal and internal hemorrhage risks.",
            source = "CHEST Antithrombotic Guidelines",
            nextStep = "Confirm with cardiologist/physician if dual antiplatelet/anticoagulant therapy is intentionally indicated."
        ),
        InteractionRule(
            drugA = "aspirin",
            drugB = "ibuprofen",
            severity = "HIGH",
            detail = "Ibuprofen attenuates cardioprotective effect of low-dose aspirin and increases GI toxicity.",
            explanation = "Ibuprofen binds competitively to COX-1 enzyme, preventing low-dose aspirin from irreversibly inhibiting platelets, increasing cardiac event risk and stomach ulcers.",
            source = "FDA MedWatch Safety Alert",
            nextStep = "Take immediate-release aspirin at least 30 minutes before or 8 hours after ibuprofen, or switch to paracetamol for pain."
        ),
        InteractionRule(
            drugA = "ibuprofen",
            drugB = "lisinopril",
            severity = "HIGH",
            detail = "NSAID may attenuate antihypertensive effect of Lisinopril and increase risk of renal impairment.",
            explanation = "NSAIDs reduce renal prostaglandin synthesis, which can trigger acute kidney injury and blunt ACE-inhibitor blood pressure control.",
            source = "KDIGO Clinical Practice Guidelines for Kidney Health",
            nextStep = "Avoid chronic NSAIDs; consult doctor for safe analgesic alternatives like paracetamol."
        ),
        InteractionRule(
            drugA = "ibuprofen",
            drugB = "warfarin",
            severity = "HIGH",
            detail = "Concurrent use increases risk of gastrointestinal bleeding.",
            explanation = "NSAID mucosal injury coupled with systemic anticoagulation creates high danger of severe GI bleeding.",
            source = "British National Formulary (BNF)",
            nextStep = "Do not take ibuprofen while on blood thinners unless specifically monitored by your specialist."
        ),
        InteractionRule(
            drugA = "atorvastatin",
            drugB = "clarithromycin",
            severity = "HIGH",
            detail = "Clarithromycin inhibits CYP3A4, increasing Atorvastatin exposure and risk of myopathy.",
            explanation = "Strong CYP3A4 inhibition causes high statin plasma accumulation, substantially increasing rhabdomyolysis and muscle toxicity risk.",
            source = "FDA Drug Safety Communication",
            nextStep = "Temporarily suspend atorvastatin during the macrolide antibiotic course as advised by your doctor."
        ),
        InteractionRule(
            drugA = "telmisartan",
            drugB = "lisinopril",
            severity = "HIGH",
            detail = "Dual renin-angiotensin-aldosterone system (RAAS) blockade increases risk of severe hyperkalemia and renal failure.",
            explanation = "Combining an ARB with an ACE inhibitor does not confer additional cardiovascular benefit and significantly increases acute renal damage risk.",
            source = "ONTARGET Clinical Study Guidelines",
            nextStep = "Ask your doctor if both medicines were meant to be active concurrently."
        ),
        InteractionRule(
            drugA = "azithromycin",
            drugB = "warfarin",
            severity = "MODERATE",
            detail = "Macrolide antibiotics may enhance anticoagulant effect of warfarin, increasing bleeding risk.",
            explanation = "Azithromycin can alter gut bacterial synthesis of Vitamin K, leading to heightened INR levels and bruising.",
            source = "Clinical Pharmacokinetics Formulary",
            nextStep = "Check INR / prothrombin time within 3 to 5 days of starting the antibiotic."
        )
    ).associateBy { canonicalPairKey(it.drugA, it.drugB) }

    fun isRecognized(drugName: String): Boolean {
        val norm = MedicationNormalizer.normalize(drugName)
        val base = MedicationNormalizer.extractBaseName(drugName)
        val canonical = MedicationNormalizer.canonicalGeneric(drugName)
        return norm in RECOGNIZED_DRUGS || base in RECOGNIZED_DRUGS || canonical in RECOGNIZED_DRUGS
    }

    fun getTherapeuticClass(drugName: String): String? {
        val norm = MedicationNormalizer.normalize(drugName)
        val base = MedicationNormalizer.extractBaseName(drugName)
        val canonical = MedicationNormalizer.canonicalGeneric(drugName)
        return THERAPEUTIC_CLASSES[norm] ?: THERAPEUTIC_CLASSES[base] ?: THERAPEUTIC_CLASSES[canonical]
    }

    fun findInteraction(drug1: String, drug2: String): InteractionRule? {
        val key = canonicalPairKey(drug1, drug2)
        return INTERACTIONS[key]
    }
}
