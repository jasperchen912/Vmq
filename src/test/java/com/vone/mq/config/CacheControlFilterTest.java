package com.vone.mq.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheControlFilterTest {

    private final CacheControlFilter filter = new CacheControlFilter();

    @Test
    void staticAssetsAreCacheableForSevenDays() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/layui/layui.all.js");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(
                "public, max-age=604800, stale-while-revalidate=86400",
                response.getHeader("Cache-Control"));
    }

    @Test
    void htmlRevalidatesAndApiResponsesAreNotStored() throws Exception {
        MockHttpServletResponse htmlResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/aaa.html"),
                htmlResponse,
                new MockFilterChain());
        assertEquals("no-cache", htmlResponse.getHeader("Cache-Control"));

        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("GET", "/admin/getMain"),
                apiResponse,
                new MockFilterChain());
        assertEquals("no-store", apiResponse.getHeader("Cache-Control"));
    }
}
