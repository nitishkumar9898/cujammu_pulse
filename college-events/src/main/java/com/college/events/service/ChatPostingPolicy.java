package com.college.events.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Operational capacity defaults, not disciplinary policy; effective changes require reviewed configuration. */
@Component
public class ChatPostingPolicy {
    private final int senderPerMinute;
    private final int groupPerMinute;
    public ChatPostingPolicy(@Value("${app.chat.sender-per-group-per-minute:20}") int senderPerMinute,
                             @Value("${app.chat.group-per-minute:200}") int groupPerMinute) {
        if (senderPerMinute < 1 || senderPerMinute > 1000 || groupPerMinute < 1 || groupPerMinute > 10000)
            throw new IllegalArgumentException("Invalid chat posting limits");
        this.senderPerMinute = senderPerMinute; this.groupPerMinute = groupPerMinute;
    }
    public int senderPerMinute() { return senderPerMinute; }
    public int groupPerMinute() { return groupPerMinute; }
}
