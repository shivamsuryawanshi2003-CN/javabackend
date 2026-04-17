package com.jobra.authservice.event;

public class RegistrationNotificationEvent {

    private final String email;
    private final String name;
    private final String eventType;

    public RegistrationNotificationEvent(String email, String name, String eventType) {
        this.email = email;
        this.name = name;
        this.eventType = eventType;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getEventType() {
        return eventType;
    }
}
