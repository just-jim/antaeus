## Thought Process

#### First Steps (Day 1)
In order to get familiarized with Kotlin language and the challenge project I spend some time exploring the project and taking some courses online on how to use Kotlin properly. After exploring the project, I got familiarised with the existing codebase and understood what is where, and how everything communicated. Then I started creating a rough plan on how to structure the project. This rough plan was:
Implement the billing functionality
Create a scheduler (every 1st of month) to process pending invoices
Create a scheduler (every hour) to retry process invoices that failed to process before
Add Unit-Tests for the project
#### Implementing the basics
To begin with I implemented some functionality to fetch the pending invoices, and then a basic functionality to charge the invoices. To do so I made a mock PaymentProvider that would return all the possible exceptions in order to handle all of them appropriately.
#### Handling Exceptions
In this point of the development I decided to handle the exceptions that may occur during the charging process of the invoices. The basic idea is that if during the charging process something goes wrong, the invoice will acquire a status of either:
- ERROR. This status means that something went wrong on the charging of the invoice (e.g Network error), but we could retry to handle this invoice again later. There is going to be a scheduler that will run every hour fetching all invoices with status ERROR and try to handle them until they get paid.
- FATAL_ERROR. This status means that something went so wrong. (e.g the invoices customer didn't exist on the database, or the currency of the invoice was different from the customer currency) that the automated invoice handling system won't be able to handle this invoice. So probably the staff of the theoretical company would need to go manually handle the invoices with this status.
- INSUFFICIENT_FUNDS. This status means that the customer had insufficient funds on their account, and the invoice could not be charged. In that case the subscription of the user must not be renewed until the customer has sufficient funds.
#### Currency Exchange Service 
In order to avoid getting rejected payments due to currency mismatch, I implemented a CurrencyExchangeService in order to convert the invoice amount to the customer's currency. That doesn't mean that we won't get the CurrencyMismatchException ever, because the customer may have changed his currency in the PaymentProvider's DB, who in that case will return the CurrencyMismatchException. The CurrencyExchangeService will help the billing project in the case of a customer changing currency in our system/DB after an invoice was issued with his previous currency. 
#### Scheduler
To make the whole project work as intended there must be a scheduler. My initial thought would be to use a cronjob that would call an endpoint every 1st of the month (0 0 1 * *). Then I realized that calling an end-point has limitations and vulnerabilities that have to be avoided in a serious task such as billing invoices. So to avoid using the endpoint as the initialization of the scheduler process I decided to implement a more native to the Kotlin project approach and after conducting research on how to implement a native scheduler, I decided to use the java.util.Timer class. Timer class has a schedule() method to schedule when you want to run a function. So i implemented 2 schedulers:
- A monthly scheduler that will run every 1st of the month, and it will charge all the pending invoices. 
- An hourly scheduler that will run every hour and will try to charge any invoice that failed to get charged in the past. (due to Network errors, etc)
#### Unit Tests (Day 2)
At this point the need to be sure that everything runs and will run as intended is getting bigger. So I implemented a series of unit tests for the BillingService and the CurrencyExchangeService to be sure that the invoices will get the propper status after any incident and thus they are going to be handled appropriately. Some other safety scenarios were also checked, like not being able to charge by mistake an already paid invoice, and that the currency of an invoice will be converted to the customer's invoice when there is a mismatch.
#### Improvements on scheduler
After creating the unit test, I realized that the scheduler is the core of the project. If the scheduler stops working it must be easy and fast to make it run again. At the beginning I exposed 2 end-points to the REST API in order to get information on the scheduler status for both hourly and monthly schedulers. Afterwards I exposed more end-point to be able to manually start and stop each scheduler. With this combination of end-points, in the system that this project would run, we could externally automate a process to check the schedulers status and if any of the schedulers is down, start it again so the invoices will keep getting handled.


## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!
