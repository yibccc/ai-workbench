package com.aiworkbench.ai;

import com.aiworkbench.dto.interview.InterviewModels.*;
import java.math.BigDecimal;
import java.util.*;

/** Deterministic complete validation is shared by real and test gateways. */
public final class InterviewOutput {
    private InterviewOutput() {}
    public static void jd(JdResult result) {
        if(result==null || result.summary()==null || result.summary().isBlank() || result.focusPoints()==null
                || result.focusPoints().stream().anyMatch(s->s==null || s.isBlank())) throw invalid();
        if(!result.matched()) throw new InterviewModelException("JD_DIRECTION_MISMATCH");
    }
    public static void questions(QuestionSet result,int count) {
        if(result==null || result.questions()==null || result.questions().size()!=count*2) throw invalid();
        for(int i=0;i<count*2;i++) {
            var question=result.questions().get(i);
            if(question==null || question.turnIndex()!=i || question.text()==null || question.text().isBlank()
                    || !(i%2==0?"MAIN".equals(question.type()) && question.parentMainIndex()==null
                        :"FOLLOW_UP".equals(question.type()) && Objects.equals(question.parentMainIndex(),i-1))) throw invalid();
        }
    }
    public static void scores(GroupScore result,GroupInput input) {
        Set<Integer> required=new HashSet<>(); input.answers().stream().filter(a->a.status().equals("SUBMITTED")).forEach(a->required.add(a.turnIndex()));
        if(result==null || result.turns()==null || result.turns().size()!=required.size()) throw invalid();
        Set<Integer> found=new HashSet<>();
        for(var score:result.turns()) if(score==null || !required.contains(score.turnIndex()) || !found.add(score.turnIndex())
                || score.score()==null || score.score().compareTo(BigDecimal.ZERO)<0 || score.score().compareTo(BigDecimal.valueOf(100))>0
                || score.feedback()==null || score.feedback().isBlank() || score.referencePoints()==null
                || score.referencePoints().stream().anyMatch(s->s==null || s.isBlank())) throw invalid();
    }
    public static InterviewModelException invalid() { return new InterviewModelException("MODEL_OUTPUT_INVALID"); }
}
