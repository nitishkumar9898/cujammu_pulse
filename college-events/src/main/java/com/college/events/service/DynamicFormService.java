package com.college.events.service;

import com.college.events.model.CustomEventFormField;
import com.college.events.model.CustomEventFormResponse;
import com.college.events.model.User;
import com.college.events.dto.DynamicFormDtos;
import com.college.events.security.DomainAccess;
import com.college.events.repository.CustomEventFormFieldRepository;
import com.college.events.repository.CustomEventFormResponseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicFormService {

    private final CustomEventFormFieldRepository fieldRepository;
    private final CustomEventFormResponseRepository responseRepository;
    private final DomainAccess access;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public List<CustomEventFormField> configureFormSchema(Long eventId, List<DynamicFormDtos.Field> fields) {
        access.requireEventManager(eventId);
        if (fields == null || fields.size() > 50) throw new IllegalArgumentException("At most 50 form fields are allowed");
        Set<String> names = new HashSet<>();
        List<CustomEventFormField> replacements = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            DynamicFormDtos.Field field = fields.get(i);
            if (field == null || field.fieldName() == null || !field.fieldName().matches("[A-Za-z][A-Za-z0-9_]{0,59}")
                    || !names.add(field.fieldName()) || field.fieldLabel() == null || field.fieldLabel().isBlank()
                    || field.fieldLabel().length() > 100 || (field.options() != null && field.options().length() > 500)
                    || (field.placeholder() != null && field.placeholder().length() > 200)) {
                throw new IllegalArgumentException("Invalid or duplicate form field");
            }
            String type = field.fieldType() == null ? "TEXT" : field.fieldType();
            if (!Set.of("TEXT", "NUMBER", "DROPDOWN", "CHECKBOX", "TEXTAREA", "URL").contains(type)) {
                throw new IllegalArgumentException("Unsupported form field type");
            }
            replacements.add(CustomEventFormField.builder().eventId(eventId).fieldOrder(i + 1)
                    .fieldName(field.fieldName()).fieldLabel(field.fieldLabel()).fieldType(type)
                    .options(field.options()).required(field.required()).placeholder(field.placeholder()).build());
        }
        // Validate all fields before replacing anything; request IDs can never merge another event's row.
        fieldRepository.deleteByEventId(eventId);
        return fieldRepository.saveAll(replacements);
    }

    @Transactional(readOnly = true)
    public List<CustomEventFormField> getFormSchema(Long eventId) {
        return fieldRepository.findByEventIdOrderByFieldOrderAsc(eventId);
    }

    @Transactional
    public CustomEventFormResponse submitForm(Long eventId, User user,
                                              String nameOverride, String rollOverride,
                                              String deptOverride, String semOverride,
                                              Map<String, String> customAnswers) {
        access.requireSelf(user.getId());
        bounded(nameOverride, 100); bounded(rollOverride, 50); bounded(deptOverride, 80); bounded(semOverride, 30);
        if (customAnswers != null && customAnswers.size() > 50) throw new IllegalArgumentException("Too many answers");
        if (responseRepository.existsByEventIdAndUserId(eventId, user.getId())) {
            throw new IllegalStateException("You have already submitted the registration form for this event.");
        }

        List<CustomEventFormField> fields = getFormSchema(eventId);
        Set<String> allowedNames = new HashSet<>();
        fields.forEach(field -> allowedNames.add(field.getFieldName()));
        if (customAnswers != null) customAnswers.forEach((key, value) -> {
            if (key == null || key.length() > 60 || !allowedNames.contains(key)) throw new IllegalArgumentException("Unknown form answer");
            bounded(value, 2000);
        });
        for (CustomEventFormField field : fields) {
            if (field.isRequired()) {
                Object val = customAnswers != null ? customAnswers.get(field.getFieldName()) : null;
                if (val == null || val.toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Field '" + field.getFieldLabel() + "' is required.");
                }
            }
        }

        String jsonResponses = "{}";
        try {
            if (customAnswers != null) {
                jsonResponses = objectMapper.writeValueAsString(customAnswers);
            }
        } catch (Exception e) {
            log.error("Error serializing form answers: {}", e.getMessage());
        }

        CustomEventFormResponse response = CustomEventFormResponse.builder()
                .eventId(eventId)
                .userId(user.getId())
                .participantName(nameOverride != null && !nameOverride.isBlank() ? nameOverride : user.getName())
                .participantEmail(user.getEmail())
                .participantRollNo(rollOverride != null && !rollOverride.isBlank() ? rollOverride : user.getEnrollmentNo())
                .participantDepartment(deptOverride != null && !deptOverride.isBlank() ? deptOverride : user.getDepartment())
                .participantSemester(semOverride != null && !semOverride.isBlank() ? semOverride : user.getSemester())
                .responsesJson(jsonResponses)
                .build();

        return responseRepository.save(response);
    }

    @Transactional(readOnly = true)
    public Optional<CustomEventFormResponse> getUserResponse(Long eventId, Long userId) {
        access.requireSelf(userId);
        return responseRepository.findByEventIdAndUserId(eventId, userId);
    }

    @Transactional(readOnly = true)
    public List<CustomEventFormResponse> getAllResponses(Long eventId) {
        access.requireEventManager(eventId);
        return responseRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public String exportResponsesCsv(Long eventId) {
        access.requireEventManager(eventId);
        List<CustomEventFormField> fields = getFormSchema(eventId);
        List<CustomEventFormResponse> responses = getAllResponses(eventId);

        StringWriter sw = new StringWriter();
        // Header
        sw.append("Response ID,User ID,Full Name,Email,Roll/Enrollment No,Department,Semester,Submitted At");
        for (CustomEventFormField field : fields) {
            sw.append(",\"").append(safe(field.getFieldLabel())).append("\"");
        }
        sw.append("\n");

        // Rows
        for (CustomEventFormResponse r : responses) {
            sw.append(String.valueOf(r.getId())).append(",")
              .append(String.valueOf(r.getUserId())).append(",")
              .append("\"").append(safe(r.getParticipantName())).append("\",")
              .append("\"").append(safe(r.getParticipantEmail())).append("\",")
              .append("\"").append(safe(r.getParticipantRollNo())).append("\",")
              .append("\"").append(safe(r.getParticipantDepartment())).append("\",")
              .append("\"").append(safe(r.getParticipantSemester())).append("\",")
              .append("\"").append(r.getSubmittedAt() != null ? r.getSubmittedAt().toString() : "").append("\"");

            Map<String, Object> ansMap = Collections.emptyMap();
            try {
                if (r.getResponsesJson() != null && !r.getResponsesJson().isBlank()) {
                    ansMap = objectMapper.readValue(r.getResponsesJson(), new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception ignored) {}

            for (CustomEventFormField field : fields) {
                Object val = ansMap.get(field.getFieldName());
                String strVal = val != null ? val.toString() : "";
                sw.append(",\"").append(safe(strVal)).append("\"");
            }
            sw.append("\n");
        }

        return sw.toString();
    }

    private String safe(String input) {
        return CsvCellSafety.literal(input).replace("\n", " ").replace("\r", "").replace("\"", "\"\"");
    }

    private static void bounded(String value, int limit) {
        if (value != null && value.length() > limit) throw new IllegalArgumentException("Form value is too long");
    }
}
