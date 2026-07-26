package com.chronocard.app

/**
 * Central place for the rank/suit <-> code mapping used by both input modes.
 * Rank:  01 Ace ... 10 Ten, 11 Jack, 12 Queen, 13 King
 * Suit:  1 Spade, 2 Hearts, 3 Clubs, 4 Diamond
 */
object CardUtils {

    val RANK_NAMES = mapOf(
        1 to "Ace", 2 to "2", 3 to "3", 4 to "4", 5 to "5", 6 to "6", 7 to "7",
        8 to "8", 9 to "9", 10 to "10", 11 to "Jack", 12 to "Queen", 13 to "King"
    )

    val SUIT_NAMES = mapOf(
        1 to "Spades", 2 to "Hearts", 3 to "Clubs", 4 to "Diamonds"
    )

    data class Card(val rank: Int, val suit: Int) {
        /** Stable key used to look up the uploaded photo, e.g. "AS", "10H", "KD" */
        fun key(): String {
            val r = when (rank) {
                1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"
                else -> rank.toString()
            }
            val s = when (suit) {
                1 -> "S"; 2 -> "H"; 3 -> "C"; 4 -> "D"
                else -> "S"
            }
            return "$r$s"
        }

        fun displayName(): String = "${RANK_NAMES[rank]} of ${SUIT_NAMES[suit]}"

        fun isValid(): Boolean = rank in 1..13 && suit in 1..4
    }

    /** All 52 keys in a stable order, used when building the upload grid. */
    fun allKeys(): List<String> {
        val ranks = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
        val suits = listOf("S", "H", "C", "D")
        return suits.flatMap { s -> ranks.map { r -> "$r$s" } }
    }

    /**
     * Passcode decode: 4 digits.
     * digits[0..1] -> rank (01-13), digits[2] -> suit (1-4), digits[3] -> ignored (decoy).
     * Returns null if the code doesn't map to a valid card (treated as a "wrong passcode").
     */
    fun decodePasscode(code: String): Card? {
        if (code.length != 4 || code.any { !it.isDigit() }) return null
        val rank = code.substring(0, 2).toIntOrNull() ?: return null
        val suit = code.substring(2, 3).toIntOrNull() ?: return null
        val card = Card(rank, suit)
        return if (card.isValid()) card else null
    }

    /**
     * AOD decode: timer minute:second string like "3:07" -> rank 7.
     * Loop is 3:01 through 3:13 (Ace..King). Suit comes from which quadrant was tapped.
     */
    fun rankFromTimerLabel(label: String): Int? {
        val parts = label.split(":")
        if (parts.size != 2) return null
        val min = parts[0].toIntOrNull() ?: return null
        val sec = parts[1].toIntOrNull() ?: return null
        if (min != 3) return null
        return if (sec in 1..13) sec else null
    }

    enum class Quadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    fun suitFromQuadrant(q: Quadrant): Int = when (q) {
        Quadrant.TOP_LEFT -> 1     // Spade
        Quadrant.TOP_RIGHT -> 2    // Hearts
        Quadrant.BOTTOM_LEFT -> 3  // Clubs
        Quadrant.BOTTOM_RIGHT -> 4 // Diamond
    }

    const val FALLBACK_KEY = "AS" // Ace of Spades, used when a photo is missing

    /**
     * Encodes a card as a valid, natural-looking M:SS music-player timestamp so the
     * performer can visually confirm the captured card on the lockscreen before
     * committing via the fingerprint hold.
     *
     * Ranks 1-9:  minute = rank, second = suit * 10        (e.g. 7 of Diamonds -> 7:40)
     * Ranks 10-13: minute = 1,   second = (rank-10)*10+suit (e.g. King of Hearts -> 1:32)
     * Both branches always produce seconds in 0-59, so the display is always a real time.
     */
    fun confirmationTimerLabel(card: Card): String {
        val (minute, second) = if (card.rank < 10) {
            card.rank to card.suit * 10
        } else {
            1 to ((card.rank - 10) * 10 + card.suit)
        }
        return String.format("%d:%02d", minute, second)
    }
}
