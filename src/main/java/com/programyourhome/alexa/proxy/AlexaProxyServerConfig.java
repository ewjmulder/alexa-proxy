package com.programyourhome.alexa.proxy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlexaProxyServerConfig {

    @Autowired
    private AlexaProxyServlet alexaProxyServlet;

    @Bean
    public ServletRegistrationBean servletRegistrationBean() {
        return new ServletRegistrationBean(this.alexaProxyServlet, "/proxy/*");
    }

}
