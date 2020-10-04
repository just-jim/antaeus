package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.SubscriptionStatus
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock

private val logger = KotlinLogging.logger {}

/**
 * This service is handling all the invoice billing processing.
 *
 * Main Logic:
 * The billing service will try to get pending invoices charged on the 1st of the month.
 * If the charge fails due to a reason that could be bypassed on a future attempt it will get the status failed
 * to get handled later.
 * If the charge fails due to a reason that won't change if we attempt to charge the invoice in the future,
 * the invoice will get the status error and an administrator will have to handle this invoice manually
 * after communicating with the customer or the paying provider according to the case.
 *
 * There are also exposed end-points to run the billing processes in order for administrators to be able to process
 * individual invoices, or external schedulers to initiate the billing processes
 *
 * - /rest/v1/scheduler/billing/pending_invoices
 * - /rest/v1/scheduler/billing/failed_invoices
 * - /rest/v1/scheduler/monthly/invoice/<Id>
 *
 * This service is thread safe by using a locking system to prevent simultaneous modification of invoices.
 * This service will handle currency mismatch by using the CurrencyExchangeService when the customer and
 * invoice instances in the DB are inconsistent.
 * Note: That doesn't mean that the customer will have the correct currency account on the PaymentProvider side.
 */
class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService : InvoiceService,
    private val customerService: CustomerService
) {

    // This is the currency exchange service that can convert currencies
    private val currencyExchange : CurrencyExchangeService = CurrencyExchangeService()

    // This is the lock used to make sure that the invoices won't get double-processed by different threads
    private val invoiceProcessingLock = ReentrantLock()

    /**
     * Process pending invoices
     *
     * This function will be called on 1st of each month to initially try to process all invoices marked as pending.
     * All the invoices after processing will acquire a new status of either failed, error or insufficient_funds
     */
    fun processPendingInvoices() {
        try {
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the pending invoices
            val pendingInvoices = invoiceService.fetchPending()
            logger.info{"================ Process Pending Invoices =============="}
            logger.info{"Found ${pendingInvoices.count()} pending invoices"}

            // Processing the pending invoices
            pendingInvoices.forEach {
                processInvoice(it)
            }
        }
        finally {
            // Un-lock the invoice processing lock
            invoiceProcessingLock.unlock()
            logger.info{"========================================================"}
        }
    }

    /**
     * Process failed invoices
     *
     * This function will be called every hour to try process all invoices marked as failed after their initial
     * processing (due to a network error etc). This way we will make sure that all the invoices that can be charged
     * will get charged at the end.
     */
    fun processFailedInvoices(){

        try{
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the failed invoices
            val failedInvoices = invoiceService.fetchFailed()

            logger.info{"================ Process Failed Invoices ==============="}
            logger.info{"Found ${failedInvoices.count()} failed invoices to retry charging"}

            // Processing the pending invoices
            failedInvoices.forEach {
                processInvoice(it)
            }
        }
        finally {
            // Un-lock the invoice processing lock
            invoiceProcessingLock.unlock()
            logger.info{"========================================================"}
        }
    }

    /**
     * Process individual invoices
     *
     * This function could be called manually by an administrator in order to try processing a specific invoice.
     * A scenario that this could be useful is a cases where an invoice was marked as insufficient_funds and after
     * communication of the administrator with the customer, the customer informed that he now have sufficient funds.
     *
     * @param invoiceId the invoice id to get processed
     */
    fun processIndividualInvoice(invoiceId: Int)
    {
        try {
            // Lock the invoice processing lock
            invoiceProcessingLock.lock()

            // Fetching the specific invoice
            val invoice = invoiceService.fetch(invoiceId)
            logger.info{"================ Process Individual Invoice ============"}
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
            logger.info{"========================================================"}
        }
    }

    /**
     * Process invoices
     *
     * This function is the core of the service, it gets an invoice and tries to charge it using the third party
     * payment provider. During the procedure it handles all possible exceptions and updates the invoice with the
     * appropriate status.
     *
     * @param invoice the invoice get processed
     */
    private fun processInvoice(invoice: Invoice){

        // If invoice is already paid return without modifying the invoice
        if(invoice.status == InvoiceStatus.PAID) {
            logger.info{"~~~~~~~~~~~~"}
            logger.info{"Invoice: ${invoice.id} is already paid"}
            return
        }

        try {
            logger.info{"~~~~~~~~~~~~"}
            logger.info{"Processing invoice with id: ${invoice.id} "}

            //Get the customer that correspond to the invoice
            val customer = customerService.fetch(invoice.customerId)
            logger.info("Customer with id: ${customer.id} fetched")

            //Before charging the invoice convert the currency of the invoice to the current customer currency
            if(invoice.amount.currency != customer.currency)
            {
                logger.info("The invoice currency (${invoice.amount.currency}) wasn't matching customers currency (${customer.currency}).")
                currencyExchange.modifyInvoiceAmountToProperCurrency(invoice,customer)
                logger.info("The invoice amount was modified to: "+String.format("%.2f",invoice.amount.value)+" "+invoice.amount.currency)
            }

            //Try to charge the invoice amount to the customer using the third party payment provider
            val successfulPayment = paymentProvider.charge(invoice,customer)
            logger.info("Invoice payment result: $successfulPayment")

            //Check if the payment was unsuccessful due to insufficient funds and take the appropriate actions
            if(!successfulPayment) {
                // Set the customer subscription status to Inactive
                customer.subscriptionStatus = SubscriptionStatus.INACTIVE
                customerService.update(customer)
                logger.info("Customer's subscription status set to inactive")
                throw UnsuccessfulPaymentException()
            }
            else if(customer.subscriptionStatus == SubscriptionStatus.INACTIVE)
            {
                // Set the customer subscription status to active if the invoice of an inactive subscription managed to get paid
                customer.subscriptionStatus = SubscriptionStatus.ACTIVE
                customerService.update(customer)
                logger.info("Customer's subscription status set to active")
            }

            // If nothing went wrong set the invoice status as paid
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
            // Update the invoice in the DB
            invoiceService.update(invoice)
            logger.info("The invoice updated it's status to ${invoice.status}")
        }
    }
}
