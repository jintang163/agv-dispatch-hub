FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
COPY agv-dispatch-common ./agv-dispatch-common
COPY agv-dispatch-core ./agv-dispatch-core
COPY agv-dispatch-mqtt ./agv-dispatch-mqtt
COPY agv-dispatch-api ./agv-dispatch-api

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /app/agv-dispatch-api/target/agv-dispatch-api-*.jar /app/agv-dispatch-api.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "agv-dispatch-api.jar"]
