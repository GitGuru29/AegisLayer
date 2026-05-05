package com.aegislayer.daemon.engine

import android.content.Context
import android.util.Log
import com.aegislayer.daemon.trace.TraceEngine
import com.aegislayer.daemon.trace.TraceLevel
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * AegisLayer's "night school" — the engine that makes the ML model smarter over time.
 *
 * Instead of relying only on the training phrases we hardcoded, this engine
 * watches how the daemon actually behaves in the real world, and periodically
 * retrains the ML model with those real observations.
 *
 * When does it retrain?
 * - At night (11PM to 4AM) when you're probably asleep
 * - When the phone is plugged in / docked (you're not using it)
 * - Never more than once every 6 hours (to save battery)
 *
 * The result: the longer you use AegisLayer, the smarter it gets at
 * understanding your personal automation preferences.
 */
class SelfLearningEngine(private val context: Context) {

    // The background job running our periodic check loop
    private var learningJob: Job? = null

    // SupervisorJob so one failure doesn't kill the whole coroutine scope
    private val learningScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // When did we last retrain? Used to enforce the cooldown period.
    private var lastRetrainTimestamp: Long = 0

    // Is the device currently plugged in? Updated by the service.
    private var isCurrentlyCharging = false

    // Don't retrain more than once every 6 hours — it's CPU work and the data
    // doesn't change that fast anyway
    private val MIN_RETRAIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

    companion object {
        private const val TAG = "SelfLearning"
    }

    /**
     * Kicks off the learning scheduler.
     * Runs a loop every 5 minutes that checks: "Is now a good time to retrain?"
     */
    fun start() {
        // Don't start twice
        if (learningJob?.isActive == true) return
        
        learningJob = learningScope.launch {
            TraceEngine.log(TraceLevel.INFO, TAG, "Self-Learning Engine started")
            
            // This loop runs forever (until stop() is called)
            while (isActive) {
                checkAndRetrain()
                delay(5 * 60 * 1000L) // Nap for 5 minutes between checks
            }
        }
        Log.d("AegisLayer", "SelfLearningEngine: Scheduler started")
    }

    /**
     * Shuts down the learning scheduler cleanly.
     */
    fun stop() {
        learningJob?.cancel()
        learningJob = null
        Log.d("AegisLayer", "SelfLearningEngine: Scheduler stopped")
    }

    /**
     * Called by the service whenever the phone gets plugged in or unplugged.
     *
     * If the phone just got plugged in, we wait 2 minutes (to make sure
     * it's not just a quick top-up) and then try to retrain.
     */
    fun onChargingStateChanged(charging: Boolean) {
        isCurrentlyCharging = charging
        if (charging) {
            learningScope.launch {
                delay(2 * 60 * 1000L) // Give it 2 minutes to settle
                if (isCurrentlyCharging) {
                    // Still charging after 2 min — looks like a real charge session
                    checkAndRetrain()
                }
            }
        }
    }

    /**
     * Decides whether NOW is a good time to retrain the model.
     *
     * Conditions:
     * 1. At least 6 hours since the last retrain (cooldown)
     * 2. EITHER it's the midnight window (11PM-4AM) OR the device is charging
     */
    private suspend fun checkAndRetrain() {
        val now = System.currentTimeMillis()
        
        // Respect the cooldown — no point retraining 5 minutes after we just did
        if (now - lastRetrainTimestamp < MIN_RETRAIN_INTERVAL_MS) return

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Is it late at night? The phone is probably sitting on a nightstand.
        val isMidnightWindow = hour in 23..23 || hour in 0..4

        // Retrain if it's nighttime OR the phone is on a charger
        val shouldRetrain = isMidnightWindow || isCurrentlyCharging
        if (!shouldRetrain) return

        performRetrain()
    }

    /**
     * Does the actual retraining work:
     * 1. Pull all recorded usage patterns from disk
     * 2. Convert them into training observations (sentence + tags)
     * 3. Rebuild the ML model from scratch with base data + new observations
     * 4. Save the updated model to disk
     */
    private suspend fun performRetrain() {
        withContext(Dispatchers.IO) {
            try {
                TraceEngine.log(TraceLevel.INFO, TAG, "Starting ML model retrain...")

                // Ask the pattern logger to convert raw observations into training data
                val observations = UsagePatternLogger.generateTrainingObservations(context)
                
                if (observations.isEmpty()) {
                    TraceEngine.log(TraceLevel.INFO, TAG, "No new patterns to learn from. Skipping retrain.")
                    return@withContext
                }

                // Rebuild the classifier from scratch: textbook + new knowledge
                RuleMLTrainer.retrain(context, observations)
                lastRetrainTimestamp = System.currentTimeMillis()

                val patternCount = UsagePatternLogger.loadPatterns(context).size
                TraceEngine.log(
                    TraceLevel.INFO, TAG,
                    "Retrain complete! Learned from $patternCount usage patterns → ${observations.size} training observations"
                )

            } catch (e: Exception) {
                TraceEngine.log(TraceLevel.ACTION, TAG, "Retrain failed: ${e.message}")
                Log.e("AegisLayer", "SelfLearningEngine: Retrain error", e)
            }
        }
    }
}
