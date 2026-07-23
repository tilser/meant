package com.meant.api.module.agent.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AgentMutationAdmission {

    public static final String FAILURE_CLASSIFICATION = "admission_timeout";
    public static final String USER_ACTION_SAFE_MESSAGE =
            "The action could not start before its deadline.";
}
