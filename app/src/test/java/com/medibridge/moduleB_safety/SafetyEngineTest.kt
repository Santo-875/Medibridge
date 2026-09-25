package com.medibridge.moduleB_safety

import com.medibridge.core.model.MedicationConflict
import com.medibridge.core.model.MedicationObject
import com.medibridge.core.model.ScheduleSlot
import com.medibridge.moduleB_safety.data.SafetyCheckDao
import com.medibridge.moduleB_safety.data.SafetyCheckEntity
import com.medibridge.moduleB_safety.data.SafetyRepository
import com.medibridge.moduleB_safety.logic.*
import com.medibridge.moduleB_safety.logic.fallback.FallbackSafetyAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafetyEngineTest {

    private val baselineHistory = MockSafetyRepository.baselineHistory

    // ── 1. Safe Medication Test ──────────────────────────────────────────────
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

    // ── 2. Duplicate Detection Test ──────────────────────────────────────────
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

    // ── 3. Overlap Detection Test ────────────────────────────────────────────
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

    // ── 4. Interaction Detection Test ────────────────────────────────────────
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

    // ── 5. Unknown Medicine Test ─────────────────────────────────────────────
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

    // ── 6. Normalization Tests ───────────────────────────────────────────────
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

    // ── 7. Order-Independent Interaction Test ────────────────────────────────
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

    // ── 8. AI Input Construction Test ────────────────────────────────────────
    @Test
    fun testAiInputConstruction_structuredCorrectly() {
        val scenario = MockSafetyRepository.demoScenarios.first { it.id == "case-4" } // Aspirin vs Warfarin
        val evaluation = SafetyEngine.evaluateSafety(scenario.candidateMedication, baselineHistory)
        val aiInput = SafetyEngine.buildAiComparisonInput(scenario.candidateMedication, baselineHistory, evaluation)

        assertEquals("Aspirin", aiInput.candidateMedicine.name)
        assertTrue(aiInput.crossVerified)
        assertTrue(aiInput.reviewRecommended)
        assertTrue(aiInput.findings.isNotEmpty())
        assertEquals(FindingType.INTERACTION, aiInput.findings[0].findingType)
        assertEquals("Warfarin", aiInput.findings[0].medicineB?.name)
        assertEquals("HIGH", aiInput.findings[0].severity)
    }

    // ── 9. AI Fallback Behavior & Polite Language Test ───────────────────────
    @Test
    fun testAiFallbackBehavior_politeLanguageAndFlags() {
        val scenario = MockSafetyRepository.demoScenarios.first { it.id == "case-4" }
        val evaluation = SafetyEngine.evaluateSafety(scenario.candidateMedication, baselineHistory)
        val aiInput = SafetyEngine.buildAiComparisonInput(scenario.candidateMedication, baselineHistory, evaluation)

        val aiResponse = FallbackSafetyAnalyzer.generatePoliteExplanation(aiInput)

        assertTrue(aiResponse.flagged)
        assertTrue(aiResponse.isFallback)
        assertEquals("HIGH", aiResponse.severity)
        assertTrue("Message must be polite and action-oriented", aiResponse.message.contains("confirm this combination with your doctor or pharmacist"))
        assertFalse("Message must avoid aggressive alarmist words", aiResponse.message.contains("DANGEROUS"))
        assertFalse("Message must avoid aggressive alarmist words", aiResponse.message.contains("STOP TAKING"))
    }

    @Test
    fun testGeminiSafetyAnalyzer_usesFallbackWhenKeyIsPlaceholder() = runBlocking {
        val analyzer = GeminiSafetyAnalyzer(apiKey = "YOUR_GEMINI_API_KEY_HERE")
        val scenario = MockSafetyRepository.demoScenarios.first { it.id == "case-1" } // Safe
        val evaluation = SafetyEngine.evaluateSafety(scenario.candidateMedication, baselineHistory)
        val aiInput = SafetyEngine.buildAiComparisonInput(scenario.candidateMedication, baselineHistory, evaluation)

        val response = analyzer.analyzeSafety(aiInput)
        assertFalse(response.flagged)
        assertEquals("LOW", response.severity)
        assertTrue(response.isFallback)
        assertTrue(response.title.contains("No known conflicts"))
    }

    // ── 10. Multiple Findings Handling Test ──────────────────────────────────
    @Test
    fun testMultipleFindings_combinedExplanation() {
        // Candidate medicine with multiple conflicts: Ibuprofen interacts with Warfarin AND Lisinopril
        val multiConflictMed = MedicationObject(
            id = "test-multi",
            name = "Ibuprofen",
            strength = "400mg",
            dose = "1 tablet",
            frequency = "As needed",
            timing = "With food",
            duration = "5 days",
            confidence = 0.95f,
            needsVerification = false,
            verifiedByUser = true,
            crossVerified = false,
            conflicts = emptyList(),
            reviewRecommended = false,
            schedule = emptyList(),
            adherence = emptyList(),
            summary = "NSAID pain reliever",
            sideEffects = emptyList(),
            visibleTo = listOf("patient")
        )

        val evaluation = SafetyEngine.evaluateSafety(multiConflictMed, baselineHistory)
        assertTrue("Should detect at least 2 conflicts for Ibuprofen with Lisinopril & Warfarin", evaluation.conflicts.size >= 2)

        val aiInput = SafetyEngine.buildAiComparisonInput(multiConflictMed, baselineHistory, evaluation)
        assertTrue(aiInput.hasMultipleFindings)

        val aiResponse = FallbackSafetyAnalyzer.generatePoliteExplanation(aiInput)
        assertTrue(aiResponse.flagged)
        assertTrue("Should produce combined explanation for multiple findings", aiResponse.title.contains("Multiple"))
        assertTrue(aiResponse.message.contains("Lisinopril") || aiResponse.message.contains("Warfarin"))
    }

    // ── 11. Database Insert & Retrieval via Repository Test ──────────────────
    @Test
    fun testDatabasePersistenceAndRetrieval() = runBlocking {
        val fakeDao = FakeSafetyCheckDao()
        val repository = SafetyRepository(fakeDao)

        val scenario = MockSafetyRepository.demoScenarios.first { it.id == "case-4" }
        val evaluation = SafetyEngine.evaluateSafety(scenario.candidateMedication, baselineHistory)
        val aiResponse = FallbackSafetyAnalyzer.generatePoliteExplanation(
            SafetyEngine.buildAiComparisonInput(scenario.candidateMedication, baselineHistory, evaluation)
        )

        // Persist evaluation
        val saved = repository.persistSafetyEvaluation(
            evaluation = evaluation,
            aiResponse = aiResponse,
            candidateMed = scenario.candidateMedication,
            history = baselineHistory
        )

        assertEquals(1, saved.size)
        assertEquals("Aspirin", saved[0].medicineA)
        assertEquals("Warfarin", saved[0].medicineB)
        assertEquals("INTERACTION", saved[0].findingType)

        // Retrieve from repository
        val directHistory = repository.getSafetyHistoryDirect()
        assertEquals(1, directHistory.size)
        assertEquals("Aspirin", directHistory[0].medicineA)
        assertEquals(aiResponse.title, directHistory[0].aiTitle)

        // Test Flow retrieval
        val flowHistory = repository.getSafetyHistory().first()
        assertEquals(1, flowHistory.size)

        // Test Clear history
        repository.clearHistory()
        val clearedHistory = repository.getSafetyHistoryDirect()
        assertTrue(clearedHistory.isEmpty())
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

    // In-memory fake DAO implementation for isolated repository unit testing
    private class FakeSafetyCheckDao : SafetyCheckDao {
        private val records = mutableListOf<SafetyCheckEntity>()

        override suspend fun insertSafetyCheck(safetyCheck: SafetyCheckEntity) {
            records.add(0, safetyCheck)
        }

        override suspend fun insertAll(safetyChecks: List<SafetyCheckEntity>) {
            records.addAll(0, safetyChecks)
        }

        override fun getAllSafetyChecks(): Flow<List<SafetyCheckEntity>> = flowOf(records.toList())

        override fun getRecentSafetyChecks(limit: Int): Flow<List<SafetyCheckEntity>> =
            flowOf(records.take(limit))

        override suspend fun getAllSafetyChecksDirect(): List<SafetyCheckEntity> = records.toList()

        override suspend fun getSafetyCheckById(id: String): SafetyCheckEntity? =
            records.find { it.id == id }

        override suspend fun deleteById(id: String) {
            records.removeAll { it.id == id }
        }

        override suspend fun deleteAll() {
            records.clear()
        }
    }
}
