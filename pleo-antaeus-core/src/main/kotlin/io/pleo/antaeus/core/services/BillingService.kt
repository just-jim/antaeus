package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.exceptions.UnsuccessfulPaymentException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService : InvoiceService,
    private val customerService: CustomerService
) {
    fun run() {

        // Fetching the pending invoices
        val pendingInvoices = invoiceService.fetchPending()
        logger.info{"Found ${pendingInvoices.count()} pending invoices"}

        // Processing the pending invoices
        pendingInvoices.forEach {
            processInvoice(it)
        }
    }

    private fun processInvoice(invoice: Invoice){

        logger.info{"~~~~~~~~~~~~"}
        logger.info{"Processing invoice with id: ${invoice.id} "}

        //This is the final status that the invoice will have at the end of the process
        var status: InvoiceStatus = invoice.status

        try {
            //Get the customer
            val customer = customerService.fetch(invoice.customerId)
            logger.info("Customer with id: ${customer.id} fetched")

            //Try to charge the invoice amount to the customer
            val payment = paymentProvider.charge(invoice,customer)
            logger.info("Invoice payment result: $payment")

            //Check the payment result to throw exception in case it failed
            if(!payment)
                throw UnsuccessfulPaymentException()

            status = InvoiceStatus.PAID;
        }
        catch (e: CustomerNotFoundException){
            status = InvoiceStatus.FATAL_ERROR
            logger.info("The customer ${invoice.customerId} of the invoice was not found.")
        }
        catch (e: CurrencyMismatchException){
            status = InvoiceStatus.FATAL_ERROR
            logger.info("The currency of the customer does not match the currency of the invoice")
        }
        catch (e: NetworkException){
            status = InvoiceStatus.ERROR
            logger.info("There was a network error while charging the invoice")
        }
        catch (e: UnsuccessfulPaymentException){
            status = InvoiceStatus.ERROR
            logger.info("The payment on the PaymentProvider wan unsuccessful")
        }

        // If the status of the invoice changed, update the invoice on the DB
        if(status != invoice.status) {
            invoiceService.updateInvoice(Invoice(invoice.id, invoice.customerId, invoice.amount, status))
            logger.info("The invoice updated it's status to $status")
        }
    }
}
