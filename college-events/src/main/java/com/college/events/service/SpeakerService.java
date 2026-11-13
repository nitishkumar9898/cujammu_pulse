package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Speaker;
import com.college.events.repository.SpeakerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SpeakerService {

    private final SpeakerRepository speakerRepository;

    public List<Speaker> getSpeakersForEvent(Event event) {
        return speakerRepository.findByEvent(event);
    }

    @Transactional
    public Speaker addSpeaker(Event event, String name, String bio, String linkedInUrl, String profileImageUrl) {
        linkedInUrl = com.college.events.security.WebUrlPolicy.optionalHttpUrl(linkedInUrl);
        profileImageUrl = com.college.events.security.WebUrlPolicy.optionalHttpUrl(profileImageUrl);
        Speaker speaker = Speaker.builder()
            .event(event)
            .name(name)
            .bio(bio)
            .linkedInUrl(linkedInUrl)
            .profileImageUrl(profileImageUrl)
            .build();
        return speakerRepository.save(speaker);
    }

    @Transactional
    public void deleteSpeaker(Long speakerId) {
        speakerRepository.deleteById(speakerId);
    }
}
