package com.medibridge.moduleC_schedule.engine

import com.medibridge.core.model.ScheduleSlot
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Result of schedule generation containing slots and whether medical review is advised.
 */
data class ScheduleGenerationResult(
    val slots: List<ScheduleSlot>,
    val reviewRecommended: Boolean,
    val note: String? = null
)

/**
 * ScheduleEngine — Module C dynamic medication schedule generator.
 *
 * Responsibilities:
 *  1. Parses frequency & timing strings dynamically into [ScheduleSlot] objects.
 *  2. NEVER hardcodes medicine names or schedules (no "Paracetamol = 10 AM").
 *  3. Handles common clinical directives:
 *     - once / twice / thrice / four times daily
 *     - every N hours
 *     - morning / afternoon / evening / night / bedtime
 *     - before/after breakfast, lunch, dinner
 *     - with food vs. empty stomach
 *  4. Evaluates finite duration vs ongoing medication.
 *  5. Recommends review if directives are ambiguous or unparseable.
 */
object ScheduleEngine {

    // Default slot times
    const val TIME_BEFORE_BREAKFAST = "07:30"
    const val TIME_MORNING = "08:00"
    const val TIME_AFTER_BREAKFAST = "08:30"
    const val TIME_BEFORE_LUNCH = "12:30"
    const val TIME_AFTERNOON = "13:00"
    const val TIME_AFTER_LUNCH = "13:30"
    const val TIME_EVENING = "18:00"
    const val TIME_BEFORE_DINNER = "19:30"
    const val TIME_NIGHT = "20:00"
    const val TIME_AFTER_DINNER = "20:30"
    const val TIME_BEDTIME = "22:00"

    /**
     * Dynamically generates schedule slots from prescription frequency and timing instructions.
     */
    fun generateSchedule(frequency: String, timing: String): ScheduleGenerationResult {
        val freqClean = frequency.trim().lowercase()
        val timingClean = timing.trim().lowercase()

        val withFood = determineWithFood(timingClean)

        // 1. Direct timing specific combinations (e.g. "morning and night", "after breakfast and dinner")
        val combinedSlots = parseExplicitTimings(freqClean, timingClean, withFood)
        if (combinedSlots.isNotEmpty()) {
            return ScheduleGenerationResult(combinedSlots.distinctBy { it.time }.sortedBy { it.time }, false)
        }

        // 2. "Every N hours" parsing
        val everyHoursRegex = Regex("""every\s*(\d+)\s*(?:hours|hrs|hr|h)""")
        val everyHoursMatch = everyHoursRegex.find(freqClean)
        if (everyHoursMatch != null) {
            val interval = everyHoursMatch.groupValues[1].toIntOrNull()
            if (interval != null && interval in 1..24) {
                val slots = generateIntervalSlots(interval, withFood)
                return ScheduleGenerationResult(slots, false)
            }
        }

        // 3. Frequency patterns
        val isOnceDaily = freqClean.contains("once") || freqClean.contains("1 time") ||
                freqClean.contains("1x") || freqClean == "daily" || freqClean == "od" || freqClean == "q.d." || freqClean == "qd"
        val isTwiceDaily = freqClean.contains("twice") || freqClean.contains("2 times") ||
                freqClean.contains("2x") || freqClean.contains("bid") || freqClean.contains("b.i.d.")
        val isThreeTimesDaily = freqClean.contains("three times") || freqClean.contains("3 times") ||
                freqClean.contains("3x") || freqClean.contains("thrice") || freqClean.contains("tid") || freqClean.contains("t.i.d.")
        val isFourTimesDaily = freqClean.contains("four times") || freqClean.contains("4 times") ||
                freqClean.contains("4x") || freqClean.contains("qid") || freqClean.contains("q.i.d.")

        val slots = when {
            isFourTimesDaily -> listOf(
                ScheduleSlot(TIME_MORNING, "Morning", withFood),
                ScheduleSlot(TIME_AFTERNOON, "Afternoon", withFood),
                ScheduleSlot(TIME_EVENING, "Evening", withFood),
                ScheduleSlot(TIME_NIGHT, "Night", withFood)
            )
            isThreeTimesDaily -> listOf(
                ScheduleSlot(
                    time = if (timingClean.contains("after breakfast")) TIME_AFTER_BREAKFAST else TIME_MORNING,
                    slot = "Morning",
                    withFood = withFood
                ),
                ScheduleSlot(
                    time = if (timingClean.contains("after lunch")) TIME_AFTER_LUNCH else TIME_AFTERNOON,
                    slot = "Afternoon",
                    withFood = withFood
                ),
                ScheduleSlot(
                    time = if (timingClean.contains("after dinner")) TIME_AFTER_DINNER else TIME_NIGHT,
                    slot = "Night",
                    withFood = withFood
                )
            )
            isTwiceDaily -> listOf(
                ScheduleSlot(
                    time = if (timingClean.contains("after breakfast")) TIME_AFTER_BREAKFAST else TIME_MORNING,
                    slot = "Morning",
                    withFood = withFood
                ),
                ScheduleSlot(
                    time = if (timingClean.contains("after dinner")) TIME_AFTER_DINNER else TIME_NIGHT,
                    slot = "Night",
                    withFood = withFood
                )
            )
            isOnceDaily -> {
                val (slotName, time) = when {
                    timingClean.contains("bed") || timingClean.contains("night") -> "Night" to TIME_BEDTIME
                    timingClean.contains("evening") -> "Evening" to TIME_EVENING
                    timingClean.contains("afternoon") || timingClean.contains("lunch") -> "Afternoon" to TIME_AFTERNOON
                    timingClean.contains("after breakfast") -> "Morning" to TIME_AFTER_BREAKFAST
                    timingClean.contains("before breakfast") -> "Morning" to TIME_BEFORE_BREAKFAST
                    else -> "Morning" to TIME_MORNING
                }
                listOf(ScheduleSlot(time, slotName, withFood))
            }
            else -> {
                // Check single time of day mentions
                val singleSlot = parseSingleTimeOfDay(freqClean, timingClean, withFood)
                if (singleSlot != null) {
                    listOf(singleSlot)
                } else {
                    emptyList()
                }
            }
        }

        return if (slots.isNotEmpty()) {
            ScheduleGenerationResult(slots.sortedBy { it.time }, false)
        } else {
            // Unresolved / ambiguous instructions - do not invent dangerous schedules
            ScheduleGenerationResult(
                slots = emptyList(),
                reviewRecommended = true,
                note = "Frequency or timing instructions could not be safely resolved without manual review."
            )
        }
    }

