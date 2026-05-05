package com.aegislayer.daemon.engine

import android.content.Context
import android.util.Log

/**
 * The ML model's "textbook" — manages everything the classifier knows.
 *
 * This object has two jobs:
 * 1. Load the BASE training data (hardcoded phrases we wrote by hand)
 * 2. Load any SELF-LEARNED data from previous usage (saved to disk)
 *
 * Think of it like a student who starts with a textbook (base data),
 * but also takes notes from real life (self-learned data). When they
 * study for the next test (retrain), they review both.
 *
 * The model is a singleton — there's only ever one classifier instance
 * running at a time, shared across the entire app.
 */
object RuleMLTrainer {

    // The one and only classifier instance. Null until first access.
    private var _classifier: NaiveBayesClassifier? = null

    // Where we save self-learned observations so they survive app restarts
    private const val MODEL_FILE = "aegis_ml_model.json"

    /**
     * Returns the trained classifier, creating it if needed.
     *
     * The first time this is called, it:
     * 1. Creates a fresh NaiveBayesClassifier
     * 2. Feeds it all the base training phrases (our "textbook")
     * 3. Loads any previously learned patterns from disk (the "notes")
     *
     * After that, it just returns the cached instance.
     */
    fun getClassifier(context: Context? = null): NaiveBayesClassifier {
        if (_classifier == null) {
            _classifier = NaiveBayesClassifier()
            
            // Step 1: Teach it the fundamentals — our hand-crafted training phrases
            loadBaseTrainingData(_classifier!!)
            
            // Step 2: Layer on any knowledge it picked up from watching the user
            if (context != null) {
                loadPersistedModel(context, _classifier!!)
            }
        }
        return _classifier!!
    }

    /**
     * Throws away the old model and builds a new one from scratch.
     *
     * Called by the SelfLearningEngine during idle times (midnight or charging).
     * The new model includes both the base textbook AND fresh observations
     * from real device usage.
     */
    fun retrain(context: Context, extraObservations: List<Pair<String, List<String>>>) {
        val model = NaiveBayesClassifier()
        loadBaseTrainingData(model)
        
        // Teach the new model everything we learned from watching the user
        for ((text, tags) in extraObservations) {
            model.train(text, tags)
        }
        
        // Swap in the new model — all future predictions use updated knowledge
        _classifier = model
        persistModel(context, extraObservations)
        Log.d("AegisLayer", "RuleMLTrainer: Model retrained with ${extraObservations.size} new observations")
    }

    /**
     * Saves self-learned observations to disk as JSON.
     * We only persist the observations (not the full model weights) so we can
     * always rebuild the model from base data + saved observations.
     */
    private fun persistModel(context: Context, observations: List<Pair<String, List<String>>>) {
        try {
            val gson = com.google.gson.Gson()
            val data = observations.map { mapOf("text" to it.first, "tags" to it.second) }
            val json = gson.toJson(data)
            java.io.File(context.filesDir, MODEL_FILE).writeText(json)
        } catch (e: Exception) {
            Log.e("AegisLayer", "RuleMLTrainer: Failed to persist model — ${e.message}")
        }
    }

    /**
     * Loads previously saved self-learned observations from disk
     * and feeds them into the classifier as additional training data.
     */
    private fun loadPersistedModel(context: Context, model: NaiveBayesClassifier) {
        try {
            val file = java.io.File(context.filesDir, MODEL_FILE)
            if (!file.exists()) return
            
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val data: List<Map<String, Any>> = gson.fromJson(file.readText(), type)
            
            for (entry in data) {
                val text = entry["text"] as? String ?: continue
                @Suppress("UNCHECKED_CAST")
                val tags = entry["tags"] as? List<String> ?: continue
                model.train(text, tags)
            }
            Log.d("AegisLayer", "RuleMLTrainer: Loaded ${data.size} persisted observations")
        } catch (e: Exception) {
            Log.e("AegisLayer", "RuleMLTrainer: Failed to load persisted model — ${e.message}")
        }
    }

