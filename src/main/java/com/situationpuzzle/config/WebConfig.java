package com.situationpuzzle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 反向代理（Kestrel）轉發後，後端所見 Host 與瀏覽器 Origin 不一致，
                // Spring 會把同源 POST（瀏覽器同源亦帶 Origin）誤判為跨源；
                // 故須把正式部署域名也列入允許清單，避免 403 Invalid CORS request。
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://situationpuzzle.mypc.tw",
                        "http://situationpuzzle.mypc.tw")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowCredentials(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
