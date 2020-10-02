package io.pleo.antaeus.core.scheduler

import io.pleo.antaeus.core.services.BillingService
import java.util.Date
import java.util.Timer
import kotlin.concurrent.schedule
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class Scheduler(private val billingService: BillingService) {

    // This function is a loop that will run every 1st of the month
    fun startMonthly() {
        // Fetch the DateTime object of the next billing date (1st of next month)
        val firstOfNextMonth : Date = get1stOfNextMonth()
        logger.info("Schedule next billing monthly run for: $firstOfNextMonth")

        Timer("MonthlySchedule", false).schedule(time = firstOfNextMonth) {
            try {
                billingService.runMonthly()
            }
            catch (e: Exception) {
                logger.error("Billing failed with error: ${e.message}")
            }
            finally {
                startMonthly()
            }
        }
    }

    // This function is a loop that will run every hour
    fun startHourly() {
        // Fetch the DateTime object of the next billing date (1st of next month)
        val oneHourFromNow : Date = get1HourFromNow()
        logger.info("Schedule next billing of failed invoices for: $oneHourFromNow")

        Timer("HourlySchedule", false).schedule(time = oneHourFromNow) {
            try {
                billingService.runHourly()
            } catch (e: Exception) {
                logger.error("Billing of failed invoices failed with error: ${e.message}")
            } finally {
                startHourly()
            }
        }
    }

    // This function will return a Date object that represent the 1st of the next month
    private fun get1stOfNextMonth(): Date{
        val currentDate = now()
        val firstOfNextMonth : LocalDateTime

        firstOfNextMonth = if(currentDate.monthValue != 12)
            LocalDateTime.of(currentDate.year, currentDate.monthValue + 1, 1,0,0)
        else
            LocalDateTime.of(currentDate.year+1,1,1,0,0)

        return java.sql.Timestamp.valueOf(firstOfNextMonth)
    }

    // This function will return a Date object that correspond to 1hour from now
    private fun get1HourFromNow(): Date{
        return java.sql.Timestamp.valueOf(now().plusHours(1))
    }
}