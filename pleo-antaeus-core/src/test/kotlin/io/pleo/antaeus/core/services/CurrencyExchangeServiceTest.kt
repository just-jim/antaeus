package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.random.Random

class CurrencyExchangeServiceTest {

    private val currencyExchange = CurrencyExchangeService()

    @Test
    fun `modify invoice currency from EUR to DKK`(){
        val eurValue = BigDecimal(1)
        val invoice = Invoice(1,1, Money(eurValue,Currency.EUR),InvoiceStatus.PENDING)
        val customer = Customer(1,Currency.DKK)
        currencyExchange.modifyInvoiceAmountToProperCurrency(invoice, customer)
        assert(invoice.amount.value == eurValue * BigDecimal(7.44490))
        assert(invoice.amount.currency == Currency.DKK)
    }

    @Test
    fun `modify invoice currency from DKK to EUR`(){
        val dkkValue = BigDecimal(1)
        val invoice = Invoice(1,1, Money(dkkValue,Currency.DKK),InvoiceStatus.PENDING)
        val customer = Customer(1,Currency.EUR)
        currencyExchange.modifyInvoiceAmountToProperCurrency(invoice, customer)
        assert(invoice.amount.value == dkkValue / BigDecimal(7.44490))
        assert(invoice.amount.currency == Currency.EUR)
    }

    @Test
    fun `modify random amount of currency from EUR to USD`(){
        val eurValue = BigDecimal(Random.nextInt(from=1 , until = 1000))
        val invoice = Invoice(1,1, Money(eurValue,Currency.EUR),InvoiceStatus.PENDING)
        val customer = Customer(1,Currency.USD)
        currencyExchange.modifyInvoiceAmountToProperCurrency(invoice, customer)
        assert(invoice.amount.value == (eurValue * BigDecimal(7.44490)) / BigDecimal(6.33633))
        assert(invoice.amount.currency == Currency.USD)
    }
}