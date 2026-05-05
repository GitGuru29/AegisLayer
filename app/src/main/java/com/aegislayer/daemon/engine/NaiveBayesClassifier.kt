package com.aegislayer.daemon.engine

import kotlin.math.ln

/**
 * The brain behind AegisLayer's natural language understanding.
 *
 * This is a Naive Bayes text classifier — a real ML algorithm that learns
 * word patterns from training examples. Think of it like teaching a dog:
 * show it enough examples of "sit" paired with the sitting action, and
 * eventually it understands what "sit" means, even if you say "please sit down".
 *
 * How it works in plain english:
 * 1. During training, we feed it sentences tagged with intents (like "mute" → ACT_MUTE)
 * 2. It builds a mental map of which words appear frequently with which intents
 * 3. When a new sentence comes in, it checks every word against its memory
 *    and calculates which intent is the most probable match
 *
 * Why Naive Bayes?
 * - It's tiny (< 100 lines), runs instantly, and needs zero external libraries
 * - It works surprisingly well for short command-like sentences
 * - It's the same algorithm used in email spam filters since the 90s
 */
class NaiveBayesClassifier {

    // All the intent tags we've seen during training (e.g., "ACT_MUTE", "COND_SCREEN_OFF")
    private val tags = mutableSetOf<String>()

    // Every unique word the model has ever encountered
    private val vocab = mutableSetOf<String>()

    // For each tag, how many times each word appeared in its training examples
    // e.g., tagWordCounts["ACT_MUTE"]["silence"] = 3
    private val tagWordCounts = mutableMapOf<String, MutableMap<String, Int>>()

    // How many training documents (sentences) were associated with each tag
    private val tagDocCounts = mutableMapOf<String, Int>()

    // Total number of training examples we've processed
    private var totalDocs = 0

    /**
     * Teaches the model by showing it a sentence and telling it what intents
     * that sentence represents.
     *
     * Example: train("mute my phone", ["ACT_MUTE", "ACT_DND_ON"])
     * This tells the model that words like "mute" and "phone" are strongly
     * associated with the MUTE and DND_ON intents.
     */
    fun train(text: String, docTags: List<String>) {
        val tokens = tokenize(text)
        vocab.addAll(tokens)
        totalDocs++
        
        for (tag in docTags) {
            tags.add(tag)
            tagDocCounts[tag] = tagDocCounts.getOrDefault(tag, 0) + 1
            
            // Count how many times each word appears under this tag
            val wordCounts = tagWordCounts.getOrPut(tag) { mutableMapOf() }
            for (token in tokens) {
                wordCounts[token] = wordCounts.getOrDefault(token, 0) + 1
            }
        }
    }

    /**
     * Given a sentence the model has never seen before, predict which intents
     * it most likely refers to. Returns multiple tags if several are probable.
     *
     * The math behind this (Bayes' Theorem):
     *   P(Tag | Words) ∝ P(Tag) × P(Word₁ | Tag) × P(Word₂ | Tag) × ...
     *
     * We use logarithms so multiplying tiny probabilities doesn't collapse to zero.
     */
    fun predict(text: String): List<String> {
        if (tags.isEmpty() || totalDocs == 0) return emptyList()

        val tokens = tokenize(text)
        val results = mutableListOf<Pair<String, Double>>()

        for (tag in tags) {
            // How likely is this tag overall? (Prior probability)
            // If "ACT_MUTE" appeared in 10 out of 50 training examples, P(ACT_MUTE) = 0.2
            val pTag = ln(tagDocCounts[tag]!!.toDouble() / totalDocs)
            
            var pWordsGivenTag = 0.0
            val wordCounts = tagWordCounts[tag]!!
            val totalWordsInTag = wordCounts.values.sum()
            val vocabSize = vocab.size.coerceAtLeast(1)

            // For each word in the input, how likely is it to appear under this tag?
            // We add +1 (Laplace smoothing) so unknown words get a tiny probability
            // instead of zero — this prevents one weird word from ruining everything
            for (token in tokens) {
                val count = wordCounts.getOrDefault(token, 0)
                val pWord = ln((count + 1).toDouble() / (totalWordsInTag + vocabSize))
                pWordsGivenTag += pWord
            }

            val score = pTag + pWordsGivenTag
            results.add(tag to score)
        }

        if (results.isEmpty()) return emptyList()

        // Sort by highest probability first
        val sorted = results.sortedByDescending { it.second }
        
        // We need multi-label output because a single sentence like
        // "mute when youtube opens" should return BOTH a condition AND an action.
        // So we return the top tag plus anything else that's close enough in probability.
        val maxScore = sorted.first().second
        val tolerance = 7.0 // How far below the top score a tag can be and still count
        
        return sorted.filter { it.second >= maxScore - tolerance }.map { it.first }
    }

    /**
     * Breaks a sentence into individual words for analysis.
     * - Lowercases everything (so "Mute" and "mute" are the same)
     * - Strips punctuation (commas, periods, etc. don't matter)
     * - Drops tiny words like "is", "a", "to" — they appear everywhere and add noise
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
    }
}
