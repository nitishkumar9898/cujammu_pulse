package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Sponsor;
import com.college.events.repository.SponsorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SponsorService {

    private final SponsorRepository sponsorRepository;

    public List<Sponsor> getSponsorsForEvent(Event event) {
        return sponsorRepository.findByEvent(event);
    }

    @Transactional
    public Sponsor addSponsor(Event event, String companyName, String logoUrl, String websiteUrl, String tier) {
        logoUrl = com.college.events.security.WebUrlPolicy.optionalHttpUrl(logoUrl);
        websiteUrl = com.college.events.security.WebUrlPolicy.optionalHttpUrl(websiteUrl);
        Sponsor sponsor = Sponsor.builder()
            .event(event)
            .companyName(companyName)
            .logoUrl(logoUrl)
            .websiteUrl(websiteUrl)
            .sponsorshipTier(tier)
            .build();
        return sponsorRepository.save(sponsor);
    }

    @Transactional
    public void deleteSponsor(Long sponsorId) {
        sponsorRepository.deleteById(sponsorId);
    }
}
