package com.aiworkbench.resume;

import com.aiworkbench.service.AccountService;
import com.aiworkbench.storage.ObjectStorage;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual Servlet multipart, sessions/CSRF, Redis, PG and private RustFS streams. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResumeHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;
    Client a,b,admin;
    record Client(HttpClient http,String token,UUID id) {}
    @BeforeEach void setup() throws Exception { a=account("USER"); b=account("USER"); admin=account("ADMIN"); }
    @AfterEach void cleanup() {
        for(Client client:List.of(a,b,admin)) {
            for(String key:jdbc.queryForList("SELECT storage_key FROM resume_objects WHERE user_id=?",String.class,client.id())) storage.delete(key);
            jdbc.update("DELETE FROM resume_write_receipts WHERE user_id=?",client.id());
            jdbc.update("DELETE FROM user_resumes WHERE user_id=?",client.id());
            jdbc.update("DELETE FROM resume_objects WHERE user_id=?",client.id());
            jdbc.update("DELETE FROM user_accounts WHERE id=?",client.id());
        }
    }
    Client account(String role) throws Exception {
        var account=accounts.create("resume-http-"+UUID.randomUUID(),"Resume HTTP strong password 123",role);
        var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        Client client=new Client(http,null,account.id()); String token=node(send(client,"GET","/api/auth/csrf",null)).path("token").asText();
        client=new Client(http,token,account.id());
        assertThat(send(client,"POST","/api/auth/login",Map.of("username",account.username(),"password","Resume HTTP strong password 123")).statusCode()).isEqualTo(200);
        return client;
    }
    @Test void privateCurrentGetHeadAndConditionalsNeverExposeOtherAccountOriginal() throws Exception {
        byte[] original="\uFEFF# 私有原件\r\nbody".getBytes(StandardCharsets.UTF_8);
        var uploaded=upload(a,0,UUID.randomUUID(),"中文原件.md",original,"edited text",List.of());
        assertThat(uploaded.statusCode()).isEqualTo(200); assertThat(node(uploaded).path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(send(a,"GET","/api/me/resume",null).body()).contains("edited text","MD_FILE").doesNotContain("storageKey","bucket","endpoint","interview/resumes/","http://");
        for(Client foreign:List.of(b,admin)) {
            var other=send(foreign,"GET","/api/me/resume",null);
            assertThat(node(other).path("exists").asBoolean()).isFalse(); assertThat(other.body()).doesNotContain("edited text","中文原件");
            for(String method:List.of("GET","HEAD")) {
                var denied=bytes(foreign,method,"/api/me/resume/original",Map.of("Range","bytes=0-0","If-None-Match","*"));
                assertThat(denied.statusCode()).isEqualTo(404); privateHeaders(denied);
                assertThat(denied.headers().firstValue("Content-Disposition")).isEmpty();
                assertThat(new String(denied.body(),StandardCharsets.UTF_8)).doesNotContain("私有原件","中文原件");
            }
        }
        Client anonymous=new Client(HttpClient.newHttpClient(),null,null);
        for(String path:List.of("/api/me/resume","/api/me/resume/original")) {
            var denied=bytes(anonymous,"GET",path,Map.of()); assertThat(denied.statusCode()).isEqualTo(401); privateHeaders(denied);
        }
        var own=bytes(a,"GET","/api/me/resume/original",Map.of("Range","bytes=0-0","If-None-Match","*","If-Modified-Since","Fri, 01 Jan 2100 00:00:00 GMT"));
        assertThat(own.statusCode()).isEqualTo(200); assertThat(own.body()).isEqualTo(original); privateHeaders(own);
        assertThat(own.headers().firstValue("Accept-Ranges").orElse("")).isEqualTo("none");
        assertThat(own.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment;").contains("UTF-8");
        assertThat(own.headers().firstValue("Location")).isEmpty(); assertThat(own.headers().firstValue("ETag")).isEmpty();
        var head=bytes(a,"HEAD","/api/me/resume/original",Map.of()); assertThat(head.statusCode()).isEqualTo(200); assertThat(head.body()).isEmpty();
        assertThat(head.headers().firstValueAsLong("Content-Length").orElse(-1)).isEqualTo(original.length);
    }
    @Test void csrfMandatoryModeExactMultipartFieldsAndInvalidTextCannotOverwriteSavedBody() throws Exception {
        UUID id=UUID.randomUUID();
        var created=send(a,"PUT","/api/me/resume",Map.of("mode","PASTE","markdownText","old","expectedVersion",0,"requestId",id));
        assertThat(created.statusCode()).isEqualTo(200);
        var noCsrf=new Client(a.http(),null,a.id());
        problem(upload(noCsrf,1,UUID.randomUUID(),"valid.md","safe".getBytes(),"safe",List.of()),403);
        problem(send(noCsrf,"PUT","/api/me/resume",Map.of("mode","PASTE","markdownText","bad","expectedVersion",1,"requestId",UUID.randomUUID())),403);
        problem(send(a,"PUT","/api/me/resume",Map.of("markdownText","bad","expectedVersion",1,"requestId",UUID.randomUUID())),400);
        problem(send(a,"PUT","/api/me/resume",Map.of("mode","UNKNOWN","markdownText","bad","expectedVersion",1,"requestId",UUID.randomUUID())),400);
        problem(upload(a,1,UUID.randomUUID(),"bad.pdf","safe".getBytes(),"safe",List.of()),400);
        problem(upload(a,1,UUID.randomUUID(),"valid.md","safe".getBytes(),"safe",List.of("unexpected")),400);
        problem(upload(a,1,UUID.randomUUID(),"valid.md","safe".getBytes(),"safe",List.of("markdownText")),400);
        var raw=HttpRequest.newBuilder(uri("/api/me/resume")).header("Content-Type","application/json").header("X-XSRF-TOKEN",a.token())
                .PUT(HttpRequest.BodyPublishers.ofString("{\"mode\":\"PASTE\",\"markdownText\":\"\\uD800\",\"expectedVersion\":1,\"requestId\":\""+UUID.randomUUID()+"\"}")).build();
        problem(a.http().send(raw,HttpResponse.BodyHandlers.ofString()),400);
        problem(send(a,"DELETE","/api/me/resume",null),400);
        assertThat(node(send(a,"GET","/api/me/resume",null)).path("markdownText").asText()).isEqualTo("old");
        assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,a.id())).isEqualTo(1);
    }
    @Test void emptyMdAndEmojiTransportCurrentEditPasteReplayAndDeleteUseAuthoritativeGet() throws Exception {
        UUID id=UUID.randomUUID(); var first=upload(a,0,id,"empty.md",new byte[0],"",List.of());
        assertThat(first.statusCode()).isEqualTo(200); assertThat(node(send(a,"GET","/api/me/resume",null)).path("exists").asBoolean()).isTrue();
        String body="😀".repeat(20000);
        assertThat(send(a,"PUT","/api/me/resume",Map.of("mode","EDIT_CURRENT","markdownText",body,"expectedVersion",1,"requestId",UUID.randomUUID())).statusCode()).isEqualTo(200);
        assertThat(node(send(a,"GET","/api/me/resume",null)).path("sourceKind").asText()).isEqualTo("MD_FILE");
        var replay=upload(a,0,id,"empty.md",new byte[0],"",List.of()); assertThat(node(replay)).isEqualTo(node(first));
        problem(upload(a,0,id,"empty.md",new byte[0],"different",List.of()),409);
        assertThat(send(a,"PUT","/api/me/resume",Map.of("mode","PASTE","markdownText","pasted","expectedVersion",2,"requestId",UUID.randomUUID())).statusCode()).isEqualTo(200);
        assertThat(bytes(a,"GET","/api/me/resume/original",Map.of()).statusCode()).isEqualTo(404);
        UUID delete=UUID.randomUUID(); var deleted=send(a,"DELETE","/api/me/resume?expectedVersion=3&requestId="+delete,null);
        assertThat(deleted.statusCode()).isEqualTo(200); assertThat(send(a,"DELETE","/api/me/resume?expectedVersion=3&requestId="+delete,null).body()).isEqualTo(deleted.body());
        var current=node(send(a,"GET","/api/me/resume",null)); assertThat(current.path("exists").asBoolean()).isFalse(); assertThat(current.path("version").asLong()).isEqualTo(4);
    }
    @Test void numericModeAndNonIntegerVersionAreRejectedWithoutCoercionOrCurrentChange() throws Exception {
        assertThat(send(a,"PUT","/api/me/resume",Map.of("mode","PASTE","markdownText","preserved","expectedVersion",0,"requestId",UUID.randomUUID())).statusCode()).isEqualTo(200);
        var current=node(send(a,"GET","/api/me/resume",null));
        for(int mode:List.of(0,1))
            problem(send(a,"PUT","/api/me/resume",Map.of("mode",mode,"markdownText","coerced mode","expectedVersion",1,"requestId",UUID.randomUUID())),400);
        for(Object version:List.of(1.5,1.0,"1"))
            problem(send(a,"PUT","/api/me/resume",Map.of("mode","PASTE","markdownText","coerced version","expectedVersion",version,"requestId",UUID.randomUUID())),400);
        assertThat(node(send(a,"GET","/api/me/resume",null))).isEqualTo(current);
        assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_write_receipts WHERE user_id=?",Integer.class,a.id())).isEqualTo(1);
    }
    @Test void multipartAllowsExactEmojiBoundaryAndRejectsEitherOverLimitWithoutReplacingCurrent() throws Exception {
        String exact="😀".repeat(20000),over="😀".repeat(20001);
        byte[] original=exact.getBytes(StandardCharsets.UTF_8);
        assertThat(original).hasSize(80000);
        var imported=upload(a,0,UUID.randomUUID(),"exact.md",original,exact,List.of());
        assertThat(imported.statusCode()).isEqualTo(200);
        var current=node(send(a,"GET","/api/me/resume",null));
        assertThat(current.path("markdownText").asText()).isEqualTo(exact);
        assertThat(bytes(a,"GET","/api/me/resume/original",Map.of()).body()).isEqualTo(original);
        problem(upload(a,1,UUID.randomUUID(),"over-original.md",over.getBytes(StandardCharsets.UTF_8),"safe",List.of()),400);
        problem(upload(a,1,UUID.randomUUID(),"over-final.md","safe".getBytes(StandardCharsets.UTF_8),over,List.of()),400);
        for(byte[] invalid:new byte[][]{ {(byte)0xc3,0x28}, {'a',0} })
            problem(uploadWithFinalBytes(a,1,UUID.randomUUID(),"valid.md","safe".getBytes(StandardCharsets.UTF_8),invalid,List.of()),400);
        assertThat(node(send(a,"GET","/api/me/resume",null))).isEqualTo(current);
        assertThat(jdbc.queryForObject("SELECT version FROM user_resumes WHERE user_id=?",Long.class,a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resume_objects WHERE user_id=?",Integer.class,a.id())).isEqualTo(1);
    }
    HttpResponse<String> upload(Client client,long version,UUID request,String filename,byte[] file,String text,List<String> extra) throws Exception {
        return uploadWithFinalBytes(client,version,request,filename,file,text.getBytes(StandardCharsets.UTF_8),extra);
    }
    HttpResponse<String> uploadWithFinalBytes(Client client,long version,UUID request,String filename,byte[] file,byte[] text,List<String> extra) throws Exception {
        String boundary="wb"+UUID.randomUUID(); var output=new ByteArrayOutputStream();
        field(output,boundary,"expectedVersion",Long.toString(version)); field(output,boundary,"requestId",request.toString()); field(output,boundary,"markdownText",text);
        for(String name:extra) field(output,boundary,name,"x");
        output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/x-client-lie\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(file); output.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var builder=HttpRequest.newBuilder(uri("/api/me/resume/import")).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(output.toByteArray()));
        if(client.token()!=null) builder.header("X-XSRF-TOKEN",client.token()); return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    void field(ByteArrayOutputStream output,String boundary,String name,String value) throws IOException { field(output,boundary,name,value.getBytes(StandardCharsets.UTF_8)); }
    void field(ByteArrayOutputStream output,String boundary,String name,byte[] value) throws IOException {
        output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value); output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    HttpResponse<String> send(Client client,String method,String path,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)); if(client.token()!=null)builder.header("X-XSRF-TOKEN",client.token());
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))); if(body!=null)builder.header("Content-Type","application/json");
        return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<byte[]> bytes(Client client,String method,String path,Map<String,String> headers) throws Exception { var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).method(method,HttpRequest.BodyPublishers.noBody()); headers.forEach(builder::header); return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofByteArray()); }
    URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    JsonNode node(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    void problem(HttpResponse<String> response,int status) throws Exception { assertThat(response.statusCode()).isEqualTo(status); assertThat(node(response).path("detail").asText()).isNotBlank(); assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private"); }
    void privateHeaders(HttpResponse<?> response) { assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private"); assertThat(response.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff"); }
}