    /**
     * The base training dataset — our hand-crafted "textbook".
     *
     * Each call to model.train() teaches the classifier:
     *   "When someone says THIS phrase, they probably mean THESE intents"
     *
     * The more varied examples we give for each intent, the better the model
     * gets at recognizing paraphrased or novel phrasing from real users.
     *
     * Intent naming convention:
     *   COND_*  = Condition tags  (triggers: when does this rule activate?)
     *   ACT_*   = Action tags     (effects: what should the phone do?)
     */
    private fun loadBaseTrainingData(model: NaiveBayesClassifier) {

        // ==========================================================================
        //  CONDITION INTENTS — "When should this rule trigger?"
        // ==========================================================================

        // --- Screen Off: user locked or pocketed their phone ---
        model.train("when my screen is off", listOf("COND_SCREEN_OFF"))
        model.train("if the display turns off", listOf("COND_SCREEN_OFF"))
        model.train("screen goes black", listOf("COND_SCREEN_OFF"))
        model.train("when screen locks", listOf("COND_SCREEN_OFF"))

        // --- Screen On: user just woke up their phone ---
        model.train("when my screen is on", listOf("COND_SCREEN_ON"))
        model.train("if the display turns on", listOf("COND_SCREEN_ON"))
        model.train("unlock phone", listOf("COND_SCREEN_ON"))
        model.train("when i wake my phone", listOf("COND_SCREEN_ON"))

        // --- Charging: phone is plugged in or on a dock ---
        model.train("when charging", listOf("COND_CHARGING"))
        model.train("plugged in to power", listOf("COND_CHARGING"))
        model.train("connected to charger", listOf("COND_CHARGING"))
        model.train("while on charger", listOf("COND_CHARGING"))
        model.train("when docked", listOf("COND_CHARGING"))

        // --- YouTube: user is watching videos ---
        model.train("open youtube", listOf("COND_APP_YOUTUBE"))
        model.train("when i launch youtube", listOf("COND_APP_YOUTUBE"))
        model.train("starting youtube app", listOf("COND_APP_YOUTUBE"))
        model.train("watching youtube", listOf("COND_APP_YOUTUBE"))
        model.train("youtube is open", listOf("COND_APP_YOUTUBE"))
        model.train("when youtube is running", listOf("COND_APP_YOUTUBE"))

        // --- Instagram: user is browsing social media ---
        model.train("open instagram", listOf("COND_APP_INSTAGRAM"))
        model.train("when i launch instagram", listOf("COND_APP_INSTAGRAM"))
        model.train("browsing instagram", listOf("COND_APP_INSTAGRAM"))
        model.train("instagram is open", listOf("COND_APP_INSTAGRAM"))

        // ==========================================================================
        //  ACTION INTENTS — "What should the phone do?"
        // ==========================================================================

        // --- Mute: kill all sound. Often paired with DND for full silence ---
        model.train("mute my phone", listOf("ACT_MUTE", "ACT_DND_ON"))
        model.train("silence the device", listOf("ACT_MUTE", "ACT_DND_ON"))
        model.train("turn off all sound", listOf("ACT_MUTE"))
        model.train("quiet mode", listOf("ACT_MUTE", "ACT_DND_ON"))
        model.train("make it silent", listOf("ACT_MUTE"))

        // --- Do Not Disturb: block notification interruptions ---
        model.train("turn on do not disturb", listOf("ACT_DND_ON"))
        model.train("enable dnd", listOf("ACT_DND_ON"))
        model.train("block notifications", listOf("ACT_DND_ON"))
        model.train("turn off do not disturb", listOf("ACT_DND_OFF"))
        model.train("disable dnd", listOf("ACT_DND_OFF"))
        model.train("allow notifications", listOf("ACT_DND_OFF"))

        // --- Brightness Low: dim the screen to save battery or reduce eye strain ---
        model.train("reduce brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("lower the brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("dim the screen", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("decrease brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("turn down brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("low brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("set brightness to zero", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("lowest brightness", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("dim the screen completely", listOf("ACT_BRIGHTNESS_LOW"))
        model.train("make screen darker", listOf("ACT_BRIGHTNESS_LOW"))

        // --- Brightness High: crank the screen up for outdoor use ---
        model.train("set brightness to max", listOf("ACT_BRIGHTNESS_HIGH"))
        model.train("maximum brightness", listOf("ACT_BRIGHTNESS_HIGH"))
        model.train("brightest screen", listOf("ACT_BRIGHTNESS_HIGH"))
        model.train("increase brightness", listOf("ACT_BRIGHTNESS_HIGH"))
        model.train("turn up brightness", listOf("ACT_BRIGHTNESS_HIGH"))
        model.train("full brightness", listOf("ACT_BRIGHTNESS_HIGH"))

        // --- Auto Rotate: toggle screen orientation lock ---
        model.train("turn on auto rotate", listOf("ACT_ROTATE_ON"))
        model.train("enable screen rotation", listOf("ACT_ROTATE_ON"))
        model.train("turn off auto rotate", listOf("ACT_ROTATE_OFF"))
        model.train("disable screen rotation", listOf("ACT_ROTATE_OFF"))
        model.train("lock orientation", listOf("ACT_ROTATE_OFF"))

        // ==========================================================================
        //  COMBO EXAMPLES — The most important training data!
        //
        //  These teach the model that a single sentence can contain BOTH a
        //  condition (when) AND an action (what). Without these, the classifier
        //  would struggle to return multiple intents from one prompt.
        // ==========================================================================
        model.train("when i open youtube reduce brightness", listOf("COND_APP_YOUTUBE", "ACT_BRIGHTNESS_LOW"))
        model.train("dim screen when youtube opens", listOf("COND_APP_YOUTUBE", "ACT_BRIGHTNESS_LOW"))
        model.train("lower brightness on youtube", listOf("COND_APP_YOUTUBE", "ACT_BRIGHTNESS_LOW"))
        model.train("when youtube is open turn down the brightness", listOf("COND_APP_YOUTUBE", "ACT_BRIGHTNESS_LOW"))
        model.train("when i open youtube turn on auto rotate and max brightness", listOf("COND_APP_YOUTUBE", "ACT_ROTATE_ON", "ACT_BRIGHTNESS_HIGH"))
        model.train("mute the phone and dim the screen when charging", listOf("ACT_MUTE", "ACT_BRIGHTNESS_LOW", "COND_CHARGING"))
        model.train("if the screen is off turn on do not disturb", listOf("COND_SCREEN_OFF", "ACT_DND_ON"))
        model.train("when instagram opens mute and reduce brightness", listOf("COND_APP_INSTAGRAM", "ACT_MUTE", "ACT_BRIGHTNESS_LOW"))
        model.train("silence phone when youtube is running", listOf("COND_APP_YOUTUBE", "ACT_MUTE"))
        model.train("enable dnd and dim screen when screen off", listOf("COND_SCREEN_OFF", "ACT_DND_ON", "ACT_BRIGHTNESS_LOW"))
    }
}
