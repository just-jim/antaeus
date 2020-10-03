package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.SubscriptionStatus
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock

private val logger = KotlinLogging.logger {}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService : InvoiceService,
    private val customerService: CustomerService
) {

    private val currencyExchange : CurrencyExchangeService = CurrencyExchangeService()
    private val invoiceProcessingLock = ReentrantLock()

    fun processPendingInvoices() {
        try {
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the pending invoices
            val pendingInvoices = invoiceService.fetchPending()
            logger.info{"================"}
            logger.info{"Found ${pendingInvoices.count()} pending invoices"}

            // Processing the pending invoices
            pendingInvoices.forEach {
                processInvoice(it)
            }
        }
        finally {
            // Un-lock the invoice processing lock
            invoiceProcessingLock.unlock()
        }
    }

    fun processFailedInvoices(){

        try{
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the failed invoices
            val failedInvoices = invoiceService.fetchFailed()
            logger.info{"================"}
            logger.info{"Found ${failedInvoices.count()} failed invoices to retry charging"}

            // Processing the pending invoices
            failedInvoices.forEach {
                processInvoice(it)
            }
        }
        finally {
            // Un-lock the invoice processing lock
            invoiceProcessingLock.unlock()
        }
    }

    fun processSpecificInvoice(invoiceId: Int)
    {
        try {
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the specific invoice
            val invoice = invoiceService.fetch(invoiceId)
            logger.info{"Invoice with id ${invoice.id} was found"}

            processInvoice(invoice)

        }
        catch (e: InvoiceNotFoundException)
        {
            logger.info("There is no invoice with id $invoiceId.")
        }
        finally {
            // Un-lock the invoice processing lock
            invoiceProcessingLock.unlock()
        }
    }

    private fun processInvoice(invoice: Invoice){

        if(invoice.status == InvoiceStatus.PAID) {
            logger.info{"Invoice: ${invoice.id} is already paid"}
            return
        }

        try {
            logger.info{"~~~~~~~~~~~~"}
            logger.info{"Processing invoice with id: ${invoice.id} "}

            //Get the customer
            val customer = customerService.fetch(invoice.customerId)
            logger.info("Customer with id: ${customer.id} fetched")

            //Before charging the invoice convert the currency of the invoice to the current customer currency
            if(invoice.amount.currency != customer.currency)
            {
                logger.info("The invoice currency (${invoice.amount.currency}) wasn't matching customers currency (${customer.currency}).")
                currencyExchange.modifyInvoiceAmountToProperCurrency(invoice,customer)
                logger.info("The invoice amount was modified to: "+String.format("%.2f",invoice.amount.value)+" "+invoice.amount.currency)
            }

            //Try to charge the invoice amount to the customer
            val successfulPayment = paymentProvider.charge(invoice,customer)
            logger.info("Invoice payment result: $successfulPayment")

            //Check if the payment was unsuccessful due to unsufficient funs and take the appropriate actions
            if(!successfulPayment) {
                // Set the customer subscription status to Inactive
                customer.subscriptionStatus = SubscriptionStatus.INACTIVE
                customerService.update(customer)
                throw UnsuccessfulPaymentException()
            }
            else if(customer.subscriptionStatus == SubscriptionStatus.INACTIVE)
            {
                // Set the customer subscription status to active if the invoice of an inactive subscription managed to get paid
                customer.subscriptionStatus = SubscriptionStatus.ACTIVE
                customerService.update(customer)
            }

            invoice.status = InvoiceStatus.PAID;
        }
        catch (e: CustomerNotFoundException){
            invoice.status = InvoiceStatus.ERROR
            logger.info("The customer ${invoice.customerId} of the invoice was not found.")
        }
        catch (e: CurrencyMismatchException){
            invoice.status = InvoiceStatus.ERROR
            logger.info("The currency of the customer does not match the currency of the invoice")
        }
        catch (e: NetworkException){
            invoice.status = InvoiceStatus.FAILED
            logger.info("There was a network error while charging the invoice")
        }
        catch (e: UnsuccessfulPaymentException){
            invoice.status = InvoiceStatus.INSUFFICIENT_FUNDS
            logger.info("The payment on the PaymentProvider wan unsuccessful. The customer has insufficient funds")
        }
        finally{
            // Update the invoice status in the DB
            invoiceService.update(invoice)
            logger.info("The invoice updated it's status to ${invoice.status}")
        }
    }
}
