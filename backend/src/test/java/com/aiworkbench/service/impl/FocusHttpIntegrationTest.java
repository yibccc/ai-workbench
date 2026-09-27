package com.aiworkbench.service.impl;

import com.aiworkbench.dto.focus.FocusModels.*;
import com.aiworkbench.service.FocusService;
import com.aiworkbench.support.OwnerTestContext;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FocusHttpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FocusService service;
    Cookie cookie;
    @BeforeEach void setup() throws Exception {
        OwnerTestContext.ensureAccounts(jdbc);OwnerTestContext.use(OwnerTestContext.USER_ID);
        cookie=OwnerTestContext.login(mvc);OwnerTestContext.use(OwnerTestContext.USER_ID);
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request){
        return OwnerTestContext.authenticated(request,cookie);
    }
    @Test void authenticationAndCsrfAreEnforcedWhileFocusWritesAreAvailable() throws Exception {
        mvc.perform(get("/api/focus/routines")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/focus/routines"))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/focus/capabilities"))).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/focus/routines/fill-today")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/focus/routines/fill-today")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
    @Test void invalidMinutesAndMissingFieldsReturn400() throws Exception {
        for(String minutes:new String[]{"1.5","\"25\"","0","481","null"}){
            String body="{\"requestId\":\""+UUID.randomUUID()+"\",\"title\":\"test\",\"targetMinutes\":"+minutes+"}";
            mvc.perform(auth(post("/api/focus/sessions")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(post("/api/focus/sessions")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\""+UUID.randomUUID()+"\",\"title\":\"test\"}"))
                .andExpect(status().isBadRequest());
        for(String minutes:new String[]{"1.5","\"25\"","0","481","null"}){
            mvc.perform(auth(post("/api/focus/routines")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"test\",\"weekdays\":[1],\"defaultDurationMinutes\":"+minutes+"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(post("/api/focus/routines")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"test\",\"weekdays\":[1]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void routineUpdateRequiresVersionAndStaleVersionConflicts() throws Exception {
        Routine routine=service.createRoutine(new SaveRoutine("规则版本",null,java.util.List.of(1),25,null,null));
        String body="{\"title\":\"更新规则\",\"weekdays\":[1],\"defaultDurationMinutes\":25}";
        mvc.perform(auth(put("/api/focus/routines/"+routine.id())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(auth(put("/api/focus/routines/"+routine.id())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.substring(0,body.length()-1)+",\"version\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
    }
    @Test void missingAndForeignResourceReturn404AndStaleVersion409() throws Exception {
        Session s=service.start(new Start(UUID.randomUUID(),"HTTP",null,null,25,10));
        mvc.perform(auth(get("/api/focus/sessions/"+UUID.randomUUID()))).andExpect(status().isNotFound());
        Cookie foreign=OwnerTestContext.login(mvc,"owner-test-b");
        mvc.perform(OwnerTestContext.authenticated(get("/api/focus/sessions/"+s.id()),foreign,
                OwnerTestContext.OTHER_ID,"USER",0)).andExpect(status().isNotFound());
        Cookie administrator=OwnerTestContext.login(mvc,"owner-test-admin");
        mvc.perform(OwnerTestContext.authenticated(get("/api/focus/sessions/"+s.id()),administrator,
                OwnerTestContext.ADMIN_ID,"ADMIN",0)).andExpect(status().isNotFound());
        mvc.perform(OwnerTestContext.authenticated(post("/api/focus/sessions/"+s.id()+"/end"),administrator,
                OwnerTestContext.ADMIN_ID,"ADMIN",0).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":"+s.version()+"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/focus/sessions/"+s.id()+"/checkpoint")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());
    }
}
