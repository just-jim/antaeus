package io.pleo.antaeus.core.external

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import kotlin.random.Random

// Based on the possible Exceptions that we have to handle, this class simulates a PaymentProvider that may return any of those exceptions
class SomeBank : PaymentProvider {

    override fun charge(invoice: Invoice, customer:Customer): Boolean {

        //In order to throw a currencyMismatchException we have to compare customer currency with invoice currency
        if(customer.currency != invoice.amount.currency){
            throw CurrencyMismatchException(invoice.id,customer.id)
        }

        //Randomly with a chance of 10% throw NetworkException
        if(Random.nextInt(from=1,until = 11) == 1){
            throw NetworkException()
        }

        //Randomly with a chance of 50% return false
        return (Random.nextInt(from=1,until = 3) == 1)
    }
}