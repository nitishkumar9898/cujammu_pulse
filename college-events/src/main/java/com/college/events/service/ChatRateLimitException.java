package com.college.events.service;

/** No private count, sender identity or content escapes via this exception. */
public class ChatRateLimitException extends RuntimeException {
    public ChatRateLimitException() { super("Chat posting limit reached; retry later"); }
}
