# Alexa proxy
Spring boot app that works as a proxy between Amazon Alexa in the cloud and
a local running smart home server running an Alexa Skill.
It works with all Alexa powered devices, such as the Amazon Echo, Amazon Tap and Amazon Echo Dot.

The proxy will check several things before forwarding the request:
A. It must be a POST request (required)
B. It must come from the Amazon Alexa cloud (optional)
C. It's timestamp can't be too far off (optional)
D. It must contain a whitelisted application id (optional)
E. It must contain a whitelisted user id (optional)

# Benefits
- Exposes one single point of entry for the outside world to your local network
- Will perform checks making sure it only forwards requests that you want to allow
- Your local network server can run without extra tight security and is easily testable from inside your local network

# FAQ
Q: How do I run the proxy?  
A:
1. Clone the project locally
2. Copy and edit one of the example properties files in src/test/resources
3. Run with: mvn exec:java -Dexec.mainClass="com.programyourhome.alexa.proxy.AlexaProxySpringBootApplication" -Dalexa.proxy.properties.location=path/to/alexa.proxy.properties

Q: How do I get Amazon to talk to the proxy?  
A: For Amazon to accept a connection to a skill, it must be served on HTTPS. As long as you have it running in development mode (which you probably want anyway for a
skill connecting to your local network) you can use a self signed certificate. The easiest way to get that working, is use nginx or apache for the HTTPS part and let it forward to the proxy. Example nginx configuration:
```
server {
  listen 443 ssl;

  ssl_certificate /etc/ssl/cacert.pem;         # path to your cacert.pem
  ssl_certificate_key /etc/ssl/privkey.pem;    # path to your privkey.pem

  location / {
    proxy_pass  http://127.0.0.1:8888/proxy;
  }
}
```

Q: Isn't there already a Github project for that? (https://github.com/sidoh/echo_proxy)  
A: Yes, but I consider this project better, because this project uses a plain Servlet for the proxying instead of a Speechlet, which means more direct control of the proxy handling, such as:
- The request validation is more explicit.
- The request payload bytes will be forwarded 1-on-1 instead of rebuilding a similar request
- The response will include all original details, instead of just a speech message. 
- More forward compatible, because it does not depend on the specific Speechlet interface.

# Thanks
Thanks to Chris Mullins (https://github.com/sidoh/) for his proxy project which convinced me to run my Alexa skill locally.  
And thanks to Amazon for creating the cool Alexa service that makes my smart home even cooler then before! :-)

