package com.college.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class DynamicFormDtos {
    private DynamicFormDtos() { }

    /** Persistence identity, event ownership and ordering are always server assigned. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Field(String fieldName, String fieldLabel, String fieldType,
                        String options, boolean required, String placeholder) { }

    public record Submit(String name, String rollNo, String department, String semester,
                         java.util.Map<String, String> answers) { }
}
