package com.itantra.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Word error rate, as shown on the benchmark screen. Runs on the computer: `gradlew test`. */
class WerTest {
    @Test
    fun ignoresCaseAndPunctuation() {
        assertEquals(Wer.Result(0, 5, 0.0), Wer.compare("Send water to camp two.", "send water, to camp two"))
        assertEquals(0, Wer.compare("आगे का रास्ता बंद है।", "आगे का रास्ता बंद है").edits)
    }

    @Test
    fun countsSubstitutionsDeletionsAndInsertions() {
        // "ahead" deleted + "blocked" -> "locked" substituted = 2 edits over 5 reference words.
        assertEquals(Wer.Result(2, 5, 0.4), Wer.compare("the road ahead is blocked", "the road is locked"))
        assertEquals(1, Wer.compare("send help", "send more help").edits)
        assertEquals(1.0, Wer.compare("we need help", "").wer, 0.0)
    }
}
