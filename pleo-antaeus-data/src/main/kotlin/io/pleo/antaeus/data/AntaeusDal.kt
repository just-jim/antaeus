/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime.now

class AntaeusDal(private val db: Database) {

    fun fetchInvoice(status: InvoiceStatus): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
                .filter { it.status == status }
        }
    }

    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                    it[this.created_ts] = now().toString()
                    it[this.updated_ts] = now().toString()
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun updateInvoice(invoice: Invoice){
        return transaction(db){
            InvoiceTable.update({InvoiceTable.id eq invoice.id}){
                it[this.value] = invoice.amount.value
                it[this.currency] = invoice.amount.currency.toString()
                it[this.status] = invoice.status.toString()
                it[this.updated_ts] = now().toString()
            }
        }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency, subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
                it[subscription_status] = subscriptionStatus.toString()
                it[this.created_ts] = now().toString()
                it[this.updated_ts] = now().toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

    fun updateCustomer(customer: Customer){
        return transaction(db){
            CustomerTable.update({CustomerTable.id eq customer.id}){
                it[this.subscription_status] = customer.subscriptionStatus.toString()
                it[this.updated_ts] = now().toString()
            }
        }
    }
}
