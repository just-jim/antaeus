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

    private val currencyExchange : CurrencyExchangeService = CurrencyExchangeService()

    fun runMonthly() {

        // Fetching the pending invoices
        val pendingInvoices = invoiceService.fetchPending()
        logger.info{"Found ${pendingInvoices.count()} pending invoices"}

        // Processing the pending invoices
        pendingInvoices.forEach {
            processInvoice(it)
        }
    }

    fun runHourly(){

        // Fetching the failed invoices
        val failedInvoices = invoiceService.fetchFailed()
        logger.info{"Found ${failedInvoices.count()} failed invoices to retry charging"}

        // Processing the pending invoices
        failedInvoices.forEach {
            processInvoice(it)
        }

    }

    private fun processInvoice(invoice: Invoice){

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
            val payment = paymentProvider.charge(invoice,customer)
            logger.info("Invoice payment result: $payment")

            //Check the payment result to throw exception in case it failed
            if(!payment)
                throw UnsuccessfulPaymentException()

            invoice.status = InvoiceStatus.PAID;
        }
        catch (e: CustomerNotFoundException){
            invoice.status = InvoiceStatus.FATAL_ERROR
            logger.info("The customer ${invoice.customerId} of the invoice was not found.")
        }
        catch (e: CurrencyMismatchException){
            invoice.status = InvoiceStatus.FATAL_ERROR
            logger.info("The currency of the customer does not match the currency of the invoice")
        }
        catch (e: NetworkException){
            invoice.status = InvoiceStatus.ERROR
            logger.info("There was a network error while charging the invoice")
        }
        catch (e: UnsuccessfulPaymentException){
            invoice.status = InvoiceStatus.INSUFFICIENT_FUNDS
            logger.info("The payment on the PaymentProvider wan unsuccessful. The customer has insufficient funds")
        }
        finally{
            // Update the invoice status in the DB
            invoiceService.updateInvoice(invoice)
            logger.info("The invoice updated it's status to ${invoice.status}")
        }
    }
}
