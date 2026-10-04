package com.aiworkbench.resume;

import com.aiworkbench.exception.ResumeException;
import com.aiworkbench.service.impl.ResumeServiceImpl;
import com.aiworkbench.storage.MarkdownText;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResumeTextTest {
    @Test void strictGrammarPreservesExactBodyAndOriginalDecodeStripsOnlyLeadingBom() throws Exception {
        String source="\uFEFF# 中文\r\n\t<script>x</script> https://example.test/ \uFEFF";
        assertThat(MarkdownText.decode(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)))).isEqualTo(source.substring(1));
        assertThat(MarkdownText.decodePreservingBom(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)))).isEqualTo(source);
        for(String value:new String[]{"\uD800","\uDC00","a\u0000","a\u0080"})
            assertThatThrownBy(()->ResumeServiceImpl.validateText(value)).isInstanceOf(ResumeException.class);
        assertThatThrownBy(()->MarkdownText.decode(new ByteArrayInputStream(new byte[]{(byte)0xC3,0x28}))).isInstanceOf(IOException.class);
        assertThatThrownBy(()->ResumeServiceImpl.validateText(null)).isInstanceOf(ResumeException.class);
    }
    @Test void emptyAndInclusiveTwentyThousandCodePointsAreValidForBmpAndEmoji() {
        ResumeServiceImpl.validateText("");
        for(String unit:new String[]{"字","😀"}) {
            ResumeServiceImpl.validateText(unit.repeat(20000));
            assertThatThrownBy(()->ResumeServiceImpl.validateText(unit.repeat(20001)))
                    .isInstanceOfSatisfying(ResumeException.class,e->assertThat(e.code()).isEqualTo("RESUME_TEXT_TOO_LONG"));
        }
    }
}