    private fun determineWithFood(timing: String): Boolean {
        return when {
            timing.contains("after") || timing.contains("with food") || timing.contains("with meals") || timing.contains("with milk") -> true
            timing.contains("before") || timing.contains("empty stomach") -> false
            else -> false
        }
    }

    private fun parseExplicitTimings(frequency: String, timing: String, defaultWithFood: Boolean): List<ScheduleSlot> {
        val slots = mutableListOf<ScheduleSlot>()
        val combined = "$frequency $timing".lowercase()

        val hasMorning = combined.contains("morning") || combined.contains("breakfast")
        val hasAfternoon = combined.contains("afternoon") || combined.contains("lunch")
        val hasEvening = combined.contains("evening")
        val hasNight = combined.contains("night") || combined.contains("dinner") || combined.contains("bed")

        val count = listOf(hasMorning, hasAfternoon, hasEvening, hasNight).count { it }
        if (count >= 2) {
            if (hasMorning) {
                val time = when {
                    combined.contains("before breakfast") -> TIME_BEFORE_BREAKFAST
                    combined.contains("after breakfast") -> TIME_AFTER_BREAKFAST
                    else -> TIME_MORNING
                }
                slots.add(ScheduleSlot(time, "Morning", defaultWithFood))
            }
            if (hasAfternoon) {
                val time = when {
                    combined.contains("before lunch") -> TIME_BEFORE_LUNCH
                    combined.contains("after lunch") -> TIME_AFTER_LUNCH
                    else -> TIME_AFTERNOON
                }
                slots.add(ScheduleSlot(time, "Afternoon", defaultWithFood))
            }
            if (hasEvening) {
                slots.add(ScheduleSlot(TIME_EVENING, "Evening", defaultWithFood))
            }
            if (hasNight) {
                val time = when {
                    combined.contains("before dinner") -> TIME_BEFORE_DINNER
                    combined.contains("after dinner") -> TIME_AFTER_DINNER
                    combined.contains("bed") -> TIME_BEDTIME
                    else -> TIME_NIGHT
                }
                slots.add(ScheduleSlot(time, "Night", defaultWithFood))
            }
        }
        return slots
    }

