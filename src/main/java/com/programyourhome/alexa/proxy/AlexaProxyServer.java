package com.programyourhome.alexa.proxy;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.context.ApplicationContext;

public class AlexaProxyServer {

    private static boolean shutdown = false;

    public static void startServer(final Class<?> springBootApplicationClass) {
        final String usageMessage = "Please provide the correct path to the Alexa Proxy property location with: -Dalexa.proxy.properties.location=/path/to/file";
        final String alexaProxyPropertyLocation = System.getProperty("alexa.proxy.properties.location");
        if (alexaProxyPropertyLocation == null) {
            System.out.println("No value provided for property 'alexa.proxy.properties.location'.");
            System.out.println(usageMessage);
            System.exit(-1);
        }
        final File propertiesFile = new File(alexaProxyPropertyLocation);
        if (!propertiesFile.exists()) {
            System.out.println("Property file not found: '" + alexaProxyPropertyLocation + "'.");
            System.out.println(usageMessage);
            System.exit(-1);
        }
        System.out.println("Using properties in file: " + propertiesFile.getAbsolutePath());

        // Set the Spring config location file to the Alexa Proxy properties file, so it will pick up all
        // Spring boot config from there as well. Note: this must be done like this instead of using a @PropertySource
        // annotation, because otherwise the logging properties will not be picked up. They must be available
        // very early in the boot process, see also: https://github.com/spring-projects/spring-boot/issues/2709
        System.setProperty(ConfigFileApplicationListener.CONFIG_LOCATION_PROPERTY, propertiesFile.toURI().toString());
        final SpringApplication application = new SpringApplication(springBootApplicationClass);
        final ApplicationContext springBootContext = application.run(new String[0]);
        // Start a shutdown poller for graceful shutdown when needed.
        new ShutdownPoller(springBootContext).start();
    }

    public static void stopServer() {
        // Trigger the shutdown poller, so the actual shutting down is done outside of the Spring context and threads.
        shutdown = true;
    }

    /**
     * This shutdown poller will wait until the shutdown flag is set to true and then trigger
     * a graceful Spring Boot shutdown and exit the java process after that.
     */
    private static class ShutdownPoller extends Thread {
        private final ApplicationContext springBootContext;

        public ShutdownPoller(final ApplicationContext springBootContext) {
            super("Shutdown poller");
            this.springBootContext = springBootContext;
        }

        @Override
        public void run() {
            while (!AlexaProxyServer.shutdown) {
                try {
                    Thread.sleep(100);
                } catch (final InterruptedException e) {
                }
            }
            System.exit(SpringApplication.exit(this.springBootContext, () -> 0));
        }
    }

}
