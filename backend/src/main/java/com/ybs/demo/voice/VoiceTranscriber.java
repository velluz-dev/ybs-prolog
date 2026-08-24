package com.ybs.demo.voice;

/**
 * Speech-to-text for a Burmese query.
 *
 * <p>This is the one interface in the project. It exists because the implementation is
 * genuinely swapped: the demo runs on {@link RecordedTranscriber} with no network, while
 * {@link WhisperHttpTranscriber} talks to a real self-hosted Whisper endpoint. Everywhere
 * else the code uses concrete classes.
 */
public interface VoiceTranscriber {

    /**
     * @param sampleKey which of the pre-recorded demo queries to transcribe
     * @return the Burmese transcript
     */
    String transcribe(String sampleKey);

    /** Shown in the UI so it is always clear which path produced the transcript. */
    String modeName();
}
