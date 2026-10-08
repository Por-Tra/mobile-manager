package com.lucas.mobilemanager.block

import android.content.Context
import android.content.Intent
import com.lucas.mobilemanager.domain.rules.BlockReason
import java.time.Duration
import java.time.Instant

/** Ce que l'écran de blocage affiche, transmis par extras d'intent. */
data class BlockScreenArgs(
    val packageName: String,
    val reason: BlockReason,
    val until: Instant?,
) {
    fun toIntent(context: Context, activity: Class<*>): Intent =
        Intent(context, activity)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_UNTIL, until?.toEpochMilli() ?: NO_VALUE)
            .apply {
                when (reason) {
                    BlockReason.StrictSession -> putExtra(EXTRA_REASON, REASON_STRICT)
                    BlockReason.GlobalRule -> putExtra(EXTRA_REASON, REASON_GLOBAL)
                    is BlockReason.Profile -> putExtra(EXTRA_REASON, REASON_PROFILE)
                        .putExtra(EXTRA_PROFILE_ID, reason.profileId)
                        .putExtra(EXTRA_PROFILE_NAME, reason.name)
                    is BlockReason.DailyLimitReached -> putExtra(EXTRA_REASON, REASON_LIMIT)
                        .putExtra(EXTRA_LIMIT_MINUTES, reason.limit.toMinutes())
                }
            }

    companion object {
        fun from(intent: Intent): BlockScreenArgs? {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
            val reason = when (intent.getStringExtra(EXTRA_REASON)) {
                REASON_STRICT -> BlockReason.StrictSession
                REASON_GLOBAL -> BlockReason.GlobalRule
                REASON_PROFILE -> BlockReason.Profile(
                    profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0),
                    name = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty(),
                )
                REASON_LIMIT -> BlockReason.DailyLimitReached(
                    Duration.ofMinutes(intent.getLongExtra(EXTRA_LIMIT_MINUTES, 0)),
                )
                else -> return null
            }
            val until = intent.getLongExtra(EXTRA_UNTIL, NO_VALUE)
                .takeIf { it != NO_VALUE }
                ?.let(Instant::ofEpochMilli)
            return BlockScreenArgs(packageName, reason, until)
        }

        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_PROFILE_NAME = "profile_name"
        private const val EXTRA_LIMIT_MINUTES = "limit_minutes"
        private const val EXTRA_UNTIL = "until"
        private const val NO_VALUE = -1L

        private const val REASON_STRICT = "strict"
        private const val REASON_GLOBAL = "global"
        private const val REASON_PROFILE = "profile"
        private const val REASON_LIMIT = "limit"
    }
}
