package io.pleo.antaeus.core.services
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.Money

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
    private val eur2dkk: Double = 7.44490
    private val usd2dkk: Double = 6.33633
    private val sek2dkk: Double = 0.710847
    private val gbp2dkk: Double = 8.16453

    private fun toDKK(value: Double, currency: Currency): Double {
        return when (currency) {
            Currency.EUR -> value * eur2dkk
            Currency.USD -> value * usd2dkk
            Currency.SEK -> value * sek2dkk
            Currency.GBP -> value * gbp2dkk
            else -> value
        }
    }

    private fun DKKTo(value: Double, currency: Currency): Double{
        return when(currency){
            Currency.EUR -> value/eur2dkk
            Currency.USD -> value/usd2dkk
            Currency.SEK -> value/sek2dkk
            Currency.GBP -> value/gbp2dkk
            else -> value
        }
    }

    fun modifyInvoiceAmountToProperCurrency(invoice: Invoice, customer: Customer){
        //To convert the invoice currency to customer's currency we will firstly convert it to DKK and then from DKK to the customer's currency
        val dkkValue = toDKK(invoice.amount.value.toDouble(), invoice.amount.currency)
        val customerCurrencyValue = DKKTo(dkkValue,customer.currency).toBigDecimal()
        invoice.amount = Money(customerCurrencyValue,customer.currency)
    }
            
}