package io.pleo.antaeus.models

import java.time.LocalDateTime

data class Invoice(
    val id: Int,
    val customerId: Int,
    var amount: Money,
    var status: InvoiceStatus,
    val created_ts: LocalDateTime,
    val updated_ts: LocalDateTime
)
