package io.github.markyu0401.everheard.data.person

import io.github.markyu0401.everheard.data.memory.Commitment

data class PersonProfile(
    val id: String,
    val name: String,
    val sessionIds: List<String>,
    val lastTalkedAt: Long,
    val openCommitments: List<Commitment> = emptyList(),
    val quotes: List<String> = emptyList()
)
