package com.jobra.authservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobra.authservice.event.RegistrationNotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String registrationTopic;

    public NotificationEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${app.kafka.registration-topic}") String registrationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.registrationTopic = registrationTopic;
    }

    public void sendRegistrationEvent(RegistrationNotificationEvent event) {
        try {
            kafkaTemplate.send(registrationTopic, event.getEmail(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize registration event", ex);
        }
    }
}
