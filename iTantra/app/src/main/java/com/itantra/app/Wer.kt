package com.itantra.app

/**
 * Wer — word error rate for the benchmark screen.
 *
 * WER = (word substitutions + insertions + deletions) / (number of reference words),
 * computed as the word-level Levenshtein edit distance between the reference sentence and
 * what the STT produced. Both are normalised first (lower-cased, punctuation removed,
 * whitespace collapsed) so trivial differences don't count as errors.
 */
object Wer {

    private val punctuation = Regex("[।॥.,!?;:\"'()\\-–—]")

    /** Split a sentence into comparable words. */
    fun words(s: String): List<String> =
        s.lowercase()
            .replace(punctuation, " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    /** Result of one comparison: edit distance, reference word count, and the WER fraction. */
    data class Result(val edits: Int, val refWords: Int, val wer: Double)

    fun compare(reference: String, hypothesis: String): Result {
        val r = words(reference)
        val h = words(hypothesis)
        val edits = levenshtein(r, h)
        val n = r.size.coerceAtLeast(1)
        return Result(edits, r.size, edits.toDouble() / n)
    }

    /** Word-level Levenshtein distance (substitution / insertion / deletion = 1 each). */
    private fun levenshtein(a: List<String>, b: List<String>): Int {
        val prev = IntArray(b.size + 1) { it }
        val cur = IntArray(b.size + 1)
        for (i in 1..a.size) {
            cur[0] = i
            for (j in 1..b.size) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(
                    prev[j] + 1,        // deletion
                    cur[j - 1] + 1,     // insertion
                    prev[j - 1] + cost  // substitution / match
                )
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.size]
    }
}
