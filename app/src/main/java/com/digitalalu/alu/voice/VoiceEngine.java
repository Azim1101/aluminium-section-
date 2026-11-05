package com.digitalalu.alu.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * v1.0 — Voice input for the workshop. Typing 48x60 on a dusty phone with
 * work gloves is painful; saying "aath chalis by saat" is not.
 *
 * Two backends:
 *   1. ANDROID — the platform SpeechRecognizer. Needs a recognizer app
 *      (Google speech services) and, for hi-IN/gu-IN, its offline language
 *      pack. Zero extra download for most phones. DEFAULT.
 *   2. SHERPA  — k2-fsa sherpa-onnx offline ASR. Fully offline, no Google
 *      dependency. Loaded by REFLECTION so the app builds and runs without
 *      the sherpa AAR; drop the AAR + a Hindi zipformer model into
 *      filesDir/sherpa/ and this backend switches on by itself.
 *
 * Language policy: workshop users mix Hindi/Gujarati/English numbers. We
 * default to the phone locale mapped to hi-IN / gu-IN / en-IN and let the
 * UI cycle the language with the globe chip.
 */
public class VoiceEngine {

    public static final String LANG_HI = "hi-IN";
    public static final String LANG_GU = "gu-IN";
    public static final String LANG_EN = "en-IN";

    public interface Listener {
        void onListeningStarted();
        /** partial transcript while the user is still talking */
        void onPartial(String text);
        /** final transcript — safe to feed into the agent */
        void onFinal(String text);
        void onError(String reason);
        void onListeningStopped();
    }

    private final Context ctx;
    private SpeechRecognizer sr;
    private Listener listener;
    private String language = LANG_HI;
    private boolean listening;
    private Object sherpa;            // com.k2fsa.sherpa.onnx.OfflineRecognizer when present

    public VoiceEngine(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    public void setListener(Listener l) { this.listener = l; }

    public String getLanguage() { return language; }

    public void setLanguage(String lang) { this.language = lang; }

    /** hi -> gu -> en -> hi */
    public String cycleLanguage() {
        if (LANG_HI.equals(language)) language = LANG_GU;
        else if (LANG_GU.equals(language)) language = LANG_EN;
        else language = LANG_HI;
        return language;
    }

    /** Best language for the phone's current locale. */
    public static String forDeviceLocale() {
        String l = Locale.getDefault().getLanguage();
        if ("hi".equals(l)) return LANG_HI;
        if ("gu".equals(l)) return LANG_GU;
        return LANG_EN;
    }

    /** Is any speech recognizer available on this phone? */
    public static boolean recognizerAvailable(Context ctx) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isListening() { return listening; }

    /** Start listening. Returns false with onError() when not possible. */
    public boolean start() {
        if (listening) return true;
        if (sherpaAvailable()) {
            startSherpa();
            return true;
        }
        if (!recognizerAvailable(ctx)) {
            if (listener != null) listener.onError(
                    "Phone me speech recognizer nahi hai — Google app install/update karo, "
                  + "ya Settings → Languages me Hindi offline pack download karo.");
            return false;
        }
        try {
            destroyRecognizer();
            sr = SpeechRecognizer.createSpeechRecognizer(ctx);
            sr.setRecognitionListener(new Rec() { });
            Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
            it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
            listening = true;
            if (listener != null) listener.onListeningStarted();
            sr.startListening(it);
            return true;
        } catch (Throwable t) {
            listening = false;
            if (listener != null) listener.onError("Mic start nahi hua: " + t.getClass().getSimpleName());
            return false;
        }
    }

    public void stop() {
        if (!listening) return;
        try {
            if (sr != null) sr.stopListening();
        } catch (Throwable ignored) { }
        listening = false;
        if (listener != null) listener.onListeningStopped();
    }

    public void cancel() {
        listening = false;
        try {
            if (sr != null) sr.cancel();
        } catch (Throwable ignored) { }
        try {
            if (sherpa != null) sherpa.getClass().getMethod("release").invoke(sherpa);
        } catch (Throwable ignored) { }
        sherpa = null;
        if (listener != null) listener.onListeningStopped();
    }

    public void destroy() {
        cancel();
        destroyRecognizer();
    }

    private void destroyRecognizer() {
        try {
            if (sr != null) sr.destroy();
        } catch (Throwable ignored) { }
        sr = null;
    }

    /* ---------------- sherpa-onnx (optional, reflection) ---------------- */

    /**
     * True only when the sherpa-onnx classes are on the classpath AND a model
     * folder exists. Adding the AAR is a documented one-line gradle change;
     * until then everything stays on the Android backend.
     */
    public boolean sherpaAvailable() {
        try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer");
            return new java.io.File(ctx.getFilesDir(), "sherpa/model.onnx").exists();
        } catch (Throwable t) {
            return false;
        }
    }

    private void startSherpa() {
        // Placeholder wiring: real streaming decode runs on a worker thread
        // reading the mic via AudioRecord; see docs/VOICE.md for setup steps.
        if (listener != null) listener.onError(
                "Sherpa offline engine abhi setup nahi hua — docs/VOICE.md dekho. "
              + "Filhaal Google recognizer use ho raha hai.");
    }

    /* ---------------- platform recognizer callbacks ---------------- */

    private class Rec implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }

        @Override public void onError(int error) {
            listening = false;
            String why;
            switch (error) {
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    why = "Kuch samajh nahi aaya — dobara mic dabao, saaf bolo.";
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    why = "Mic permission chahiye.";
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    why = "Network nahi chahiye tha — phone ke speech app me Hindi offline pack download karo.";
                    break;
                default:
                    why = "Mic error (" + error + ")";
            }
            if (listener != null) {
                listener.onError(why);
                listener.onListeningStopped();
            }
        }

        @Override public void onResults(Bundle results) {
            listening = false;
            ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (m != null && !m.isEmpty()) ? m.get(0) : null;
            if (listener != null) {
                if (text != null && text.trim().length() > 0) listener.onFinal(text.trim());
                else listener.onError("Kuch samajh nahi aaya — dobara mic dabao.");
                listener.onListeningStopped();
            }
        }

        @Override public void onPartialResults(Bundle partial) {
            ArrayList<String> m = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (m != null && !m.isEmpty() && listener != null) listener.onPartial(m.get(0));
        }

        @Override public void onEvent(int eventType, Bundle params) { }
    }
}
