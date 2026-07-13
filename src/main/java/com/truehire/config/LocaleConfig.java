package com.truehire.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ru", "kk", "en");

    @Bean
    LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("AIORAHUB_LANG");
        resolver.setCookiePath("/");
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSameSite("Lax");
        resolver.setDefaultLocaleFunction(LocaleConfig::browserLocale);
        return resolver;
    }

    @Bean
    LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor() {
            @Override
            protected Locale parseLocaleValue(String localeValue) {
                String language = Locale.forLanguageTag(localeValue).getLanguage();
                return SUPPORTED_LANGUAGES.contains(language)
                        ? Locale.forLanguageTag(language)
                        : Locale.ENGLISH;
            }
        };
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    private static Locale browserLocale(HttpServletRequest request) {
        Enumeration<Locale> locales = request.getLocales();
        while (locales.hasMoreElements()) {
            String language = locales.nextElement().getLanguage();
            if (SUPPORTED_LANGUAGES.contains(language)) {
                return Locale.forLanguageTag(language);
            }
        }
        return Locale.ENGLISH;
    }
}
