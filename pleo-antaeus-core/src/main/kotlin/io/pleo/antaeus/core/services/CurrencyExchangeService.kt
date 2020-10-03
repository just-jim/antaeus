package io.pleo.antaeus.core.services
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.Money
import java.math.BigDecimal

/**
 * This Currency exchange service will convert any currency to any other
 * in order to be able to charge an invoice even if it is in different currency
 * from the customers currency.
 * Normally a service like this would fetch the exchange rates from a CurrencyExchange
 * website API. For simplicity I will fetch the current exchange rates for the
 * wanted currencies by hand from xe.com
 */
class CurrencyExchangeService {

    // The exchange rates
    private val eur2dkk = BigDecimal(7.44490)
    private val usd2dkk = BigDecimal(6.33633)
    private val sek2dkk = BigDecimal(0.710847)
    private val gbp2dkk = BigDecimal(8.16453)

    /**
     * Convert a value to DKK
     *
     * @param value the value that we want to convert
     * @param currency the previous currency
     * @return the equivalent DKK amount
     */
    private fun toDKK(value: BigDecimal, currency: Currency): BigDecimal {
        return when (currency) {
            Currency.EUR -> value * eur2dkk
            Currency.USD -> value * usd2dkk
            Currency.SEK -> value * sek2dkk
            Currency.GBP -> value * gbp2dkk
            else -> value
        }
    }

    /**
     * Convert a value from DKK to another currency
     *
     * @param value the value that we want to convert
     * @param currency the targeted currency
     * @return the equivalent amount converted in the targeted currency
     */
    private fun DKKTo(value: BigDecimal, currency: Currency): BigDecimal{
        return when(currency){
            Currency.EUR -> value/eur2dkk
            Currency.USD -> value/usd2dkk
            Currency.SEK -> value/sek2dkk
            Currency.GBP -> value/gbp2dkk
            else -> value
        }
    }

    /**
     * Modifies an invoice to get matching currency with it's corresponding customer
     *
     * @param invoice The invoice that we want to modify
     * @param customer the customer that corresponds to the invoice
     */
    fun modifyInvoiceAmountToProperCurrency(invoice: Invoice, customer: Customer){
        //To convert the invoice currency to customer's currency we will firstly convert it to DKK and then from DKK to the customer's currency
        val dkkValue = toDKK(invoice.amount.value, invoice.amount.currency)
        val customerCurrencyValue = DKKTo(dkkValue,customer.currency)
        invoice.amount = Money(customerCurrencyValue,customer.currency)
    }
            
}