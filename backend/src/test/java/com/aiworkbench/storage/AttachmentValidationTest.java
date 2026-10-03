package com.aiworkbench.storage;

import com.aiworkbench.exception.AttachmentException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class AttachmentValidationTest {
    @TempDir Path dir;
    final AttachmentValidator validator=new AttachmentValidator();
    byte[] image(String format) throws IOException {
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3,3,format.equals("jpg")?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_INT_ARGB),format,output); return output.toByteArray();
    }
    byte[] pdf(String password) throws IOException {
        var path=dir.resolve(UUID.randomUUID()+".pdf");
        try(var document=new PDDocument()) {
            document.addPage(new PDPage());
            if(password!=null) { var policy=new StandardProtectionPolicy("owner-secret",password,new AccessPermission()); policy.setEncryptionKeyLength(256); document.protect(policy); }
            document.save(path.toFile());
        }
        return Files.readAllBytes(path);
    }
    @Test void strictPdfApiValidatesActualSavedDocument() throws Exception {
        Path path=dir.resolve("strict.pdf"); Files.write(path,pdf(null)); PdfStructureValidator.validate(path);
    }
    @Test void actualFormatsFilenameAndOriginalBytesArePreservedAndTempFilesRemoved() throws Exception {
        for(String type:List.of("png","jpg","pdf","md")) {
            byte[] bytes=type.equals("pdf")?pdf(null):type.equals("md")?"\ufeff# 中文\r\nbody\ttext".getBytes(StandardCharsets.UTF_8):image(type);
            Path path;
            try(var file=validator.stage("C:\\danger\\safe\r\n."+type,new ByteArrayInputStream(bytes))) {
                path=file.path(); assertThat(file.fileName()).isEqualTo("safe."+type); assertThat(Files.readAllBytes(path)).isEqualTo(bytes);
                assertThat(file.size()).isEqualTo(bytes.length);
            }
            assertThat(path).doesNotExist();
        }
    }
    @Test void nonemptyAndEmptyPasswordEncryptedPdfsAreAcceptedWithoutReadingContent() throws Exception {
        for(String password:List.of("secret-document-password","")) try(var ignored=validator.stage("encrypted.pdf",new ByteArrayInputStream(pdf(password)))) { }
    }
    @Test void disguiseTruncationInvalidUtf8AndBinaryControlsAreRejected() throws Exception {
        for(byte[] bytes:List.of("%PDF-1.7\nnot a PDF".getBytes(StandardCharsets.UTF_8),Arrays.copyOf(pdf(null),30))) reject("bad.pdf",bytes);
        reject("bad.png",image("jpg")); reject("bad.jpg",Arrays.copyOf(image("jpg"),20));
        reject("bad.md",new byte[]{(byte)0xc3,0x28}); reject("bad.md",new byte[]{65,0,66});
        reject("bad.md",new byte[]{1,2,3}); reject("bad.svg",image("png"));
        reject("bad.webp","RIFF\u0004\u0000\u0000\u0000WEBP".getBytes(StandardCharsets.US_ASCII));
    }
    @Test void markdownExactLimitAllowedAndPlusOneRejectedByActualStreamBytes() throws Exception {
        try(var file=validator.stage("empty.md",new ByteArrayInputStream(new byte[0]))) { assertThat(file.size()).isZero(); }
        byte[] bytes=new byte[1_048_576]; Arrays.fill(bytes,(byte)'a');
        try(var file=validator.stage("exact.md",new ByteArrayInputStream(bytes))) { assertThat(file.size()).isEqualTo(bytes.length); }
        assertThatThrownBy(()->validator.stage("over.md",new ByteArrayInputStream(Arrays.copyOf(bytes,bytes.length+1))))
                .isInstanceOfSatisfying(AttachmentException.class,error->{ assertThat(error.getStatusCode().value()).isEqualTo(413); });
    }
    @Test void staticAnimatedAndHighCompressionWebpAreAcceptedAndTruncatedFramesRejected() throws Exception {
        for(String fixture:List.of("static.webp","animated.webp","high-compression.webp")) {
            byte[] bytes;
            try(var input=getClass().getResourceAsStream("/storage/"+fixture)) { bytes=input.readAllBytes(); }
            try(var file=validator.stage(fixture,new ByteArrayInputStream(bytes))) { assertThat(file.contentType()).isEqualTo("image/webp"); }
            reject("broken.webp",Arrays.copyOf(bytes,bytes.length-1));
        }
    }
    @Test void actualPublicKeyEncryptedPdfIsAcceptedWithoutCertificateOrPrivateKey() throws Exception {
        byte[] bytes;
        try(var input=getClass().getResourceAsStream("/storage/public-key-encrypted.pdf")) { bytes=input.readAllBytes(); }
        try(var file=validator.stage("public-key.pdf",new ByteArrayInputStream(bytes))) { assertThat(file.contentType()).isEqualTo("application/pdf"); }
        reject("truncated.pdf",Arrays.copyOf(bytes,bytes.length-20));
    }
    private void reject(String name,byte[] bytes) {
        assertThatThrownBy(()->validator.stage(name,new ByteArrayInputStream(bytes))).isInstanceOfSatisfying(AttachmentException.class,
                error->assertThat(error.code()).isEqualTo("ATTACHMENT_FORMAT_INVALID"));
    }
}
