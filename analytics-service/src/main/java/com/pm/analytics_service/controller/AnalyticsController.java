package com.pm.analytics_service.controller;

import com.pm.analytics_service.kafka.KafkaConsumer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private final KafkaConsumer kafkaConsumer;

    public AnalyticsController(KafkaConsumer kafkaConsumer) {
        this.kafkaConsumer = kafkaConsumer;
    }

    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        List<KafkaConsumer.PatientActivity> activities = kafkaConsumer.getActivities();
        return Map.of(
                "eventsProcessed", activities.size(),
                "patientEvents", activities,
                "status", "CONSUMING_KAFKA");
    }
}
