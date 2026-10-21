package com.systa.advisors;

import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** For tests of code that calls the LLM: the advisor params it handed its chat client request. */
public final class LlmCallContextCapture {

    private LlmCallContextCapture() {
    }

    /** The params set by the advisors(..) call made on this mocked request spec. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> paramsGivenTo(final ChatClient.ChatClientRequestSpec request) {
        final ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(request).advisors(captor.capture());
        return paramsOf(captor.getValue());
    }

    public static Map<String, Object> paramsOf(final Consumer<ChatClient.AdvisorSpec> context) {
        final Map<String, Object> params = new HashMap<>();
        final ChatClient.AdvisorSpec spec = mock(ChatClient.AdvisorSpec.class);
        doAnswer(invocation -> {
            params.put(invocation.getArgument(0), invocation.getArgument(1));
            return spec;
        }).when(spec).param(anyString(), org.mockito.ArgumentMatchers.any());
        context.accept(spec);
        return params;
    }
}
