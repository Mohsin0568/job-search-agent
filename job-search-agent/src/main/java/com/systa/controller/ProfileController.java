package com.systa.controller;

import com.systa.model.CandidateProfileDto;
import com.systa.service.CandidateProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final CandidateProfileService candidateProfileService;

    public ProfileController(final CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    // 404 tells the UI this is a first-time user who still needs to set up their profile.
    @GetMapping
    public ResponseEntity<CandidateProfileDto> getProfile(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.of(candidateProfileService.findByUserId(jwt.getSubject())
                .map(CandidateProfileDto::from));
    }

    @PutMapping
    public CandidateProfileDto saveProfile(@AuthenticationPrincipal final Jwt jwt,
                                           @Valid @RequestBody final CandidateProfileDto details) {
        return CandidateProfileDto.from(candidateProfileService.save(jwt.getSubject(), jwt.getTokenValue(), details));
    }
}
