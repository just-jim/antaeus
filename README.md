## Thought Process

### (Day 1)

#### First Steps
In order to get familiarized with Kotlin language and the challenge project I spend some time exploring the project and taking some short courses online on how to use Kotlin properly. After exploring the project, I got familiarised with the existing codebase and understood what is where, and how everything is connected. Then I started creating a rough plan on how to structure the project. This rough plan was:
- Implement the billing functionality
- Create a scheduler (every 1st of month) to process pending invoices
- Create a scheduler (every hour) to retry process invoices that failed to process before
- Add Unit-Tests for the project
#### Implementing the basics
To begin with I implemented some functionality to fetch the pending invoices, and then a basic functionality to charge the invoices. To do so I implemented a fake PaymentProvider that would return randomly all the possible exceptions in order to handle all of them appropriately.
#### Handling Exceptions
In this point of the development I decided to handle the exceptions that may occur during the charging process of the invoices. The basic idea is that if during the charging process something goes wrong, the invoice will acquire a status of:
- FAILED. This status means that something went wrong while charging the invoice (e.g Network error), but we could retry to handle this invoice again later. There is going to be a scheduler that will run every hour fetching all invoices with this status and try to handle them until they get paid.
- ERROR. This status means that something went so wrong (e.g the customer didn't exist on the database, or the currency of the invoice was different from the customer currency) that the automated invoice handling system won't be able to handle this invoice. So probably the administrators would need to manually handle the invoices with this status.
- INSUFFICIENT_FUNDS. This status means that the customer had insufficient funds on their account, and the invoice could not be paid. In that case the subscription of the user must become inactive until the customer has sufficient funds and the invoice manages to get paid.
#### Currency Exchange Service 
In order to avoid getting rejected payments due to currency mismatch, I implemented a CurrencyExchangeService in order to convert the invoice amount to the equivalent amount of the customer's currency. That doesn't mean that we won't get the CurrencyMismatchException, because the customer may have changed his currency in the PaymentProvider's account, who in that case will throw the CurrencyMismatchException. 
#### Scheduler
The scheduler is probably the most essential part of that project. The need of a robust scheduler that won’t fail and will initiate the process of billing each month like clockwork is the most important factor for a successful billing service. After conducting research on how to implement a native Kotlin scheduler, I decided to use the java.util.Timer class. The Timer class has a schedule() method that conveniently lets you schedule an exact date that you want a process to run. So as originally planned I implemented 2 schedulers:
- A monthly scheduler that will run every 1st of the month, and it will charge all the pending invoices. 
- An hourly scheduler that will run every hour and will try to charge any invoice that failed to get charged in the past. (due to Network errors, etc)

### (Day 2)

#### Unit Tests 
At this point the need to be sure that everything runs and will run as intended is getting bigger. So I implemented a series of unit tests for the BillingService and the CurrencyExchangeService to be sure that the invoices will get the propper status after any incident and thus they are going to be handled appropriately. Some other safety scenarios were also checked, like not being able to charge by mistake an already paid invoice, and that the currency of an invoice will be converted to the customer's invoice when there is a mismatch.
#### Improvements on scheduler
After creating the unit tests, I realized that the scheduler is the core of the project. If the scheduler stops working it must be easy and fast to make it run again. At the beginning I exposed 2 end-points to the REST API in order to get information on the scheduler status for both hourly and monthly schedulers. Afterwards I exposed more end-point to be able to manually start and stop each scheduler. With this combination of end-points, we could externally automate a process to check the schedulers status and if any of the schedulers is down, start it again so the invoices will keep getting handled.
#### Locks
By thinking what are going to be the needs in such a project I realized that an administrator would probably want to have the option to handle individual invoices on demand. For that purpose I implemented another end-point to handle individual invoices based on their id. After extensive testing on the whole project I realized that if the volume of invoices is big enough the process of handling all the pending invoices could take a while. And now that we have exposed end-points to trigger manual handling of invoices, the 2 processes could run (in extreme cases) simultaneously and charge the same invoice twice, or modify it’s status differently. To prevent that and ensure robustness of the project I added a ReentrantLock to queue the processes that are going to modify the invoices.

### (Day 3)

#### Updated DB fields
At this point I was going through the possible needs of such a project, to realize that some very important variables were missing from the invoice and customer models. Fields such as the timestamp of the creation and latest modification of each instance, and the subscription status of a customer. So I added to the database the necessary fields and made sure they get the appropriate values when they need to.
#### Setting customer subscription_status
I realized that some kind of action should be taken when an invoice was unable to get paid due to insufficient funds in the customer account. It would make sense to mark that customer’s subscription status inactive in order to stop receiving the product services until the invoice gets paid successfully.
#### Another scheduler approach
While reading some theory about scheduler robustness I realized that another approach on the scheduler functionality could be implemented. Using a cron job would make the process of scheduling much more clean and less susceptible to failures. Having a cron job handling the schedule would eliminate the fear of the java Timer thread dying unexpectedly causing the billing scheduling to stop. So to demonstrate this functionality I modified the docker file to install cron and include a cron file with instructions to schedule the monthly (0 0 1 * *) and hourly (0 * * * *) schedulers. I tested the cron scheduler functionality while running the project on it’s docker container and it works extremely well. For the purpose of this exercise though, I will keep the previous implemented scheduler in order for the project to be able to run with it’s full functionality even in an environment that doesn’t support cronjobs. If you want to test the project with the use of cron scheduler though you will just have to uncomment some code in the Dockerfile and deactivate the native scheduler by commenting some code in the AntaeusApp.kt file. I have left notes in the comments of the files to make those changes.

## Final thoughts

After exploring the interesting world of Kotlin I have to say that I really enjoyed developing this project! The decisions I had to take were challenging and the project as a whole was intriguing. The thought process of my implementation is documented above but of course there are aspects of the project that I would improve further. A list of my ideas on further improvements:
- Modify the BillingService to use multithreading on invoice processing in order to have a really scalable service.
- Use a third party service to retrieve the currency exchange rates in order to use the latest conversion rates each time an invoice amount is converted
- Improve the locking mechanism to not lock the whole invoice handling process but lock each individual invoice while it is handled. This is required in order to have the multithreaded implementation mentioned previously
- An extensive logging system to collect all the information of any attempt of the service
- An automated notification system to notify the customers with an email for unsuccessful charges. Possible even a grace period before deactivating the customer subscription in order for him to have a chance to pay a failed auto-charged invoice

### The final design

After 3 days implementing this project I ended up having a stable working billing service. I hope this explanation of how the project works, will help you navigate with ease through the code.

The billing service project has 3 distinct functionalities:

- Processing of the pending invoices. All the invoices that have a status of “pending”, are the invoices that haven’t yet processed by the service and they have to be processed and get charged on the 1st of the next month.
- Processing of the failed invoices. All the invoices that have a status of “failed”, are the invoices that have been processed before but failed to get charged. There is a process that continuously will try to process these invoices until they get charged.
- Processing of individual invoices. This functionality exist for administrators to process individual invoices when needed

Those functionalities have their corresponding REST end-points:

- /rest/v1/billing/pending_invoices
- /rest/v1/billing/failed_invoices
- /rest/v1/billing/invoice/<invoice_id>

There is a scheduler that runs the processing of pending invoices and the processing of failed invoices. The scheduler has 2 sub-schedulers:

- The monthly scheduler. This scheduler runs on every 1st of the month, and it triggers the processing of pending invoices.
- The hourly scheduler. This scheduler runs every hour, and it triggers the processing of failed invoices

The schedulers can be started, stopped and check their running status via some REST end-points:

- /rest/v1/scheduler/hourly/start
- /rest/v1/scheduler/hourly/stop
- /rest/v1/scheduler/hourly/check


- /rest/v1/scheduler/monthly/start
- /rest/v1/scheduler/monthly/stop
- /rest/v1/scheduler/monthly/check

The main idea of how the whole billing service works is that the service will try to get pending invoices charged on the 1st of the month. 

If the charge fails due to a reason that could be bypassed on a future attempt it will get the status failed to get handled later by the hourly scheduler. 

If the charge fails due to a reason that won't change if we attempt to charge the invoice in the future, the invoice will get the status error and an administrator will have to handle this invoice manually after communicating with the customer or the paying provider according to the case.

If the charge gets rejected due to insufficient funds on a customer’s account, the invoice will get the status “insufficient funds” and the customer subscription status will become inactive in order to stop receiving the services. In case that an invoice with the status “insufficient funds” manages to get paid in the future the customer’s subscription status will become active again.

This sums up the functionality of the implemented project.


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
