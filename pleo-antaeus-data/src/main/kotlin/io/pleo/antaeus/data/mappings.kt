/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.ResultRow
import java.time.LocalDateTime

fun ResultRow.toInvoice(): Invoice = Invoice(
    id = this[InvoiceTable.id],
    amount = Money(
        value = this[InvoiceTable.value],
        currency = Currency.valueOf(this[InvoiceTable.currency])
    ),
    status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
    customerId = this[InvoiceTable.customerId],
    created_ts = LocalDateTime.parse(this[InvoiceTable.created_ts]),
    updated_ts = LocalDateTime.parse(this[InvoiceTable.updated_ts])
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency]),
    subscriptionStatus = SubscriptionStatus.valueOf(this[CustomerTable.subscription_status]),
    created_ts = LocalDateTime.parse(this[CustomerTable.created_ts]),
    updated_ts = LocalDateTime.parse(this[CustomerTable.updated_ts])
)
