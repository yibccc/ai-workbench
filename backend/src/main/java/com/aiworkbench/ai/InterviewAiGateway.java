package com.aiworkbench.ai;

import com.aiworkbench.dto.interview.InterviewModels.*;

public interface InterviewAiGateway {
    JdResult parseJd(JdInput input);
    QuestionSet generate(QuestionInput input);
    GroupScore evaluate(GroupInput input);
}
