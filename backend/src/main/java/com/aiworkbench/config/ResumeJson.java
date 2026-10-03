package com.aiworkbench.config;

import com.aiworkbench.dto.resume.ResumeModels.Mode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Exact scalars for the new resume request; no global Jackson coercion changes. */
public final class ResumeJson {
    private ResumeJson() {}
    public static class ExactMode extends JsonDeserializer<Mode> {
        @Override public Mode deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_STRING)) return context.reportInputMismatch(Mode.class,"Expected a resume mode name");
            try { return Mode.valueOf(parser.getText()); }
            catch(IllegalArgumentException invalid) { return context.reportInputMismatch(Mode.class,"Unknown resume mode"); }
        }
    }
    public static class ExactVersion extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser,DeserializationContext context) throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) return context.reportInputMismatch(Long.class,"Expected an integer version");
            return parser.getLongValue();
        }
    }
}
