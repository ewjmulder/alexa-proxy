package com.programyourhome.alexa.proxy;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AlexaProxySpringBootApplication {

    public static void main(final String[] args) {
        AlexaProxyServer.startServer(AlexaProxySpringBootApplication.class);
    }

}