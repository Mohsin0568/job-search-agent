package com.systa.advisors;

import com.systa.model.LlmUsage;
import com.systa.model.LlmUsage.Operation;
import com.systa.model.LlmUsage.Status;
import com.systa.repository.LlmUsageRepository;
import com.systa.service.LlmUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmUsageAdvisorTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    private final ChatModel chatModel = mock(ChatModel.class);
    private final LlmUsageService llmUsageService = mock(LlmUsageService.class);
    private final AtomicInteger ids = new AtomicInteger();

    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        final LlmUsageAdvisor advisor = new LlmUsageAdvisor(llmUsageService, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "usage-" + ids.incrementAndGet());
        chatClient = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();
    }

    @Test
    void recordsTheTokensTheModelReported_forTheUserAndOperationTheCallerGave() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Hello", 120, 30, 150, 40L));

        final String content = chatClient.prompt("Hi")
                .advisors(LlmCallContext.of("user-1", Operation.JOB_SEARCH, "run-1", "Acme Corp"))
                .call()
                .content();

        assertThat(content).isEqualTo("Hello");
        final LlmUsage usage = recorded(1).getFirst();
        assertThat(usage.requestId()).isNotBlank();
        assertThat(usage).usingRecursiveComparison().ignoringFields("requestId").isEqualTo(new LlmUsage("usage-1",
                null, "user-1", Operation.JOB_SEARCH, "gpt-4.1", 120, 30, 150, 40, "run-1", "Acme Corp",
                Status.SUCCEEDED, null, NOW, 0));
    }

    @Test
    void recordsEachRoundTripOfACallThatRunsATool_underOneRequestId() {
        final ToolCallback lookup = FunctionToolCallback.builder("lookup", (String query) -> "found it")
                .description("Looks something up")
                .inputType(String.class)
                .build();
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCall("lookup", 500, 20, 520))
                .thenReturn(answer("Done", 700, 50, 750, null));

        final String content = chatClient.prompt("Find it")
                .advisors(LlmCallContext.of("user-1", Operation.JOB_POSTING))
                .toolCallbacks(lookup)
                .call()
                .content();

        assertThat(content).isEqualTo("Done");
        final List<LlmUsage> usages = recorded(2);
        assertThat(usages).extracting(LlmUsage::totalTokens).containsExactly(520, 750);
        assertThat(usages).extracting(LlmUsage::userId).containsOnly("user-1");
        assertThat(usages.get(0).requestId()).isNotNull().isEqualTo(usages.get(1).requestId());
    }

    @Test
    void givesEachCallItsOwnRequestId() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Hello", 10, 5, 15, null));

        chatClient.prompt("Hi").advisors(LlmCallContext.of("user-1", Operation.RESUME_REVIEW)).call().content();
        chatClient.prompt("Hi").advisors(LlmCallContext.of("user-1", Operation.RESUME_REVIEW)).call().content();

        final List<LlmUsage> usages = recorded(2);
        assertThat(usages.get(0).requestId()).isNotEqualTo(usages.get(1).requestId());
    }

    @Test
    void recordsAFailedCallWithNoTokens_andStillThrows() {
        final RuntimeException rateLimited = new RuntimeException("429 Too Many Requests");
        when(chatModel.call(any(Prompt.class))).thenThrow(rateLimited);

        assertThatThrownBy(() -> chatClient.prompt("Hi")
                .advisors(LlmCallContext.of("user-1", Operation.JOB_SEARCH, "run-1", "Acme Corp"))
                .call()
                .content())
                .isSameAs(rateLimited);

        final LlmUsage usage = recorded(1).getFirst();
        assertThat(usage.status()).isEqualTo(Status.FAILED);
        assertThat(usage.failureReason()).contains("429 Too Many Requests");
        assertThat(usage.totalTokens()).isZero();
        assertThat(usage.model()).isNull();
        assertThat(usage.userId()).isEqualTo("user-1");
    }

    @Test
    void cutsALongFailureReasonDown() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("x".repeat(2_000)));

        assertThatThrownBy(() -> chatClient.prompt("Hi").call().content()).isInstanceOf(RuntimeException.class);

        assertThat(recorded(1).getFirst().failureReason()).hasSize(500);
    }

    @Test
    void recordsACallMadeWithoutAContext_asUnknownAndForNoUser() {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Hello", 10, 5, 15, null));

        chatClient.prompt("Hi").call().content();

        final LlmUsage usage = recorded(1).getFirst();
        assertThat(usage.operation()).isEqualTo(Operation.UNKNOWN);
        assertThat(usage.userId()).isNull();
        assertThat(usage.requestId()).isNull();
        assertThat(usage.totalTokens()).isEqualTo(15);
    }

    @Test
    void recordsZeroTokens_whenTheModelReportsNoUsage() {
        when(chatModel.call(any(Prompt.class))).thenReturn(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("Hello"))))
                .build());

        chatClient.prompt("Hi").advisors(LlmCallContext.of("user-1", Operation.DEMO_CHAT)).call().content();

        final LlmUsage usage = recorded(1).getFirst();
        assertThat(usage.totalTokens()).isZero();
        assertThat(usage.status()).isEqualTo(Status.SUCCEEDED);
    }

    @Test
    void returnsTheAnswer_whenSavingTheUsageFails() {
        final LlmUsageRepository repository = mock(LlmUsageRepository.class);
        doThrow(new IllegalStateException("Mongo is down")).when(repository).save(any());
        final ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LlmUsageAdvisor(new LlmUsageService(repository), Clock.fixed(NOW, ZoneOffset.UTC),
                        () -> "id"))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Hello", 10, 5, 15, null));

        assertThat(client.prompt("Hi").call().content()).isEqualTo("Hello");
    }

    @Test
    void timesTheCall() {
        final Clock ticking = mock(Clock.class);
        when(ticking.instant()).thenReturn(NOW, NOW.plus(Duration.ofMillis(1_250)));
        final ChatClient client = ChatClient.builder(chatModel)
                .defaultAdvisors(new LlmUsageAdvisor(llmUsageService, ticking, () -> "id"))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Hello", 10, 5, 15, null));

        client.prompt("Hi").call().content();

        final LlmUsage usage = recorded(1).getFirst();
        assertThat(usage.calledAt()).isEqualTo(NOW);
        assertThat(usage.durationMs()).isEqualTo(1_250);
    }

    private List<LlmUsage> recorded(final int times) {
        final ArgumentCaptor<LlmUsage> captor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageService, times(times)).record(captor.capture());
        return captor.getAllValues();
    }

    private static ChatResponse answer(final String text, final int prompt, final int completion, final int total,
                                       final Long cached) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(text))))
                .metadata(metadata(prompt, completion, total, cached))
                .build();
    }

    private static ChatResponse toolCall(final String tool, final int prompt, final int completion, final int total) {
        final AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", tool, "\"anything\"")))
                .build();
        return ChatResponse.builder()
                .generations(List.of(new Generation(message)))
                .metadata(metadata(prompt, completion, total, null))
                .build();
    }

    private static ChatResponseMetadata metadata(final int prompt, final int completion, final int total,
                                                 final Long cached) {
        return ChatResponseMetadata.builder()
                .model("gpt-4.1")
                .usage(new DefaultUsage(prompt, completion, total, null, cached, null))
                .build();
    }
}
