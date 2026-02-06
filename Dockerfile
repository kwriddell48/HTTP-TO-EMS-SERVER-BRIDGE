# ---------- Build stage ----------
# java-http-to-ems - HTTP server forwarding to TIBCO EMS via JMS
# Requires lib/ containing TIBCO EMS JARs: tibjms.jar, jms-2.0.jar, javax.jms-api-2.0.1.jar
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy pom.xml first (better layer caching)
COPY pom.xml .
# Copy source code
COPY src ./src
# Copy TIBCO EMS JARs (tibjms.jar, jms-2.0.jar, javax.jms-api-2.0.1.jar) from lib/
COPY lib ./lib

# Build the JAR
#RUN mvn -B package -DskipTests
#RUN mvn clean package -DskipTests
RUN mvn package -DskipTests
#RUN java -jar target/*.jar

#RUN jar tf target/*.jar | grep TibjmsConnectionFactory.class
#RUN jar tf target/*.jar | grep http/to/ems/messager/Main.class
RUN jar tf target/*.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/*.jar java-http-to-ems-1.0.jar
# Copy TIBCO EMS JARs (tibjms.jar, jms-2.0.jar, javax.jms-api-2.0.1.jar) from build stage
COPY --from=build /build/lib ./lib
COPY src/main/resources/logging.properties .

EXPOSE 8080
# Define environment variables

#"Example command line:");
#  java -cp <classpath> http.to.ems.messager.Main [port] [DEBUG=level]
# Valid DEBUG levels: OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, ALL
# APP_ARGS  8080 DEBUG=FINE

ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    APP_ARGS="8080 DEBUG=FINE"

CMD java $JAVA_OPTS -cp "java-http-to-ems-1.0.jar:lib/*" http.to.ems.messager.Main $APP_ARGS

#CMD ["sh", "-c", "java $JAVA_OPTS -cp 'java-http-to-ems-1.0:lib/*' http.to.ems.messager.Main $APP_ARGS"]
#CMD ["sh", "-c", "java $JAVA_OPTS -jar java-http-to-ems-1.0.jar $APP_ARGS"]
#CMD ["java", "-cp", "java-http-to-ems-1.0.jar:lib/*", "http.to.ems.messager.Main", $APP_ARGS"]

#CMD ["sh", "-c", "java $JAVA_OPTS -jar java-http-to-ems-1.0.jar $APP_ARGS"]
