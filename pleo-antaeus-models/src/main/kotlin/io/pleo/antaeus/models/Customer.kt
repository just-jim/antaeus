package io.pleo.antaeus.models

import java.time.LocalDateTime

data class Customer(
    val id: Int,
    val currency: Currency,
    var subscriptionStatus: SubscriptionStatus,
    val created_ts: LocalDateTime,
    val updated_ts: LocalDateTime
)
