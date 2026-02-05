# ---------- Build stage ----------
#  java-http-to-ems
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy pom.xml first (better layer caching)
COPY pom.xml .
# RUN mvn -B dependency:go-offline

# Copy source code
COPY src ./src
# Copy lib 
COPY lib ./lib

# Build the JAR
#RUN mvn -B package -DskipTests
RUN mvn clean package -DskipTests
#RUN java -jar target/*.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/*.jar java-http-to-ems-1.0.jar
# Copy lib 
COPY lib ./lib
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
