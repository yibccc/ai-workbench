package com.aiworkbench.interview;

import com.aiworkbench.ai.*;
import com.aiworkbench.dto.interview.InterviewModels.*;
import com.aiworkbench.e2e.DeterministicInterviewAiGateway;
import com.aiworkbench.service.impl.InterviewServiceImpl;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InterviewDomainTest {
    @Test void allFourDirectionsThreeDifficultiesAndEveryNHaveExactlyTwoNStableTurns() {
        var gateway=new DeterministicInterviewAiGateway();
        for(var direction:Direction.values()) for(var difficulty:Difficulty.values()) for(int n=3;n<=20;n++) {
            var result=gateway.generate(new QuestionInput(direction,difficulty,n,null,null,null,"q1","r1","m1"));
            InterviewOutput.questions(result,n); assertThat(result.questions()).hasSize(n*2);
        }
    }
    @Test void missingExtraWrongParentAndEmptyFollowUpAreRejectedAsWholeSet() {
        var gateway=new DeterministicInterviewAiGateway(); var good=gateway.generate(new QuestionInput(Direction.JAVA_BACKEND,Difficulty.MID,3,null,null,null,"q1","r1","m1"));
        for(int kind=0;kind<4;kind++) {
            var questions=new ArrayList<>(good.questions());
            if(kind==0) questions.remove(1); else if(kind==1) questions.add(good.questions().get(0));
            else questions.set(1,new Question(1,"FOLLOW_UP",kind==2?2:0,kind==3?"":"follow"));
            assertThatThrownBy(()->InterviewOutput.questions(new QuestionSet(questions),3)).isInstanceOf(InterviewModelException.class);
        }
    }
    @Test void jdAndAnswerUnicodeInclusiveBoundsEmptySubmissionAndInvalidSurrogate() {
        for(String unit:List.of("字","😀")) {
            InterviewServiceImpl.text(unit.repeat(10000),10000); InterviewServiceImpl.text(unit.repeat(5000),5000);
            assertThatThrownBy(()->InterviewServiceImpl.text(unit.repeat(10001),10000)).isInstanceOf(com.aiworkbench.exception.InterviewException.class);
            assertThatThrownBy(()->InterviewServiceImpl.text(unit.repeat(5001),5000)).isInstanceOf(com.aiworkbench.exception.InterviewException.class);
        }
        InterviewServiceImpl.text("",5000); assertThatThrownBy(()->InterviewServiceImpl.text("\uD800",5000)).isInstanceOf(com.aiworkbench.exception.InterviewException.class);
    }
    @Test void submittedEmptyStillNeedsScoreAndMissingWrongNonFiniteOrRangeScoreCannotPass() {
        var input=new GroupInput(Direction.REACT_FRONTEND,Difficulty.JUNIOR,null,null,null,List.of(),List.of(new Answer(0,"SUBMITTED","",1),new Answer(1,"UNANSWERED","",1)),"r1","m1");
        InterviewOutput.scores(new GroupScore(List.of(new Score(0,BigDecimal.ZERO,"空提交反馈",List.of()))),input);
        for(var invalid:List.of(new GroupScore(List.of()),new GroupScore(List.of(new Score(1,BigDecimal.ZERO,"wrong",List.of()))),new GroupScore(List.of(new Score(0,new BigDecimal("100.01"),"range",List.of())))))
            assertThatThrownBy(()->InterviewOutput.scores(invalid,input)).isInstanceOf(InterviewModelException.class);
    }
}
