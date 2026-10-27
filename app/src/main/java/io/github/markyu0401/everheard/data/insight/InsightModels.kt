package io.github.markyu0401.everheard.data.insight

import io.github.markyu0401.everheard.data.project.Project

data class InsightCitation(
    val sessionId: String,
    val sessionTitle: String,
    val quote: String,
    val startMs: Long
)

data class InsightObservation(
    val title: String,
    val body: String,
    val action: String? = null,
    val citations: List<InsightCitation> = emptyList()
)

data class InsightReport(
    val periodStart: Long,
    val periodEnd: Long,
    val sessionCount: Int,
    val minutes: Int,
    val decisionCount: Int,
    val commitmentCount: Int,
    val openCommitmentCount: Int,
    val riskCount: Int,
    val ideaCount: Int,
    val projectCount: Int,
    val observations: List<InsightObservation> = emptyList()
)
