# --- Build Stage ---
FROM ubuntu:22.04 AS build

RUN apt-get update && \
apt-get install -y curl unzip gnupg software-properties-common && \
curl -L -o corretto.deb https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.deb && \
apt install -y ./corretto.deb && \
rm corretto.deb

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /app

COPY . .
RUN ./gradlew bootJar

# --- Runtime Stage ---
FROM ubuntu:22.04

RUN apt-get update && \
apt-get install -y curl unzip && \
curl -L -o corretto.deb https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.deb && \
apt install -y ./corretto.deb && \
rm corretto.deb

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
