/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchPending() : List<Invoice> {
        return dal.fetchInvoice(InvoiceStatus.PENDING)
    }

    fun fetchFailed() : List<Invoice> {
        return dal.fetchInvoice(InvoiceStatus.FAILED)
    }

    fun update(invoice: Invoice){
        dal.updateInvoice(invoice)
    }
}
