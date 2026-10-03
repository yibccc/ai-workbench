package com.aiworkbench.community;

import com.aiworkbench.service.AccountService;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aiworkbench.storage.ObjectStorage;
import static org.assertj.core.api.Assertions.*;

/** Real Servlet multipart, HTTP Cookie/CSRF sessions, Redis, PostgreSQL and RustFS. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;
    Client a,b,admin;
    @BeforeEach void setup() throws Exception { a=account("USER"); b=account("USER"); admin=account("ADMIN"); }
    @AfterEach void cleanup() {
        for(Client client:List.of(a,b,admin)) {
            for(String key:jdbc.queryForList("SELECT object_key FROM community_attachments WHERE owner_id=?",String.class,client.id())) storage.delete(key);
            CommunityTestData.remove(jdbc,client.id());
        }
    }
    record Client(HttpClient http,String token,UUID id) {}
    Client account(String role) throws Exception {
        var account=accounts.create("attachment-http-"+UUID.randomUUID(),"Attachment HTTP strong password 123",role);
        var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        String token=node(send(new Client(http,null,account.id()),"GET","/api/auth/csrf",null)).path("token").asText();
        Client client=new Client(http,token,account.id());
        assertThat(send(client,"POST","/api/auth/login",Map.of("username",account.username(),"password","Attachment HTTP strong password 123")).statusCode()).isEqualTo(200); return client;
    }
    @Test void privateGetHeadAndRangeNeverExposeForeignOrAnonymousMetadata() throws Exception {
        UUID post=create(); var uploaded=upload(a,post,0,UUID.randomUUID(),"中文资料.md","# 私有文件".getBytes(StandardCharsets.UTF_8));
        assertThat(uploaded.statusCode()).isEqualTo(201); UUID id=UUID.fromString(node(uploaded).path("attachment").path("id").asText());
        String privatePath="/api/me/posts/"+post+"/attachments/"+id;
        for(String method:List.of("GET","HEAD")) {
            for(Client foreign:List.of(b,admin,new Client(HttpClient.newHttpClient(),null,null))) {
                var denied=bytes(foreign,method,privatePath,Map.of("Range","bytes=0-0","If-None-Match","*"));
                assertThat(denied.statusCode()).isEqualTo(foreign.id()==null?401:404);
                assertThat(denied.headers().firstValue("Content-Disposition")).isEmpty();
                assertThat(new String(denied.body(),StandardCharsets.UTF_8)).doesNotContain("私有文件","中文资料.md");
                assertThat(denied.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private");
            }
        }
        var own=bytes(a,"GET",privatePath,Map.of("Range","bytes=0-0","If-None-Match","*"));
        assertThat(own.statusCode()).isEqualTo(200); assertThat(new String(own.body(),StandardCharsets.UTF_8)).isEqualTo("# 私有文件");
        assertThat(own.headers().firstValue("Accept-Ranges").orElse("")).isEqualTo("none");
        assertThat(own.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment;").contains("UTF-8");
        assertThat(own.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        assertThat(own.headers().firstValue("Location")).isEmpty(); assertThat(own.headers().firstValue("ETag")).isEmpty();
        var head=bytes(a,"HEAD",privatePath,Map.of()); assertThat(head.statusCode()).isEqualTo(200); assertThat(head.body()).isEmpty();
        assertThat(head.headers().firstValueAsLong("Content-Length").orElse(-1)).isEqualTo(own.body().length);
        assertThat(send(a,"GET","/api/me/posts/"+post,null).body()).doesNotContain("objectKey","endpoint","bucket","secret","http://127.0.0.1:19000");
    }
    @Test void currentReferenceWithdrawAndHideApplyToEveryFreshRequest() throws Exception {
        UUID post=create(); var first=node(upload(a,post,0,UUID.randomUUID(),"first.md","F1".getBytes())); UUID f1=UUID.fromString(first.path("attachment").path("id").asText());
        var published=node(send(a,"POST","/api/me/posts/"+post+"/publish",publish(1,List.of(f1))));
        var second=node(upload(a,post,2,UUID.randomUUID(),"second.md","F2".getBytes())); UUID f2=UUID.fromString(second.path("attachment").path("id").asText());
        String p1="/api/community/posts/"+post+"/attachments/"+f1,p2="/api/community/posts/"+post+"/attachments/"+f2;
        assertThat(bytes(b,"GET",p1,Map.of()).body()).isEqualTo("F1".getBytes()); assertThat(bytes(b,"GET",p2,Map.of()).statusCode()).isEqualTo(404);
        var updated=send(a,"POST","/api/me/posts/"+post+"/publish",publish(3,List.of(f2))); assertThat(updated.statusCode()).isEqualTo(200);
        for(String method:List.of("GET","HEAD")) assertThat(bytes(b,method,p1,Map.of("Range","bytes=0-1")).statusCode()).isEqualTo(404);
        assertThat(bytes(b,"GET",p2,Map.of()).body()).isEqualTo("F2".getBytes());
        assertThat(send(a,"POST","/api/me/posts/"+post+"/withdraw",Map.of("version",4)).statusCode()).isEqualTo(200);
        for(String method:List.of("GET","HEAD")) assertThat(bytes(b,method,p2,Map.of("If-Modified-Since","Fri, 01 Jan 2100 00:00:00 GMT")).statusCode()).isEqualTo(404);
        var visible=node(send(a,"POST","/api/me/posts/"+post+"/publish",publish(5,List.of(f2))));
        assertThat(send(admin,"POST","/api/admin/community/posts/"+post+"/hide",Map.of("expectedRevisionId",visible.path("revisionId").asText(),"reason","HTTP attachment hide test")).statusCode()).isEqualTo(200);
        for(String method:List.of("GET","HEAD")) assertThat(bytes(b,method,p2,Map.of()).statusCode()).isEqualTo(404);
        assertThat(bytes(a,"GET","/api/me/posts/"+post+"/attachments/"+f1,Map.of()).body()).isEqualTo("F1".getBytes());
        assertThat(published.path("firstPublishedAt").asText()).isEqualTo(visible.path("firstPublishedAt").asText());
    }
    @Test void multipartOwnershipCsrfFormatAndEmptyMdKeepTextAndOtherAttachments() throws Exception {
        UUID post=create();
        assertThat(upload(b,post,999,UUID.randomUUID(),"bad.exe",new byte[]{0}).statusCode()).isEqualTo(404);
        assertThat(upload(new Client(a.http(),null,a.id()),post,0,UUID.randomUUID(),"file.md","safe".getBytes()).statusCode()).isEqualTo(403);
        var bad=upload(a,post,0,UUID.randomUUID(),"fake.pdf","%PDF-1.7\nwrong".getBytes()); problem(bad,400);
        var empty=upload(a,post,0,UUID.randomUUID(),"empty.md",new byte[0]); assertThat(empty.statusCode()).isEqualTo(201);
        UUID id=UUID.fromString(node(empty).path("attachment").path("id").asText());
        var read=bytes(a,"GET","/api/me/posts/"+post+"/attachments/"+id,Map.of()); assertThat(read.statusCode()).isEqualTo(200); assertThat(read.body()).isEmpty();
        var imageOut=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",imageOut);
        var image=upload(a,post,1,UUID.randomUUID(),"actual.png",imageOut.toByteArray()); assertThat(image.statusCode()).isEqualTo(201);
        UUID imageId=UUID.fromString(node(image).path("attachment").path("id").asText());
        var inline=bytes(a,"GET","/api/me/posts/"+post+"/attachments/"+imageId,Map.of());
        assertThat(inline.headers().firstValue("Content-Disposition").orElse("")).startsWith("inline;"); assertThat(inline.body()).isEqualTo(imageOut.toByteArray());
        assertThat(node(send(a,"GET","/api/me/posts/"+post,null)).path("draft").path("bodyMarkdown").asText()).isEqualTo("preserved body");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_attachments WHERE post_id=?",Integer.class,post)).isEqualTo(2);
    }
    UUID create() throws Exception { return UUID.fromString(node(send(a,"POST","/api/me/posts",Map.of("type","BLOG","title","title","bodyMarkdown","preserved body"))).path("postId").asText()); }
    Map<String,Object> publish(long version,List<UUID> ids) { return Map.of("version",version,"requestId",UUID.randomUUID(),"visibility","MEMBERS","type","BLOG","title","title","bodyMarkdown","published body","attachmentIds",ids); }
    HttpResponse<String> upload(Client client,UUID post,long version,UUID request,String filename,byte[] bytes) throws Exception {
        String boundary="wb"+UUID.randomUUID();
        String prefix="--"+boundary+"\r\nContent-Disposition: form-data; name=\"expectedVersion\"\r\n\r\n"+version+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"+request+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/x-client-lie\r\n\r\n";
        var body=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(prefix.getBytes(StandardCharsets.UTF_8)),HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n"));
        var builder=HttpRequest.newBuilder(uri("/api/me/posts/"+post+"/attachments")).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(body);
        if(client.token()!=null) builder.header("X-XSRF-TOKEN",client.token()); return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> send(Client client,String method,String path,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)); if(client.token()!=null)builder.header("X-XSRF-TOKEN",client.token());
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))); if(body!=null)builder.header("Content-Type","application/json"); return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<byte[]> bytes(Client client,String method,String path,Map<String,String> headers) throws Exception { var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).method(method,HttpRequest.BodyPublishers.noBody()); headers.forEach(builder::header); return client.http().send(builder.build(),HttpResponse.BodyHandlers.ofByteArray()); }
    URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }
    JsonNode node(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    void problem(HttpResponse<String> response,int status) throws Exception { assertThat(response.statusCode()).isEqualTo(status); assertThat(node(response).path("detail").asText()).isNotBlank(); }
}
