/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.pleo.antaeus.core.exceptions.EntityNotFoundException
import io.pleo.antaeus.core.scheduler.Scheduler
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val thisFile: () -> Unit = {}

class AntaeusRest(
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService,
    private val scheduler: Scheduler
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
            get("/") {
                it.result("Welcome to Antaeus! see AntaeusRest class for routes")
            }
            path("rest") {
                // Route to check whether the app is running
                // URL: /rest/health
                get("health") {
                    it.json("ok")
                }

                // V1
                path("v1") {

                    path("scheduler"){
                        // URL: /rest/v1/scheduler
                        path("hourly"){
                            // URL: /rest/v1/scheduler/hourly

                            path("check") {
                                // URL: /rest/v1/scheduler/hourly/check
                                get {
                                    it.json(scheduler.checkHourlyScheduler())
                                }
                            }

                            path("start") {
                                // URL: /rest/v1/scheduler/hourly/start
                                get {
                                    scheduler.startHourlyScheduler()
                                    it.json("the hourly scheduler start running.")
                                }
                            }

                            path("stop") {
                                // URL: /rest/v1/scheduler/hourly/stop
                                get {
                                    scheduler.stopHourlyScheduler()
                                    it.json("the hourly scheduler stopped running.")
                                }
                            }
                        }

                        path("monthly"){
                            // URL: /rest/v1/scheduler/monthly

                            path("check") {
                                // URL: /rest/v1/scheduler/monthly/check
                                get {
                                    it.json(scheduler.checkMonthlyScheduler())
                                }
                            }

                            path("start") {
                                // URL: /rest/v1/scheduler/monthly/start
                                get {
                                    scheduler.startMonthlyScheduler()
                                    it.json("the monthly scheduler start running.")
                                }
                            }

                            path("stop") {
                                // URL: /rest/v1/scheduler/monthly/stop
                                get {
                                    scheduler.stopMonthlyScheduler()
                                    it.json("the monthly scheduler stopped running.")
                                }
                            }
                        }
                    }

                    path("billing")
                    {
                        path("pending_invoices") {
                            // URL: /rest/v1/billing/pending_invoices
                            get {
                                billingService.runMonthly()
                                it.json("Billing service run (for invoices with status pending)")
                            }
                        }

                        path("failed_invoices") {
                            // URL: /rest/v1/billing/failed_invoices
                            get {
                                billingService.runHourly()
                                it.json("Billing retry service run (for invoices with status error)")
                            }
                        }
                    }

                    path("invoices") {
                        // URL: /rest/v1/invoices
                        get {
                            it.json(invoiceService.fetchAll())
                        }

                        // URL: /rest/v1/invoices/{:id}
                        get(":id") {
                            it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                        }
                    }

                    path("customers") {
                        // URL: /rest/v1/customers
                        get {
                            it.json(customerService.fetchAll())
                        }

                        // URL: /rest/v1/customers/{:id}
                        get(":id") {
                            it.json(customerService.fetch(it.pathParam("id").toInt()))
                        }
                    }
                }
            }
        }
    }
}
