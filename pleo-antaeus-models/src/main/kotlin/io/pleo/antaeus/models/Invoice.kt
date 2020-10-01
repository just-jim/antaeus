package io.pleo.antaeus.models

data class Invoice(
    val id: Int,
    val customerId: Int,
    var amount: Money,
    var status: InvoiceStatus
)
