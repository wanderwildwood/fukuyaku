package com.wanderwildwood.fukuyaku.data

/**
 * A medicine as it was set up. [caughtUp] is the moment up to which its due doses have been
 * written into the log; nothing before it is ever added again, so a schedule changed today
 * never rewrites what yesterday was.
 */
data class Medicine(
    val id: Long = 0,
    val name: String,
    val amount: String = "",
    val schedule: Schedule,
    /** What is left, when it is being counted; null when it is not. */
    val left: Int? = null,
    /** How many of what is counted one dose uses. */
    val perDose: Int = 1,
    /** Say so once when [left] falls to this many. */
    val warnAt: Int? = null,
    val pharmacyName: String = "",
    val pharmacyNumber: String = "",
    /** The pharmacy's entry in Contacts, when it was chosen from there. */
    val pharmacyContact: String = "",
    val notes: String = "",
    val paused: Boolean = false,
    val caughtUp: Long = 0,
    val refillWarned: Boolean = false,
) {
    val reminds: Boolean get() = !paused && schedule !is Schedule.AsNeeded

    /** The name with its strength or amount after it, the way it is read out on a reminder. */
    val label: String get() = if (amount.isBlank()) name else "$name, $amount"

    val low: Boolean get() = left != null && warnAt != null && left <= warnAt
}

enum class Status(val code: Int) {
    /** Due and not yet marked: a dose nobody has said was taken or skipped. */
    OPEN(0),
    TAKEN(1),
    SKIPPED(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: OPEN
    }
}

/**
 * One dose in the log. It carries the medicine's name and amount as they were, so a medicine
 * renamed or deleted later leaves its history readable.
 */
data class Dose(
    val id: Long = 0,
    val medicineId: Long,
    val name: String,
    val amount: String,
    /** When it was due; for a dose taken as needed, when it was taken. */
    val due: Long,
    val status: Status = Status.OPEN,
    /** When it was marked taken or skipped. */
    val marked: Long? = null,
    /** When its reminder first rang; null when no reminder rang for it. */
    val rang: Long? = null,
    /** When it is to ring again: a snooze, or a reminder repeated because it was not marked. */
    val remindAt: Long? = null,
    /** How many times it has rung again since the first. */
    val again: Int = 0,
    val asNeeded: Boolean = false,
) {
    val label: String get() = if (amount.isBlank()) name else "$name, $amount"
}
