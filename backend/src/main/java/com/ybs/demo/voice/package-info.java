/**
 * <b>Feature: Burmese voice query.</b>
 *
 * <p><b>AI topic: NLP / speech.</b> audio to transcript to entities to a journey search.
 *
 * <p>{@link com.ybs.demo.voice.VoiceTranscriber} is the only interface in the project, and
 * it earns its place: the offline recorded path is the demo default, while the Whisper
 * client talks to a real self-hosted endpoint and falls back if it is down.
 *
 * <p>{@link com.ybs.demo.voice.EntityExtractor} is string matching on the postpositions
 * {@code မှ} (from) and {@code ကို} (to), not a language model - and the UI shows the parse
 * so nobody has to take that on trust.
 */
package com.ybs.demo.voice;
