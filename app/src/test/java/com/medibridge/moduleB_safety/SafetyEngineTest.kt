package com.medibridge.moduleB_safety

import com.medibridge.core.model.MedicationObject
import com.medibridge.moduleB_safety.logic.MockSafetyRepository
import com.medibridge.moduleB_safety.logic.SafetyEngine
import com.medibridge.moduleB_safety.logic.SafetyRules
import com.medibridge.moduleB_safety.logic.SafetyVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyEngineTest {

    private val baselineHistory = MockSafetyRepository.baselineHistory

    @Test
    fun testCase1_SafeMedication_passesVerificationWithoutConflicts() {
        val safeScenario = MockSafetyRepository.demoScenarios.first { it.id == "case-1" }
        val result = SafetyEngine.evaluateSafety(safeScenario.candidateMedication, baselineHistory)

        assertEquals(SafetyVerdict.SAFE, result.verdict)
        assertTrue("crossVerified should be true for recognized safe medication", result.crossVerified)
        assertTrue("Conflicts should be empty for safe medication", result.conflicts.isEmpty())
        assertFalse("reviewRecommended should be false for safe medication", result.reviewRecommended)
        assertTrue(result.updatedMedication.crossVerified)
        assertFalse(result.updatedMedication.reviewRecommended)
        assertEquals("No Known Conflict Detected", result.headline)
    }

    @Test
    fun testCase2_DuplicateMedication_detectedSuccessfully() {
        val duplicateScenario = MockSafetyRepository.demoScenarios.first { it.id == "case-2" }
        val result = SafetyEngine.evaluateSafety(duplicateScenario.candidateMedication, baselineHistory)

        assertEquals(SafetyVerdict.DUPLICATE, result.verdict)
        assertTrue(result.crossVerified)
        assertTrue("reviewRecommended must be true on duplicate", result.reviewRecommended)
        assertTrue(result.conflicts.any { it.type == SafetyEngine.CONFLICT_TYPE_DUPLICATE })
        assertTrue(result.conflicts.any { it.withMedId == "hist-001" })
    }

    @Test
    fun testCase3_TherapeuticOverlap_detectedSuccessfully() {
        val overlapScenario = MockSafetyRepository.demoScenarios.first { it.id == "case-3" }
        val result = SafetyEngine.evaluateSafety(overlapScenario.candidateMedication, baselineHistory)

        assertEquals(SafetyVerdict.OVERLAP, result.verdict)
        assertTrue(result.crossVerified)
        assertTrue("reviewRecommended must be true on overlap", result.reviewRecommended)
        val overlapConflict = result.conflicts.find { it.type == SafetyEngine.CONFLICT_TYPE_OVERLAP }
        assertNotNull("Overlap conflict should be present", overlapConflict)
        assertEquals("hist-003", overlapConflict?.withMedId) // Lisinopril
        assertTrue(overlapConflict?.detail?.contains("ACE Inhibitor") == true)
    }

    @Test
    fun testCase4_DrugInteraction_detectedSuccessfully() {
        val interactionScenario = MockSafetyRepository.demoScenarios.first { it.id == "case-4" }
        val result = SafetyEngine.evaluateSafety(interactionScenario.candidateMedication, baselineHistory)

        assertEquals(SafetyVerdict.INTERACTION, result.verdict)
        assertTrue(result.crossVerified)
        assertTrue("reviewRecommended must be true on interaction", result.reviewRecommended)
        val interactionConflict = result.conflicts.find { it.type == SafetyEngine.CONFLICT_TYPE_INTERACTION }
        assertNotNull("Interaction conflict should be present", interactionConflict)
        assertEquals("hist-005", interactionConflict?.withMedId) // Warfarin
        assertTrue(interactionConflict?.detail?.contains("bleeding") == true)
    }

    @Test
    fun testCase5_UnknownMedication_flagsUnverifiedAndRequiresReview() {
        val unknownScenario = MockSafetyRepository.demoScenarios.first { it.id == "case-5" }
        val result = SafetyEngine.evaluateSafety(unknownScenario.candidateMedication, baselineHistory)

        assertEquals(SafetyVerdict.UNVERIFIED, result.verdict)
        assertFalse("crossVerified should be false for unrecognized drug", result.crossVerified)
        assertTrue("reviewRecommended must be true for unverified drug", result.reviewRecommended)
        assertTrue(result.conflicts.any { it.type == SafetyEngine.CONFLICT_TYPE_VERIFICATION })
        assertFalse(result.updatedMedication.crossVerified)
        assertTrue(result.updatedMedication.reviewRecommended)
    }

    @Test
    fun testNormalization_caseInsensitiveMatching() {
        val candidate = baselineHistory[0].copy(
            id = "test-dup-case",
            name = "mEtFoRmIn"
        )
        val result = SafetyEngine.evaluateSafety(candidate, baselineHistory)

        assertTrue("Should detect duplicate despite mixed case",
            result.conflicts.any { it.type == SafetyEngine.CONFLICT_TYPE_DUPLICATE })
    }

    @Test
    fun testNormalization_whitespaceHandling() {
        val candidate = baselineHistory[0].copy(
            id = "test-dup-spaces",
            name = "   Metformin     "
        )
        val result = SafetyEngine.evaluateSafety(candidate, baselineHistory)

        assertTrue("Should detect duplicate despite leading/trailing whitespace",
            result.conflicts.any { it.type == SafetyEngine.CONFLICT_TYPE_DUPLICATE })
    }

    @Test
    fun testInteraction_orderIndependentMatching() {
        val rule1 = SafetyRules.findInteraction("Amlodipine", "Lisinopril")
        val rule2 = SafetyRules.findInteraction("Lisinopril", "Amlodipine")

        assertNotNull(rule1)
        assertNotNull(rule2)
        assertEquals(rule1, rule2)

        val aspirnWrf1 = SafetyRules.findInteraction("Aspirin", "Warfarin")
        val aspirnWrf2 = SafetyRules.findInteraction("Warfarin", "Aspirin")
        assertNotNull(aspirnWrf1)
        assertNotNull(aspirnWrf2)
        assertEquals(aspirnWrf1, aspirnWrf2)
    }

    @Test
    fun testReviewRecommended_isDeterministic() {
        val safeScenario = MockSafetyRepository.demoScenarios[0]
        val dupScenario = MockSafetyRepository.demoScenarios[1]

        val safeResult1 = SafetyEngine.evaluateSafety(safeScenario.candidateMedication, baselineHistory)
        val safeResult2 = SafetyEngine.evaluateSafety(safeScenario.candidateMedication, baselineHistory)
        assertEquals(safeResult1.reviewRecommended, safeResult2.reviewRecommended)
        assertFalse(safeResult1.reviewRecommended)

        val dupResult1 = SafetyEngine.evaluateSafety(dupScenario.candidateMedication, baselineHistory)
        val dupResult2 = SafetyEngine.evaluateSafety(dupScenario.candidateMedication, baselineHistory)
        assertEquals(dupResult1.reviewRecommended, dupResult2.reviewRecommended)
        assertTrue(dupResult1.reviewRecommended)
    }
}
