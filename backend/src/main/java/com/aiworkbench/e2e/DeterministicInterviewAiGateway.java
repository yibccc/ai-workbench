package com.aiworkbench.e2e;

import com.aiworkbench.ai.InterviewAiGateway;
import com.aiworkbench.dto.interview.InterviewModels.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicit isolated-profile fixture; never used by a live acceptance/ordinary application. */
@Component
@Profile("(test | e2e) & !live-acceptance")
public class DeterministicInterviewAiGateway implements InterviewAiGateway {
    @Override public JdResult parseJd(JdInput input) { return new JdResult(true,"隔离fixture匹配所选方向",List.of("正确性","边界和验证")); }
    @Override public QuestionSet generate(QuestionInput input) {
        List<Question> questions=new ArrayList<>();
        for(int i=0;i<input.mainQuestionCount();i++) {
            questions.add(new Question(i*2,"MAIN",null,input.direction()+" "+input.difficulty()+" 主问"+(i+1)+"：说明设计与验证。"));
            questions.add(new Question(i*2+1,"FOLLOW_UP",i*2,"追问"+(i+1)+"：说明失败边界与取舍。"));
        }
        return new QuestionSet(questions);
    }
    @Override public GroupScore evaluate(GroupInput input) {
        return new GroupScore(input.answers().stream().filter(a->a.status().equals("SUBMITTED"))
                .map(a->new Score(a.turnIndex(),new BigDecimal(a.answerText().isEmpty()?"0":"80.125"),"隔离fixture：检查说明是否覆盖正确性和失败边界。",List.of("描述输入输出","解释取舍","提供验证"))).toList());
    }
}
