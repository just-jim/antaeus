package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService : InvoiceService
) {
    fun run() {
        val pendingInvoices = invoiceService.fetchPending()

        pendingInvoices.forEach {
            logger.info{"Found pending invoice with id: ${it.id}"}
        }
    }
}
