/*
 * IceWheel Energy
 * Copyright (C) 2025 IceWheel LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.icewheel.energy.application.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final CacheControl ONE_YEAR_PUBLIC = CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Apply long-term caching and content hashing to CSS and JS under classpath:/static/
        VersionResourceResolver versionResolver = new VersionResourceResolver()
                .addContentVersionStrategy("/**");

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(ONE_YEAR_PUBLIC)
                .resourceChain(true)
                .addResolver(versionResolver);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(ONE_YEAR_PUBLIC)
                .resourceChain(true)
                .addResolver(versionResolver);

        // Webjars (served from classpath META-INF), also long-term cached and versioned.
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCacheControl(ONE_YEAR_PUBLIC)
                .resourceChain(true)
                .addResolver(versionResolver);

        // You can add other static folders similarly (images, fonts) if needed later.
    }

    // Enables Thymeleaf/Spring to expand @{/...} to versioned URLs when resource chain is active.
    @Bean
    public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }
}
