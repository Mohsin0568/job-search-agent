package com.systa.controller;

import com.systa.advisors.LlmCallContext;
import com.systa.model.LlmUsage.Operation;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DemoChatController {

    private final ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@AuthenticationPrincipal final Jwt jwt, @RequestParam("message") final String message){
        return chatClient
                .prompt(message)
                .advisors(LlmCallContext.of(jwt != null ? jwt.getSubject() : null, Operation.DEMO_CHAT))
                .call()
                .content();
    }
}
