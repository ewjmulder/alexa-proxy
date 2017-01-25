package com.programyourhome.alexa.proxy;

import java.util.Set;

import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.speechlet.verifier.SpeechletRequestEnvelopeVerifier;

public class UserIdSpeechletRequestEnvelopeVerifier implements SpeechletRequestEnvelopeVerifier {

    private final Set<String> whitelistedUserIds;

    public UserIdSpeechletRequestEnvelopeVerifier(final Set<String> whitelistedUserIds) {
        this.whitelistedUserIds = whitelistedUserIds;
    }

    @Override
    public boolean verify(final SpeechletRequestEnvelope<?> speechletRequestEnvelope) {
        return this.whitelistedUserIds.contains(speechletRequestEnvelope.getSession().getUser().getUserId());
    }

}
