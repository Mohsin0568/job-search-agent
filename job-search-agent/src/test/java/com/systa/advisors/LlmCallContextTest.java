package com.systa.advisors;

import com.systa.model.LlmUsage.Operation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.systa.advisors.LlmCallContextCapture.paramsOf;
import static org.assertj.core.api.Assertions.assertThat;

class LlmCallContextTest {

    @Test
    void setsTheUserOperationRunAndCompany_andARequestId() {
        final Map<String, Object> params = paramsOf(LlmCallContext.of("user-1", Operation.JOB_SEARCH, "run-1",
                "Acme Corp"));

        assertThat(params)
                .containsEntry(LlmCallContext.USER_ID, "user-1")
                .containsEntry(LlmCallContext.OPERATION, Operation.JOB_SEARCH)
                .containsEntry(LlmCallContext.RUN_ID, "run-1")
                .containsEntry(LlmCallContext.COMPANY_NAME, "Acme Corp");
        assertThat(params.get(LlmCallContext.REQUEST_ID)).asString().isNotBlank();
    }

    @Test
    void leavesOutWhatIsMissing_sinceAdvisorParamsCannotHoldNull() {
        final Map<String, Object> params = paramsOf(LlmCallContext.of(null, Operation.DEMO_CHAT));

        assertThat(params).containsOnlyKeys(LlmCallContext.OPERATION, LlmCallContext.REQUEST_ID);
    }

    @Test
    void givesEachUseANewRequestId() {
        final var context = LlmCallContext.of("user-1", Operation.RESUME_REVIEW);

        assertThat(paramsOf(context).get(LlmCallContext.REQUEST_ID))
                .isNotEqualTo(paramsOf(context).get(LlmCallContext.REQUEST_ID));
    }
}
