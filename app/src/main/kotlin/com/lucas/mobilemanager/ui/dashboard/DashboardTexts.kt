package com.lucas.mobilemanager.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.domain.dashboard.Trend
import com.lucas.mobilemanager.ui.format.Formats

/** Comparaison avec la période précédente : factuelle, jamais culpabilisante. */
@Composable
fun comparisonText(summary: DashboardSummary, period: Period): String {
    val delta = Formats.duration(summary.comparison.delta)
    val today = period == Period.TODAY
    return when (summary.comparison.trend) {
        Trend.LESS -> stringResource(if (today) R.string.trend_less_day else R.string.trend_less_week, delta)
        Trend.MORE -> stringResource(if (today) R.string.trend_more_day else R.string.trend_more_week, delta)
        Trend.SAME -> stringResource(if (today) R.string.trend_same_day else R.string.trend_same_week)
        Trend.NO_HISTORY -> stringResource(R.string.trend_no_history)
    }
}
