# Use an appropriate base image, for example, OpenJDK 8 JDK
FROM openjdk:8-jdk-slim

# Set environment variables
ENV ACTIVEMQ_HOME /opt/amq-broker
ENV BROKER_HOME /opt/amq-broker-instance

# Copy the broker installation files
COPY . /opt/amq-broker

# Create broker instance
RUN /opt/amq-broker/bin/artemis create /opt/amq-broker-instance --user Fuse_Service --password QAZxsw123 --role amq --allow-anonymous --http-host 0.0.0.0

# Set the working directory
WORKDIR /opt/amq-broker-instance

# Expose necessary ports
EXPOSE 8161

# Start the broker
CMD ["/opt/amq-broker-instance/bin/artemis", "run"]