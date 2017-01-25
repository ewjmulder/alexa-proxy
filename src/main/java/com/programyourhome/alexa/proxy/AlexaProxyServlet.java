package com.programyourhome.alexa.proxy;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.amazon.speech.Sdk;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.json.SpeechletResponseEnvelope;
import com.amazon.speech.speechlet.SpeechletRequest;
import com.amazon.speech.speechlet.SpeechletRequestHandlerException;
import com.amazon.speech.speechlet.authentication.SpeechletRequestSignatureVerifier;
import com.amazon.speech.speechlet.verifier.ApplicationIdSpeechletRequestEnvelopeVerifier;
import com.amazon.speech.speechlet.verifier.SpeechletRequestEnvelopeVerifier;
import com.amazon.speech.speechlet.verifier.SpeechletRequestVerifierWrapper;
import com.amazon.speech.speechlet.verifier.TimestampSpeechletRequestVerifier;
import com.fasterxml.jackson.core.JsonParseException;

/**
 * HTTP Servlet that works as a proxy between Amazon Alexa in the cloud and
 * a local running smart home server running an Alexa Skill.
 *
 * This proxy servlet will check several things before forwarding the request:
 * A. It must be a POST request (required)
 * B. It must come from the Amazon Alexa cloud (optional)
 * C. It's timestamp can't be too far off (optional)
 * D. It must contain a whitelisted application id (optional)
 * E. It must contain a whitelisted user id (optional)
 */
@Component
public class AlexaProxyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final List<Class<?>> BAD_REQUEST_EXCEPTION_TYPES = Arrays.asList(
            SecurityException.class, SpeechletRequestHandlerException.class, JsonParseException.class, HttpClientErrorException.class);

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${forwardTo.endpoint}")
    private URI endpoint;

    @Value("${validation.checkAmazonSignature}")
    private boolean checkAmazonSignature;

    @Value("${validation.timestampToleranceInSeconds}")
    private int timestampToleranceInSeconds;

    @Value("${validation.whitelistedApplicationIds}")
    private Set<String> whitelistedApplicationIds;

    @Value("${validation.whitelistedUserIds}")
    private Set<String> whitelistedUserIds;

    private final List<SpeechletRequestEnvelopeVerifier> requestEnvelopeVerifiers;

    private final RestTemplate restTemplate;

    public AlexaProxyServlet() {
        this.requestEnvelopeVerifiers = new ArrayList<>();
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void initVerifiers() {
        if (this.timestampToleranceInSeconds > 0) {
            // C. It's timing can't be too far off.
            this.requestEnvelopeVerifiers.add(new SpeechletRequestVerifierWrapper(
                    new TimestampSpeechletRequestVerifier(this.timestampToleranceInSeconds, TimeUnit.SECONDS)));
        }
        if (!this.isEmptyProperty(this.whitelistedApplicationIds)) {
            // D. It must contain a whitelisted application id.
            this.requestEnvelopeVerifiers.add(new ApplicationIdSpeechletRequestEnvelopeVerifier(this.whitelistedApplicationIds));
        }
        if (!this.isEmptyProperty(this.whitelistedUserIds)) {
            // E. It must contain a whitelisted user id.
            this.requestEnvelopeVerifiers.add(new UserIdSpeechletRequestEnvelopeVerifier(this.whitelistedUserIds));
        }
    }

    private boolean isEmptyProperty(final Set<String> set) {
        return set.isEmpty() || set.size() == 1 && set.iterator().next().equals("");
    }

    @Override
    // A. It must be a POST request.
    protected void doPost(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws ServletException, IOException {
        byte[] serializedSpeechletRequest = IOUtils.toByteArray(httpRequest.getInputStream());

        try {
            if (this.checkAmazonSignature) {
                // B. It must come from the Amazon Alexa cloud.
                SpeechletRequestSignatureVerifier.checkRequestSignature(serializedSpeechletRequest,
                        httpRequest.getHeader(Sdk.SIGNATURE_REQUEST_HEADER),
                        httpRequest.getHeader(Sdk.SIGNATURE_CERTIFICATE_CHAIN_URL_REQUEST_HEADER));
            }

            final SpeechletRequestEnvelope<?> requestEnvelope = SpeechletRequestEnvelope.fromJson(serializedSpeechletRequest);

            for (SpeechletRequestEnvelopeVerifier verifier : this.requestEnvelopeVerifiers) {
                if (!verifier.verify(requestEnvelope)) {
                    throw new SpeechletRequestHandlerException(this.createExceptionMessage(verifier, requestEnvelope));
                }
            }

            ResponseEntity<SpeechletResponseEnvelope> speechletResponse = this.restTemplate.postForEntity(this.endpoint, requestEnvelope,
                    SpeechletResponseEnvelope.class);
            if (speechletResponse.getStatusCode().is2xxSuccessful() && speechletResponse.hasBody()) {
                byte[] outputBytes = speechletResponse.getBody().toJsonBytes();
                httpResponse.setContentType("application/json");
                httpResponse.setStatus(speechletResponse.getStatusCodeValue());
                httpResponse.setContentLength(outputBytes.length);
                httpResponse.getOutputStream().write(outputBytes);
            } else {
                // Should never happen, cause all edge cases are already covered by actual exceptions thrown.
                httpResponse.sendError(speechletResponse.getStatusCodeValue(), "Unexpected error in proxy");
            }
        } catch (Exception e) {
            int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            if (BAD_REQUEST_EXCEPTION_TYPES.contains(e.getClass())) {
                statusCode = HttpServletResponse.SC_BAD_REQUEST;
            }
            this.log.error("Exception occurred in doPost, returning status code {}", statusCode, e);
            httpResponse.sendError(statusCode, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String createExceptionMessage(final SpeechletRequestEnvelopeVerifier verifier, final SpeechletRequestEnvelope<?> requestEnvelope) {
        SpeechletRequest request = requestEnvelope.getRequest();
        String requestId = request != null ? request.getRequestId() : "null";
        String verifierName = verifier.getClass().getSimpleName();
        if (verifier.getClass().equals(SpeechletRequestVerifierWrapper.class)) {
            // Currently the only wrapped verifier is this one. Unfortunately, the underlying verifier is not accessible from the wrapper.
            verifierName = "TimestampSpeechletRequestVerifier";
        }
        return String.format("Could not validate SpeechletRequest %s using verifier %s, rejecting request", requestId, verifierName);
    }

}
