package citu.edu.stathis.mobile.features.exercise.adaptive

import android.content.Context
import android.content.SharedPreferences

/**
 * RCT arm assignment for adaptive vs static feedback evaluation.
 *
 * Teachers/researchers can flip `rct_static_control` in SharedPreferences
 * `stathis_adaptive` for the static-control cohort.
 *
 * Practice sessions append `_PRACTICE` so ungraded research volume stays separable
 * from classroom task interventions in evaluation exports.
 */
object RctExperimentPrefs {
    private const val PREFS = "stathis_adaptive"
    private const val KEY_STATIC = "rct_static_control"
    private const val KEY_ARM_LOG = "rct_arm_assigned"

    const val CONTEXT_TASK = "TASK"
    const val CONTEXT_PRACTICE = "PRACTICE"

    fun isStaticControl(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STATIC, false)

    fun setStaticControl(context: Context, enabled: Boolean) {
        prefs(context)
            .edit()
            .putBoolean(KEY_STATIC, enabled)
            .putString(KEY_ARM_LOG, if (enabled) "STATIC" else "ADAPTIVE")
            .apply()
    }

    fun baseArm(context: Context): String =
        if (isStaticControl(context)) "STATIC" else "ADAPTIVE"

    fun experimentArm(context: Context, sessionContext: String = CONTEXT_TASK): String =
        composeArm(baseArm(context), sessionContext)

    fun composeArm(baseArm: String, sessionContext: String): String {
        val normalizedBase =
            when {
                baseArm.equals("STATIC", ignoreCase = true) ||
                    baseArm.startsWith("STATIC", ignoreCase = true) -> "STATIC"
                else -> "ADAPTIVE"
            }
        return if (sessionContext.equals(CONTEXT_PRACTICE, ignoreCase = true)) {
            "${normalizedBase}_PRACTICE"
        } else {
            normalizedBase
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
