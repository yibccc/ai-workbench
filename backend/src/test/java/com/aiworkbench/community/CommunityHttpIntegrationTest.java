package com.aiworkbench.community;

import com.aiworkbench.service.AccountService;
import com.aiworkbench.security.SessionActivity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

/** Actual HTTP server + Cookie/CSRF + Redis sessions, not mock authentication. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommunityHttpIntegrationTest {
    private static final String PASSWORD = "community HTTP strong password 123";
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SessionActivity activity;
    Client a,b,admin;

    @BeforeEach void setup() throws Exception {
        SecurityContextHolder.clearContext();
        a=account("USER"); b=account("USER"); admin=account("ADMIN");
        clearInvocations(activity);
    }
    @AfterEach void cleanup() {
        CommunityTestData.remove(jdbc,a.id());
        CommunityTestData.remove(jdbc,b.id());
        CommunityTestData.remove(jdbc,admin.id());
        SecurityContextHolder.clearContext();
    }
    Client account(String role) throws Exception {
        var account = accounts.create("community-http-"+UUID.randomUUID(),PASSWORD,role);
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        String token = json.readTree(send(client,"GET","/api/auth/csrf",null,null).body()).path("token").asText();
        assertThat(send(client,"POST","/api/auth/login",json.writeValueAsString(Map.of("username",account.username(),"password",PASSWORD)),token).statusCode()).isEqualTo(200);
        return new Client(client,token,account.id());
    }
    record Client(HttpClient http,String csrf,UUID id) {}

    @Test void anonymousForeignOwnerAndCsrfAreRejectedWithoutDataOrMutation() throws Exception {
        UUID post=create(a,"DAILY","2059-01-02","秘密草稿");
        HttpClient anonymous=HttpClient.newHttpClient();
        for(String path:List.of("/api/community/posts/page","/api/community/posts/"+post,
                "/api/community/authors/"+a.id(),"/api/me/posts/"+post)) {
            var denied=send(anonymous,"GET",path,null,null);
            problem(denied,401);
            assertThat(denied.body()).doesNotContain("秘密草稿");
            assertThat(denied.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        }
        for(Client foreign:List.of(b,admin)) {
            problem(send(foreign,"GET","/api/me/posts/"+post,null),404);
            problem(send(foreign,"PUT","/api/me/posts/"+post,Map.of("version",999,"type","DAILY","businessDate","2059-01-02","title","窃取","bodyMarkdown","窃取","attachmentIds",List.of())),404);
            problem(send(foreign,"POST","/api/me/posts/"+post+"/withdraw",Map.of("version",999)),404);
        }
        var noCsrf=send(a.http(),"PUT","/api/me/posts/"+post,json.writeValueAsString(Map.of("version",0,"type","DAILY","businessDate","2059-01-02","title","非法","bodyMarkdown","非法")),null);
        problem(noCsrf,403);
        assertThat(send(a,"GET","/api/me/posts/"+post,null).body()).contains("秘密草稿").doesNotContain("非法");
        assertThat(jdbc.queryForObject("SELECT version FROM community_posts WHERE id=?",Long.class,post)).isZero();
    }

    @Test void publishedProjectionIsSafeAndOwnerLogoutDoesNotChangeCurrentVisibility() throws Exception {
        UUID post=create(a,"BLOG",null,"私有旧草稿");
        var body=publishPayload(0,"BLOG",null,"公开标题","公开正文",UUID.randomUUID());
        var published=send(a,"POST","/api/me/posts/"+post+"/publish",body);
        assertThat(published.statusCode()).isEqualTo(200);
        var reader=send(b,"GET","/api/community/posts/"+post,null);
        assertThat(reader.statusCode()).isEqualTo(200);
        assertThat(reader.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private");
        var node=json.readTree(reader.body());
        assertThat(node.path("bodyMarkdown").asText()).isEqualTo("公开正文");
        assertThat(node.path("author").path("nickname").asText()).isEqualTo("未设置昵称");
        assertNoPrivateFields(node);
        var saved=send(a,"PUT","/api/me/posts/"+post,Map.of("version",1,"type","BLOG","title","修改私有标题","summary","私有摘要","bodyMarkdown","新私有正文","attachmentIds",List.of()));
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(send(b,"GET","/api/community/posts/"+post,null).body()).isEqualTo(reader.body());
        assertThat(send(a,"POST","/api/me/posts/"+post+"/publish",body).body()).isEqualTo(published.body());
        assertThat(send(a,"POST","/api/auth/logout",Map.of()).statusCode()).isEqualTo(200);
        assertThat(send(b,"GET","/api/community/posts/"+post,null).statusCode()).isEqualTo(200);
        assertNoPrivateFields(json.readTree(send(b,"GET","/api/community/authors/"+a.id(),null).body()));
        verify(activity,never()).touch(anyString());
    }

    @Test void realAdminHideNeedsReasonRevisionAndCannotBeBypassedByAuthor() throws Exception {
        UUID post=create(a,"MOMENT",null,"草稿");
        var body=publishPayload(0,"MOMENT",null,"","公开动态",UUID.randomUUID());
        var pub=json.readTree(send(a,"POST","/api/me/posts/"+post+"/publish",body).body());
        String revision=pub.path("revisionId").asText();
        String path="/api/admin/community/posts/"+post+"/hide";
        problem(send(b,"POST",path,Map.of("expectedRevisionId",revision,"reason","未获许可")),403);
        problem(send(admin,"POST",path,Map.of("expectedRevisionId",revision,"reason"," ")),400);
        problem(send(admin,"POST",path,Map.of("expectedRevisionId",UUID.randomUUID(),"reason","过期下架")),409);
        assertThat(send(admin,"POST",path,Map.of("expectedRevisionId",revision,"reason","规则原因")).statusCode()).isEqualTo(200);
        problem(send(b,"GET","/api/community/posts/"+post,null),404);
        assertThat(json.readTree(send(b,"GET","/api/community/posts/page?authorId="+a.id(),null).body()).path("items")).isEmpty();
        assertThat(send(a,"POST","/api/me/posts/"+post+"/publish",body).statusCode()).isEqualTo(200);
        problem(send(a,"POST","/api/me/posts/"+post+"/publish",publishPayload(2,"MOMENT",null,"","绕过下架",UUID.randomUUID())),409);
        assertThat(jdbc.queryForObject("SELECT status FROM community_posts WHERE id=?",String.class,post)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_moderation_audit WHERE post_id=?",Integer.class,post)).isEqualTo(1);
        assertThat(send(a,"GET","/api/me/posts/"+post,null).body()).contains("规则原因");
    }

    @Test void pagingProfilesAndMalformedOrAnonymousPublicationStayExplicit() throws Exception {
        for(int i=0;i<6;i++) {
            UUID post=create(a,"MOMENT",null,"草稿"+i);
            assertThat(send(a,"POST","/api/me/posts/"+post+"/publish",publishPayload(0,"MOMENT",null,"","正文"+i,UUID.randomUUID())).statusCode()).isEqualTo(200);
        }
        var first=json.readTree(send(b,"GET","/api/community/posts/page?authorId="+a.id()+"&size=5",null).body());
        var second=json.readTree(send(b,"GET","/api/community/posts/page?authorId="+a.id()+"&size=5&page=1",null).body());
        assertThat(first.path("totalElements").asInt()).isEqualTo(6);
        assertThat(first.path("items")).hasSize(5);
        assertThat(second.path("items")).hasSize(1);
        assertNoPrivateFields(first);
        problem(send(b,"GET","/api/community/posts/page?size=6",null),400);
        problem(send(b,"GET","/api/community/posts/page?type=NOTE",null),400);
        problem(send(a,"GET","/api/me/community/sources/page",null),400);
        assertThat(send(a,"GET","/api/me/community/profile",null).body()).contains("未设置昵称");
        var profile=send(a,"PUT","/api/me/community/profile",Map.of("nickname","公开昵称","bio","公开简介","version",0));
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(send(b,"GET","/api/community/authors/"+a.id(),null).body()).contains("公开昵称","公开简介");
        problem(send(a,"PUT","/api/me/community/profile",Map.of("nickname","旧修改","bio","","version",0)),409);
        UUID draft=create(a,"BLOG",null,"非公开草稿");
        var scope=publishPayload(0,"BLOG",null,"标题","正文",UUID.randomUUID()); scope.put("visibility","PUBLIC");
        problem(send(a,"POST","/api/me/posts/"+draft+"/publish",scope),400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_post_revisions WHERE post_id=?",Integer.class,draft)).isZero();
    }

    @Test void selectedSourceIdsAndFieldsAreValidatedThroughRealHttpWithAtomicFailure() throws Exception {
        UUID own=UUID.randomUUID(),foreign=UUID.randomUUID();
        jdbc.update("INSERT INTO work_records(id,user_id,content,occurred_at) VALUES (?,?,'本人HTTP素材','2060-01-02T04:00:00Z'),(?,?,'他人秘密素材','2060-01-02T04:00:00Z')",
                own,a.id(),foreign,b.id());
        var page=json.readTree(send(a,"GET","/api/me/community/sources/page?date=2060-01-02&size=5",null).body());
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(own.toString());
        assertThat(page.toString()).doesNotContain("他人秘密素材");
        var invalid=Map.of("date","2060-01-02","selections",List.of(
                Map.of("recordId",own,"fields",List.of("CONTENT")),Map.of("recordId",foreign,"fields",List.of("CONTENT"))),"includeFocus",false);
        problem(send(a,"POST","/api/me/community/share-drafts",invalid),404);
        problem(send(a,"POST","/api/me/community/share-drafts",Map.of("date","2060-01-02","selections",List.of(
                Map.of("recordId",own,"fields",List.of("PROJECT_NAME"))))),400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_posts WHERE owner_id=?",Integer.class,a.id())).isZero();
        var generated=send(a,"POST","/api/me/community/share-drafts",Map.of("date","2060-01-02","selections",List.of(
                Map.of("recordId",own,"fields",List.of("CONTENT"))),"includeFocus",false));
        assertThat(generated.statusCode()).isEqualTo(201);
        assertThat(generated.body()).contains("本人HTTP素材").doesNotContain("他人秘密素材");
        assertThat(json.readTree(send(b,"GET","/api/community/posts/page?authorId="+a.id(),null).body()).path("items")).isEmpty();
    }

    @Test void staleWritesAndUnknownAttachmentReferencesDoNotPartiallyMutatePublishedContent() throws Exception {
        UUID post = create(a, "BLOG", null, "原草稿");
        assertThat(send(a, "POST", "/api/me/posts/"+post+"/publish",
                publishPayload(0, "BLOG", null, "原标题", "当前公开正文", UUID.randomUUID())).statusCode()).isEqualTo(200);
        String ownerBefore = send(a, "GET", "/api/me/posts/"+post, null).body();
        String readerBefore = send(b, "GET", "/api/community/posts/"+post, null).body();
        var save = new LinkedHashMap<String,Object>();
        save.put("version", 0); save.put("type", "BLOG"); save.put("title", "拒绝的新标题");
        save.put("summary", "拒绝的新摘要"); save.put("bodyMarkdown", "拒绝的新正文"); save.put("attachmentIds", List.of());
        problem(send(a, "PUT", "/api/me/posts/"+post, save), 409);
        problem(send(a, "POST", "/api/me/posts/"+post+"/withdraw", Map.of("version", 0)), 409);
        var publish = publishPayload(0, "BLOG", null, "拒绝的新标题", "拒绝的新正文", UUID.randomUUID());
        problem(send(a, "POST", "/api/me/posts/"+post+"/publish", publish), 409);
        save.put("version", 1); save.put("attachmentIds", List.of(UUID.randomUUID()));
        problem(send(a, "PUT", "/api/me/posts/"+post, save), 404);
        publish.put("version", 1); publish.put("attachmentIds", save.get("attachmentIds"));
        problem(send(a, "POST", "/api/me/posts/"+post+"/publish", publish), 404);
        for (Client foreign : List.of(b, admin)) {
            problem(send(foreign, "POST", "/api/me/posts/"+post+"/publish", publish), 404);
        }
        assertThat(send(a, "GET", "/api/me/posts/"+post, null).body()).isEqualTo(ownerBefore);
        assertThat(send(b, "GET", "/api/community/posts/"+post, null).body()).isEqualTo(readerBefore);
        assertThat(jdbc.queryForObject("SELECT version FROM community_posts WHERE id=?", Long.class, post)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_post_revisions WHERE post_id=?", Integer.class, post)).isEqualTo(1);
    }

    UUID create(Client client,String type,String date,String body) throws Exception {
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("type",type); payload.put("title","草稿标题"); payload.put("bodyMarkdown",body);
        if(date!=null)payload.put("businessDate",date);
        var response=send(client,"POST","/api/me/posts",payload);
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(response.body()).path("postId").asText());
    }
    Map<String,Object> publishPayload(long version,String type,String date,String title,String body,UUID request) {
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("requestId",request);payload.put("version",version);
        payload.put("type",type);payload.put("title",title);payload.put("summary","公开摘要");payload.put("bodyMarkdown",body);
        payload.put("visibility","MEMBERS");payload.put("attachmentIds",List.of()); if(date!=null)payload.put("businessDate",date);return payload;
    }
    HttpResponse<String> send(Client client,String method,String path,Object body) throws Exception {
        return send(client.http(),method,path,body==null?null:json.writeValueAsString(body),client.csrf());
    }
    HttpResponse<String> send(HttpClient client,String method,String path,String body,String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)builder.header("X-XSRF-TOKEN",token);
        if(body==null)builder.method(method,HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    void problem(HttpResponse<String> response,int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).path("detail").asText()).isNotBlank();
    }
    void assertNoPrivateFields(JsonNode node) {
        Set<String> forbidden=Set.of("username","passwordHash","role","enabled","ownerId","sourceSelection","recordId","taskId","todoId","sessionId","projectId","objectKey","version","likes","comments");
        if(node.isObject())node.fields().forEachRemaining(field->{assertThat(forbidden).doesNotContain(field.getKey());assertNoPrivateFields(field.getValue());});
        if(node.isArray())node.forEach(this::assertNoPrivateFields);
    }
}
