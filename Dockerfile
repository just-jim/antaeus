FROM adoptopenjdk/openjdk11:latest

RUN apt-get update \
    && apt-get install -y sqlite3 \
    && apt-get install -y cron

COPY . /anteus
WORKDIR /anteus

# Copy cron_config file to the cron.d directory
COPY cron_config /etc/cron.d/antaeus

# Give execution rights on the cron job
RUN chmod 0644 /etc/cron.d/antaeus

# Apply cron job
RUN crontab /etc/cron.d/antaeus

# Create the cron log file
RUN touch /var/log/cron.log

EXPOSE 7000
# When the container starts: build, test and run the app while starting the cron scheduler as well.
CMD ./gradlew build && ./gradlew test && cron && ./gradlew run