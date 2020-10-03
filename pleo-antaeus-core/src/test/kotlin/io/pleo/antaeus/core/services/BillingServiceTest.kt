package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime.now

class BillingServiceTest {

    //InvoiceStatus.values()[Random.nextInt(0, InvoiceStatus.values().size)]
    private val invoice = Invoice(1, 1, Money(BigDecimal(10),Currency.DKK), InvoiceStatus.PENDING,now(),now())
    private val pendingInvoice = Invoice(1, 1, Money(BigDecimal(10),Currency.DKK), InvoiceStatus.PENDING,now(),now())
    private val failedInvoice = Invoice(1, 1, Money(BigDecimal(10),Currency.DKK), InvoiceStatus.FAILED,now(),now())
    private val paidInvoice = Invoice(1, 1, Money(BigDecimal(10),Currency.DKK), InvoiceStatus.PAID,now(),now())

    private val customer = Customer(1, Currency.DKK,SubscriptionStatus.ACTIVE,now(),now())
    private val customerWithWrongCurrency = Customer(1, Currency.EUR,SubscriptionStatus.ACTIVE,now(),now())

    private val paymentProvider = mockk<PaymentProvider>() {
        every { charge(any(),any()) } returns true
    }

    private val invoiceService = mockk<InvoiceService>(relaxed = true) {
        every { fetchAll() } returns listOf(invoice)
        every { fetchPending() } returns listOf(pendingInvoice)
        every { fetchFailed() } returns listOf(failedInvoice)
    }

    private val customerService = mockk<CustomerService>(relaxed = true) {
        every { fetch(any()) } returns customer
    }

    private val billingService = BillingService(
            paymentProvider = paymentProvider,
            invoiceService = invoiceService,
            customerService = customerService
    )

    @Test
    fun `Successful payment provider charge`() {
        every { paymentProvider.charge(any(),any()) } returns true
        billingService.processPendingInvoices()
        assert(pendingInvoice.status == InvoiceStatus.PAID)
    }

    @Test
    fun `Payment provider charge failed due to insufficient funds`() {
        every { paymentProvider.charge(any(),any()) } returns false
        billingService.processPendingInvoices()
        assert(pendingInvoice.status == InvoiceStatus.INSUFFICIENT_FUNDS)
        assert(customer.subscriptionStatus == SubscriptionStatus.INACTIVE)
    }

    @Test
    fun `Payment provider charge network error`() {
        every { paymentProvider.charge(any(),any()) } throws NetworkException()
        billingService.processPendingInvoices()
        assert(pendingInvoice.status == InvoiceStatus.FAILED)
    }

    @Test
    fun `Payment provider charge missing user`() {
        every { paymentProvider.charge(any(),any()) } throws CustomerNotFoundException(customer.id)
        billingService.processPendingInvoices()
        assert(pendingInvoice.status == InvoiceStatus.ERROR)
    }

    @Test
    fun `Payment provider charge currency mismatch`() {
        every { paymentProvider.charge(any(),any()) } throws CurrencyMismatchException(invoice.id,customer.id)
        billingService.processPendingInvoices()
        assert(pendingInvoice.status == InvoiceStatus.ERROR)
    }

    @Test
    fun `Will not charge paid invoices`() {
        every { invoiceService.fetchPending() } returns listOf(paidInvoice)
        billingService.processPendingInvoices()
        verify(exactly = 0) { paymentProvider.charge(any(),any()) }
    }

    @Test
    fun `Will convert the currency if customer currency mismatch the invoice's currency`() {
        every { customerService.fetch(any()) } returns customerWithWrongCurrency
        billingService.processPendingInvoices()
        assert(pendingInvoice.amount.currency == customerWithWrongCurrency.currency)
    }
}