package com.aiworkbench.storage;

import com.aiworkbench.exception.AttachmentException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AttachmentValidator {
    private final Semaphore slots=new Semaphore(2,true);
    public ValidatedAttachment stage(String name,InputStream input) {
        if(!slots.tryAcquire()) throw unavailable();
        Path path=null;
        try {
            String fileName=safeName(name),extension=extension(fileName);
            String mime=switch(extension) {
                case "jpg","jpeg"->"image/jpeg"; case "png"->"image/png"; case "webp"->"image/webp";
                case "pdf"->"application/pdf"; case "md"->"text/markdown; charset=UTF-8";
                default->throw invalid();
            };
            long limit=mime.startsWith("image/")?5_242_880:mime.equals("application/pdf")?20_971_520:1_048_576;
            path=Files.createTempFile("workbench-attachment-",".upload");
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try(var output=Files.newOutputStream(path)) {
                byte[] buffer=new byte[16_384]; int count;
                while((count=input.read(buffer))!=-1) {
                    size+=count;
                    if(size>limit) throw new AttachmentException(HttpStatus.PAYLOAD_TOO_LARGE,"ATTACHMENT_TOO_LARGE","文件超过该类型的大小上限");
                    output.write(buffer,0,count); digest.update(buffer,0,count);
                }
            }
            if(size==0 && !extension.equals("md")) throw invalid();
            validateInProcess(path,extension);
            var file=new ValidatedAttachment(path,fileName,mime,size,HexFormat.of().formatHex(digest.digest()));
            path=null; return file;
        } catch(AttachmentException failure) { throw failure; }
        catch(Exception failure) { throw unavailable(); }
        finally {
            if(path!=null) try { Files.deleteIfExists(path); } catch(IOException ignored) { }
            slots.release();
        }
    }
    /** A real process budget bounds parsers that can allocate before applying subsampling. */
    private void validateInProcess(Path path,String extension) throws IOException,InterruptedException {
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        List<String> command=new ArrayList<>(List.of(executable,"-Xmx128m","-XX:MaxDirectMemorySize=16m","-XX:MaxMetaspaceSize=96m","-Dfile.encoding=UTF-8"));
        Path scratch=Files.createTempDirectory("workbench-attachment-parser-");
        command.add("-Djava.io.tmpdir="+scratch.toAbsolutePath());
        boolean packaged=classpath.endsWith(".jar") && !classpath.contains(File.pathSeparator);
        if(packaged) command.add("-Dloader.main="+AttachmentValidationWorker.class.getName());
        command.addAll(List.of("-cp",classpath,packaged?"org.springframework.boot.loader.launch.PropertiesLauncher":AttachmentValidationWorker.class.getName(),path.toString(),extension));
        ProcessBuilder builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(key->key.matches("(?i)^(DEEPSEEK_|POSTGRES_|REDIS_|REPORT_AI_|WORKBENCH_STORAGE_|RUSTFS_|AWS_|WORKBENCH_BOOTSTRAP_|DATABASE_URL|TEST_DATABASE_URL|LIVE_ACCEPTANCE_DATABASE_URL).*$"));
        Process process;
        try { process=builder.start(); }
        catch(IOException failure) { Files.deleteIfExists(scratch); throw failure; }
        try {
            if(!process.waitFor(60,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if(!process.waitFor(10,TimeUnit.SECONDS)) throw unavailable();
                throw unavailable();
            }
            if(process.exitValue()==0) return;
            if(process.exitValue()==2) throw invalid();
            throw unavailable();
        } finally {
            if(process.isAlive()) { process.destroyForcibly(); process.waitFor(10,TimeUnit.SECONDS); }
            process.getInputStream().close();
            if(!process.isAlive()) {
                // The path is a new application-owned directory, never a caller-selected filename.
                // Files.walk does not follow links; only entries under this exact root are removed.
                try(var paths=Files.walk(scratch)) {
                    for(Path item:paths.sorted(Comparator.reverseOrder()).toList()) {
                        if(!item.toAbsolutePath().normalize().startsWith(scratch.toAbsolutePath().normalize())) throw new IOException();
                        Files.deleteIfExists(item);
                    }
                }
            }
        }
    }
    static String safeName(String name) {
        if(name==null) throw invalid();
        name=name.replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1);
        StringBuilder safe=new StringBuilder();
        name.codePoints().filter(c->!Character.isISOControl(c) && c!=0x2028 && c!=0x2029).forEach(safe::appendCodePoint);
        String value=safe.toString().trim();
        if(value.isBlank() || value.length()>255) throw invalid();
        return value;
    }
    private String extension(String name) { int dot=name.lastIndexOf('.'); return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT); }
    static AttachmentException invalid() { return new AttachmentException(HttpStatus.BAD_REQUEST,"ATTACHMENT_FORMAT_INVALID","文件扩展名、实际格式或文本编码无效"); }
    static AttachmentException unavailable() { return new AttachmentException(HttpStatus.SERVICE_UNAVAILABLE,"ATTACHMENT_VALIDATION_UNAVAILABLE","文件验证暂不可用，请重试"); }
}
