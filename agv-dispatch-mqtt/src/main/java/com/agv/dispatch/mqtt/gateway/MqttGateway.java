package com.agv.dispatch.mqtt.gateway;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

@MessagingGateway(defaultRequestChannel = "mqttOutputChannel")
public interface MqttGateway {

    void send(@Header(MqttHeaders.TOPIC) String topic, String payload);

    void send(@Header(MqttHeaders.TOPIC) String topic,
              @Header(MqttHeaders.QOS) int qos,
              String payload);

    void send(@Header(MqttHeaders.TOPIC) String topic, byte[] payload);

    void send(@Header(MqttHeaders.TOPIC) String topic,
              @Header(MqttHeaders.QOS) int qos,
              byte[] payload);
}
