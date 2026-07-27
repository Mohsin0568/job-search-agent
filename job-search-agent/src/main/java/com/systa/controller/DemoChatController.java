package com.systa.controller;

import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
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
    public String chat(@RequestParam("message") final String message){
        return chatClient
                .prompt(message)
                .call()
                .content();
    }
}
