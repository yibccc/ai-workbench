package com.aiworkbench.interview;

import com.aiworkbench.service.AccountService;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Real sessions/CSRF/owner HTTP with actual queued worker and deterministic isolated model. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterviewHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired AccountService accounts;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    Client a,b,admin;
    record Client(HttpClient http,String token,UUID id) {}
    @BeforeEach void setup() throws Exception { a=account("USER"); b=account("USER"); admin=account("ADMIN"); }
    @AfterEach void cleanup() {
        for(Client client:List.of(a,b,admin)) {
            for(String table:List.of("interview_operation_receipts","interview_ai_jobs","interview_evaluations","interview_answers","interview_questions","interview_sessions","interview_jd_analyses")) jdbc.update("DELETE FROM "+table+" WHERE user_id=?",client.id());
            jdbc.update("DELETE FROM user_accounts WHERE id=?",client.id());
        }
    }
    Client account(String role) throws Exception {
        var account=accounts.create("interview-http-"+UUID.randomUUID(),"Interview HTTP strong password 123",role);
        var http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        var client=new Client(http,null,account.id()); String token=node(send(client,"GET","/api/auth/csrf",null)).path("token").asText(); client=new Client(http,token,account.id());
        assertThat(send(client,"POST","/api/auth/login",Map.of("username",account.username(),"password","Interview HTTP strong password 123")).statusCode()).isEqualTo(200); return client;
    }
    Map<String,Object> create(UUID request) { return Map.of("requestId",request,"direction","REACT_FRONTEND","difficulty","SENIOR","mainQuestionCount",3,"useCurrentResume",false); }
    @Test void ownerAdminAnonymousCsrfPrivateCacheAndExactDTOsAcrossFullFlow() throws Exception {
        var input=create(UUID.randomUUID()); var created=send(a,"POST","/api/interviews",input); assertThat(created.statusCode()).isEqualTo(200);
        UUID id=UUID.fromString(node(created).path("sessionId").asText()); String path="/api/interviews/"+id;
        var ready=await(path,"generationStatus","SUCCEEDED"); assertThat(ready.path("questions").size()).isEqualTo(6);
        assertThat(ready.path("direction").asText()).isEqualTo("REACT_FRONTEND"); assertThat(ready.path("difficulty").asText()).isEqualTo("SENIOR");
        assertThat(ready.path("questions").get(0).path("turnIndex").asInt()).isZero(); assertThat(ready.path("questions").get(1).path("parentMainIndex").asInt()).isZero();
        for(Client foreign:List.of(b,admin)) {
            problem(send(foreign,"GET",path,null),404); problem(send(foreign,"GET",path+"/report",null),404);
            problem(send(foreign,"POST",path+"/complete",Map.of("expectedVersion",999,"requestId",UUID.randomUUID(),"early",true)),404);
            problem(send(foreign,"DELETE",path,null),404);
        }
        problem(send(new Client(HttpClient.newHttpClient(),null,null),"GET",path,null),401);
        problem(send(new Client(a.http(),null,a.id()),"POST",path+"/answers/0/submit",Map.of("expectedVersion",ready.path("version").asLong(),"requestId",UUID.randomUUID(),"answerText","")),403);
        var draft=send(a,"PUT",path+"/answer-draft",Map.of("expectedVersion",ready.path("version").asLong(),"requestId",UUID.randomUUID(),"turnIndex",0,"answerText","草稿")); assertThat(draft.statusCode()).isEqualTo(200);
        var current=node(send(a,"GET",path,null)); assertThat(current.path("currentTurn").asInt()).isZero();
        UUID submit=UUID.randomUUID(); var body=Map.of("expectedVersion",current.path("version").asLong(),"requestId",submit,"answerText","");
        var submitted=send(a,"POST",path+"/answers/0/submit",body); assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(node(send(a,"POST",path+"/answers/0/submit",body))).isEqualTo(node(submitted));
        current=node(send(a,"GET",path,null)); assertThat(current.path("currentTurn").asInt()).isEqualTo(1); assertThat(current.path("answers").get(0).path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(send(a,"POST",path+"/complete",Map.of("expectedVersion",current.path("version").asLong(),"requestId",UUID.randomUUID(),"early",true)).statusCode()).isEqualTo(200);
        await(path,"evaluationStatus","SUCCEEDED"); var report=node(send(a,"GET",path+"/report",null));
        assertThat(report.path("turns").get(0).path("status").asText()).isEqualTo("SCORED"); assertThat(report.path("turns").get(1).path("status").asText()).isEqualTo("UNANSWERED"); assertThat(report.path("totalScore").isNumber()).isTrue();
        var page=node(send(a,"GET","/api/interviews/page?page=0&size=5",null)); assertThat(page.path("totalElements").asInt()).isEqualTo(1); assertThat(page.path("items").get(0).has("questions")).isFalse();
        assertThat(send(a,"DELETE",path,null).statusCode()).isEqualTo(204); assertThat(send(a,"DELETE",path,null).statusCode()).isEqualTo(204);
        assertThat(node(send(a,"POST","/api/interviews",input)).path("state").asText()).isEqualTo("DELETED"); problem(send(a,"GET",path,null),404);
    }
    @Test void jdParse202ExactRawAndCancelledAnalysisCannotBeUsedAgain() throws Exception {
        var parsed=send(a,"POST","/api/interviews/jd/parse",Map.of("direction","JAVA_BACKEND","jdText","事务\r\n😀","requestId",UUID.randomUUID()));
        assertThat(parsed.statusCode()).isEqualTo(202); UUID id=UUID.fromString(node(parsed).path("id").asText()); String path="/api/interviews/jd/"+id;
        var jd=await(path,"status","SUCCEEDED"); assertThat(jd.path("jdText").asText()).isEqualTo("事务\r\n😀"); assertThat(jd.path("result").path("matched").asBoolean()).isTrue();
        problem(send(b,"GET",path,null),404); problem(send(admin,"DELETE",path,null),404);
        assertThat(send(a,"DELETE",path,null).statusCode()).isEqualTo(204); problem(send(a,"GET",path,null),404);
        var create=new HashMap<>(create(UUID.randomUUID())); create.put("direction","JAVA_BACKEND"); create.put("jdText","事务\r\n😀"); create.put("jdAnalysisId",id);
        problem(send(a,"POST","/api/interviews",create),404);
    }
    @Test void fractionalStringEnumAndBooleanCoercionAndMissingFrozenFieldsAreRejectedLocally() throws Exception {
        for(var entry:List.<Map.Entry<String,Object>>of(Map.entry("mainQuestionCount",3.5),Map.entry("mainQuestionCount","3"),
                Map.entry("direction",0),Map.entry("difficulty",1),Map.entry("useCurrentResume","false"),Map.entry("expectedResumeVersion",0.5))) {
            var payload=new HashMap<>(create(UUID.randomUUID())); payload.put(entry.getKey(),entry.getValue()); problem(send(a,"POST","/api/interviews",payload),400);
        }
        for(String missing:List.of("direction","difficulty","useCurrentResume")) {
            var payload=new HashMap<>(create(UUID.randomUUID())); payload.remove(missing); problem(send(a,"POST","/api/interviews",payload),400);
        }
        var created=send(a,"POST","/api/interviews",create(UUID.randomUUID())); UUID id=UUID.fromString(node(created).path("sessionId").asText());
        String path="/api/interviews/"+id; var ready=await(path,"generationStatus","SUCCEEDED");
        problem(send(a,"PUT",path+"/answer-draft",Map.of("expectedVersion",ready.path("version").asLong(),"requestId",UUID.randomUUID(),"answerText","safe","turnIndex",0.5)),400);
        problem(send(a,"POST",path+"/answers/0/submit",Map.of("expectedVersion",ready.path("version").asLong()+0.5,"requestId",UUID.randomUUID(),"answerText","safe")),400);
        assertThat(node(send(a,"GET",path,null)).path("currentTurn").asInt()).isZero();
    }
    JsonNode await(String path,String field,String value) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos(); JsonNode current;
        do { var response=send(a,"GET",path,null); assertThat(response.statusCode()).isEqualTo(200); current=node(response); if(current.path(field).asText().equals(value)) return current; Thread.sleep(50); } while(System.nanoTime()<deadline);
        throw new AssertionError("Persisted async state did not reach "+value+": "+current.path(field).asText());
    }
    HttpResponse<String> send(Client client,String method,String path,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15)); if(client.token()!=null) builder.header("X-XSRF-TOKEN",client.token());
        if(body!=null) builder.header("Content-Type","application/json"); builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response=client.http().send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(path.startsWith("/api/interviews")) { assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private"); assertThat(response.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff"); }
        return response;
    }
    JsonNode node(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    void problem(HttpResponse<String> response,int status) throws Exception { assertThat(response.statusCode()).isEqualTo(status); assertThat(node(response).path("detail").asText()).isNotBlank(); assertThat(response.body()).doesNotContain("草稿","SENIOR 主问","SELECT ","jdbc:"); }
}
