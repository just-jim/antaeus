package io.pleo.antaeus.core.scheduler

import io.pleo.antaeus.core.services.BillingService
import kotlin.concurrent.schedule
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import mu.KotlinLogging
import java.time.ZoneId
import java.util.*

private val logger = KotlinLogging.logger {}

class Scheduler(private val billingService: BillingService) {

    private var lastHourlyRun: LocalDateTime = now()
    private var nextFirstOfMonth: Date = get1stOfNextMonth()
    private lateinit var hourlyTimer : TimerTask
    private lateinit var monthlyTimer : TimerTask

    // This function is a loop that will run every 1st of the month
    fun startMonthlyScheduler() {
        // Fetch the DateTime object of the next billing date (1st of next month)
        val firstOfNextMonth : Date = get1stOfNextMonth()
        logger.info("Schedule next billing monthly run for: $firstOfNextMonth")

        // Check if another instance of the Timer is running and cancel it
        if(this::monthlyTimer.isInitialized)
            monthlyTimer.cancel()

        monthlyTimer = Timer("MonthlySchedule", false).schedule(time = firstOfNextMonth) {
            try {
                nextFirstOfMonth = firstOfNextMonth
                billingService.processPendingInvoices()
            }
            catch (e: Exception) {
                logger.error("Billing failed with error: ${e.message}")
            }
            finally {
                startMonthlyScheduler()
            }
        }
    }

    // This function will stop the monthly scheduler
    fun stopMonthlyScheduler() {
        if(this::monthlyTimer.isInitialized)
            monthlyTimer.cancel()
    }


    // This function will check if the monthly scheduler is out of schedule
    fun checkMonthlyScheduler() : String {
        if(now().isAfter(nextFirstOfMonth.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()))
        {
            return "the monthly scheduler is down."
        }
        else
        {
            return "the monthly scheduler is up."
        }
    }


    // This function is a loop that will run every hour
    fun startHourlyScheduler() {
        // Fetch the DateTime object of the next billing date (1st of next month)
        logger.info("Schedule hourly billing for failed invoices")

        // Check if another instance of the Timer is running and cancel it
        if(this::hourlyTimer.isInitialized)
            hourlyTimer.cancel()

        hourlyTimer = Timer("HourlySchedule", false).schedule(delay = 0,period = 3600000)
        {
            try {
                lastHourlyRun = now()
                billingService.processFailedInvoices()
            } catch (e: Exception) {
                logger.error("Billing of failed invoices failed with error: ${e.message}")
            }
        }
    }

    // This function will stop the hourly scheduler
    fun stopHourlyScheduler() {
        if(this::hourlyTimer.isInitialized)
            hourlyTimer.cancel()
    }

    // This function will check if the hourly scheduler is out of schedule
    fun checkHourlyScheduler() : String {
        if(now().isAfter(lastHourlyRun.plusHours(1)))
        {
            return "the hourly scheduler is down."
        }
        else
        {
            return "the hourly scheduler is up."
        }
    }

    // This function will return a Date object that represent the 1st of the next month
    private fun get1stOfNextMonth(): Date{
        val currentDate = now()
        var firstOfNextMonth : LocalDateTime

        firstOfNextMonth = if(currentDate.monthValue != 12)
            LocalDateTime.of(currentDate.year, currentDate.monthValue + 1, 1,0,0)
        else
            LocalDateTime.of(currentDate.year+1,1,1,0,0)

        return java.sql.Timestamp.valueOf(firstOfNextMonth)
    }
}