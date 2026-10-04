package com.aiworkbench.config;

import com.aiworkbench.dto.interview.InterviewModels.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** New interview request scalars are exact; existing APIs keep their own contracts. */
public final class InterviewJson {
    private InterviewJson() {}
    public static class ExactText extends JsonDeserializer<String> {
        @Override public String deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING)) return context.reportInputMismatch(String.class,"Expected text"); return parser.getText();
        }
    }
    public static class ExactInteger extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Integer.class,"Expected an integer"); return parser.getIntValue();
        }
    }
    public static class ExactLong extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Long.class,"Expected an integer version"); return parser.getLongValue();
        }
    }
    public static class ExactBoolean extends JsonDeserializer<Boolean> {
        @Override public Boolean deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_TRUE) && !parser.hasToken(JsonToken.VALUE_FALSE)) return context.reportInputMismatch(Boolean.class,"Expected a boolean"); return parser.getBooleanValue();
        }
    }
    public static class ExactDirection extends JsonDeserializer<Direction> {
        @Override public Direction deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING)) return context.reportInputMismatch(Direction.class,"Expected a direction name");
            try { return Direction.valueOf(parser.getText()); } catch(IllegalArgumentException invalid) { return context.reportInputMismatch(Direction.class,"Unknown direction"); }
        }
    }
    public static class ExactDifficulty extends JsonDeserializer<Difficulty> {
        @Override public Difficulty deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING)) return context.reportInputMismatch(Difficulty.class,"Expected a difficulty name");
            try { return Difficulty.valueOf(parser.getText()); } catch(IllegalArgumentException invalid) { return context.reportInputMismatch(Difficulty.class,"Unknown difficulty"); }
        }
    }
}
