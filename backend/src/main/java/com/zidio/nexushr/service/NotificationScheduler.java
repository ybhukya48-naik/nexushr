package com.zidio.nexushr.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private final NotificationService notificationService;

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /*
     * Check every 30 seconds for notifications whose scheduled time
     * has arrived.
     */
    @Scheduled(fixedDelay = 30000)
    public void processScheduledNotifications() {
        notificationService.processScheduledNotifications();
    }
}
