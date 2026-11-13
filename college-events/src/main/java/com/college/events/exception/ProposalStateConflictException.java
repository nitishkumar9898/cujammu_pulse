package com.college.events.exception;

/** Expected proposal lifecycle conflict; unrelated programming failures remain server errors. */
public final class ProposalStateConflictException extends IllegalStateException {
    public ProposalStateConflictException(String message) { super(message); }
}
