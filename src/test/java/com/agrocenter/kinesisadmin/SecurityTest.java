package com.agrocenter.kinesisadmin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.kinesis.KinesisClient;

@WebMvcTest(Streams.class)
@Import(Security.class)
class SecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean KinesisClient aws;
    @Test void requiresTokenAndAdminRole() throws Exception {
        mvc.perform(get("/api/kinesis-admin/streams/test")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kinesis-admin/streams/test").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/kinesis-admin/streams/test").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isBadRequest());
    }
    @Test void creationChecksRoleValidationAndWriteSwitch() throws Exception {
        var request = post("/api/kinesis-admin/streams").contentType("application/json")
                .content("{\"nombre\":\"agrocenter-prueba\",\"shards\":1}");
        mvc.perform(request).andExpect(status().isUnauthorized());
        mvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/kinesis-admin/streams").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"nombre\":\"invalido\",\"shards\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/kinesis-admin/streams").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"nombre\":\"agrocenter-prueba\",\"shards\":1}"))
                .andExpect(status().isConflict());
        org.mockito.Mockito.verifyNoInteractions(aws);
    }
}
