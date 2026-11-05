package com.odontomax.infrastructure.security;

import com.odontomax.identidad.api.SecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpSecurityTest {

    @Test
    @DisplayName("SecurityUtils.getClientIp retorna IP remota cuando el request tiene remoteAddr válido")
    void testGetClientIpWithValidRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("190.237.45.12");

        String ip = SecurityUtils.getClientIp(request);
        assertEquals("190.237.45.12", ip);
    }

    @Test
    @DisplayName("SecurityUtils.getClientIp retorna 127.0.0.1 si request es nulo o remoteAddr está vacío")
    void testGetClientIpWithNullOrEmpty() {
        assertEquals("127.0.0.1", SecurityUtils.getClientIp(null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        assertEquals("127.0.0.1", SecurityUtils.getClientIp(request));

        request.setRemoteAddr("   ");
        assertEquals("127.0.0.1", SecurityUtils.getClientIp(request));
    }
}
