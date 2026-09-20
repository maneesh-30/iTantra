package com.itantra.app

/**
 * BenchmarkSentences — fixed reference sentences the user reads aloud during the WER
 * benchmark. The app compares its transcription against these to compute the Word Error Rate.
 * Kept short and domain-relevant (rescue / field communication) so they are easy to read.
 */
object BenchmarkSentences {

    fun forLanguage(language: Language): List<String> = when (language) {
        Language.EN -> listOf(
            "Send water to camp two",
            "We need help near the river",
            "The road ahead is blocked",
            "Two people are injured here",
            "Bring the first aid kit quickly",
            "Move everyone to higher ground",
            "The bridge is not safe to cross",
            "We are waiting at the school",
            "Send a doctor as soon as possible",
            "There is no network in this area",
        )
        Language.HI -> listOf(
            "कैंप दो को पानी भेजो",
            "नदी के पास मदद चाहिए",
            "आगे का रास्ता बंद है",
            "यहाँ दो लोग घायल हैं",
            "जल्दी दवाई का डिब्बा लाओ",
            "सबको ऊँची जगह पर ले जाओ",
            "पुल पार करना सुरक्षित नहीं है",
            "हम स्कूल के पास इंतज़ार कर रहे हैं",
            "जल्दी से एक डॉक्टर भेजो",
            "इस इलाके में नेटवर्क नहीं है",
        )
    }
}
