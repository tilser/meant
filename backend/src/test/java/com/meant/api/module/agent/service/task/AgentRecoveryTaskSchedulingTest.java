package com.meant.api.module.agent.service.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AgentRecoveryTaskSchedulingTest {

    @Test
    void periodicMaintenanceWaitsAfterTheExplicitStartupRecovery() throws NoSuchMethodException {
        Scheduled schedule = schedule(AgentMaintenanceTask.class, "recoverStaleRuns");

        assertThat(schedule.fixedDelayString()).isEqualTo("${commerce.agent.stale-run-age}");
        assertThat(schedule.initialDelayString()).isEqualTo("${commerce.agent.stale-run-age}");
    }

    @Test
    void periodicMutationRecoveryWaitsAfterTheExplicitStartupRecovery() throws NoSuchMethodException {
        Scheduled schedule = schedule(AgentMutationRecoveryTask.class, "recoverStaleExecutions");

        assertThat(schedule.fixedDelayString()).isEqualTo("${commerce.agent.tool-deadline}");
        assertThat(schedule.initialDelayString()).isEqualTo("${commerce.agent.tool-deadline}");
    }

    private Scheduled schedule(Class<?> taskType, String methodName) throws NoSuchMethodException {
        Method method = taskType.getDeclaredMethod(methodName);
        return method.getAnnotation(Scheduled.class);
    }
}