    private fun parseSingleTimeOfDay(frequency: String, timing: String, withFood: Boolean): ScheduleSlot? {
        val combined = "$frequency $timing".lowercase()
        return when {
            combined.contains("before breakfast") -> ScheduleSlot(TIME_BEFORE_BREAKFAST, "Morning", false)
            combined.contains("after breakfast") -> ScheduleSlot(TIME_AFTER_BREAKFAST, "Morning", true)
            combined.contains("morning") -> ScheduleSlot(TIME_MORNING, "Morning", withFood)
            combined.contains("before lunch") -> ScheduleSlot(TIME_BEFORE_LUNCH, "Afternoon", false)
            combined.contains("after lunch") -> ScheduleSlot(TIME_AFTER_LUNCH, "Afternoon", true)
            combined.contains("afternoon") -> ScheduleSlot(TIME_AFTERNOON, "Afternoon", withFood)
            combined.contains("evening") -> ScheduleSlot(TIME_EVENING, "Evening", withFood)
            combined.contains("before dinner") -> ScheduleSlot(TIME_BEFORE_DINNER, "Night", false)
            combined.contains("after dinner") -> ScheduleSlot(TIME_AFTER_DINNER, "Night", true)
            combined.contains("bed") -> ScheduleSlot(TIME_BEDTIME, "Night", false)
            combined.contains("night") -> ScheduleSlot(TIME_NIGHT, "Night", withFood)
            else -> null
        }
    }

    private fun generateIntervalSlots(intervalHours: Int, withFood: Boolean): List<ScheduleSlot> {
        val slots = mutableListOf<ScheduleSlot>()
        var hour = 8 // Start at 08:00 AM
        val count = 24 / intervalHours
        for (i in 0 until count) {
            val h = (hour + (i * intervalHours)) % 24
            val timeString = String.format("%02d:00", h)
            val slotName = when (h) {
                in 5..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
            slots.add(ScheduleSlot(timeString, slotName, withFood))
        }
        return slots.sortedBy { it.time }
    }

    /**
     * Parses duration string into total active days.
     * Returns null if medication is "Ongoing" or indefinite.
     * Returns -1 if duration format is invalid/unknown.
     */
    fun parseDurationDays(duration: String): Int? {
        val clean = duration.trim().lowercase()
        if (clean.isEmpty() || clean.contains("ongoing") || clean.contains("continuous") || clean.contains("chronic") || clean == "indefinite") {
            return null // Ongoing
        }

        // Match "X days"
        val daysRegex = Regex("""(\d+)\s*(?:days|day|d)""")
        daysRegex.find(clean)?.let {
            return it.groupValues[1].toIntOrNull()
        }

        // Match "X weeks"
        val weeksRegex = Regex("""(\d+)\s*(?:weeks|week|wk|wks)""")
        weeksRegex.find(clean)?.let {
            val weeks = it.groupValues[1].toIntOrNull()
            return if (weeks != null) weeks * 7 else null
        }

        // Match "X months"
        val monthsRegex = Regex("""(\d+)\s*(?:months|month|mo|mos)""")
        monthsRegex.find(clean)?.let {
            val months = it.groupValues[1].toIntOrNull()
            return if (months != null) months * 30 else null
        }

        // Plain integer
        clean.toIntOrNull()?.let { return it }

        return -1 // Unrecognized duration
    }

    /**
     * Checks if a medication is currently active on the given date.
     *
     * @param startDate Date the medication was prescribed/started (ISO format yyyy-MM-dd).
     * @param targetDate Date to check (ISO format yyyy-MM-dd).
     * @param duration Duration string e.g. "7 days", "Ongoing".
     */
    fun isMedicationActive(startDate: String, targetDate: String, duration: String): Boolean {
        val durationDays = parseDurationDays(duration) ?: return true // Ongoing is always active
        if (durationDays < 0) return false // Malformed duration requires review

        return try {
            val start = LocalDate.parse(startDate)
            val target = LocalDate.parse(targetDate)
            val daysBetween = ChronoUnit.DAYS.between(start, target)
            daysBetween in 0 until durationDays
        } catch (e: Exception) {
            true // Fallback to safe prototype default
        }
    }
}
