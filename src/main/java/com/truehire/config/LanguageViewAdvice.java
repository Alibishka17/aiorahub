package com.truehire.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class LanguageViewAdvice {

    @ModelAttribute("languageUrls")
    Map<String, String> languageUrls(HttpServletRequest request) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (String language : new String[]{"ru", "kk", "en"}) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(request.getRequestURI());
            if (request.getQueryString() != null) {
                builder.query(request.getQueryString());
            }
            urls.put(language, builder.replaceQueryParam("lang", language).build().toUriString());
        }
        return urls;
    }
}
