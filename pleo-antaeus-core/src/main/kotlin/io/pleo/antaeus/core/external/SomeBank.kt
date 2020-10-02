package io.pleo.antaeus.core.external

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import kotlin.random.Random

// Based on the possible Exceptions that we have to handle, this class simulates a PaymentProvider that may return any of those exceptions
class SomeBank : PaymentProvider {

    override fun charge(invoice: Invoice, customer:Customer): Boolean {

        //Randomly with a chance of 5% throw a CustomerNotFoundException to simulate that the payment provider does not have this customer anymore
        if(Random.nextInt(from=0,until=100) <= 5){
            throw CustomerNotFoundException(customer.id)
        }

        //Randomly with a chance of 5% throw a CurrencyMismatchException to simulate that the customer changed his currency on the payment provider
        if(customer.currency != invoice.amount.currency){
            throw CurrencyMismatchException(invoice.id,customer.id)
        }

        //Randomly with a chance of 5% throw a NetworkException to simulate a Network error
        if(Random.nextInt(from=0,until=100) <= 5){
            throw NetworkException()
        }

        //Randomly with a chance of 5% return false to simulate customer insufficient funds
        return (Random.nextInt(from=0,until=100) <= 95)
    }
}