package com.digitalalu.alu.agent;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * Lightweight ONNX Runtime inference engine for ALU AI Agent.
 * Runs an ultra-compact neural intent classifier (~268 KB, opset 13)
 * trained on Hinglish and English aluminium fabrication queries.
 *
 * Runs 100% offline on device with minimal memory footprint (<1ms inference).
 */
public class OnnxAgentClassifier implements AutoCloseable {

    private static final String TAG = "OnnxAgentClassifier";
    private static final String MODEL_FILE = "agent_model.onnx";
    private static final String VOCAB_FILE = "agent_vocab.json";

    private final Map<String, Integer> vocabMap = new HashMap<>();
    private String[] intents = new String[0];
    private int vocabSize = 1024;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean isReady = false;

    public static class Prediction {
        public final String intent;
        public final float confidence;

        public Prediction(String intent, float confidence) {
            this.intent = intent;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return "Prediction{" + intent + " (" + String.format("%.1f%%", confidence * 100f) + ")}";
        }
    }

    public OnnxAgentClassifier(Context context) {
        try {
            boolean vocabLoaded = loadVocab(context);
            if (!vocabLoaded) {
                Log.w(TAG, "Vocabulary could not be loaded");
                return;
            }
            byte[] modelBytes = loadModelBytes(context);
            if (modelBytes == null || modelBytes.length == 0) {
                Log.w(TAG, "Model file could not be read");
                return;
            }

            initOnnx(modelBytes);
            Log.i(TAG, "ONNX Agent Model successfully loaded (" + intents.length + " intents, vocab " + vocabSize + ")");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to initialize ONNX runtime classifier: " + t.getMessage());
            close();
        }
    }

    private void initOnnx(byte[] modelBytes) throws Throwable {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = env.createSession(modelBytes, options);
        isReady = true;
    }

    /**
     * Vectorizes query text and runs ONNX neural classification.
     * Returns highest probability intent and confidence (0.0 - 1.0),
     * or null if inference cannot be completed.
     */
    public Prediction predict(String query) {
        if (!isReady || session == null || env == null) return null;
        if (query == null || query.trim().isEmpty()) return null;

        try {
            float[] vec = vectorize(query);
            float[][] inputData = new float[1][vocabSize];
            System.arraycopy(vec, 0, inputData[0], 0, vocabSize);

            OnnxTensor inputTensor = OnnxTensor.createTensor(env, inputData);
            try {
                OrtSession.Result result = session.run(Collections.singletonMap("features", inputTensor));
                try {
                    float[][] probs = (float[][]) result.get(0).getValue();
                    if (probs != null && probs.length > 0 && probs[0].length > 0) {
                        int bestIdx = 0;
                        float maxProb = probs[0][0];
                        for (int i = 1; i < probs[0].length; i++) {
                            if (probs[0][i] > maxProb) {
                                maxProb = probs[0][i];
                                bestIdx = i;
                            }
                        }
                        if (bestIdx >= 0 && bestIdx < intents.length) {
                            return new Prediction(intents[bestIdx], maxProb);
                        }
                    }
                } finally {
                    result.close();
                }
            } finally {
                inputTensor.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Inference error: " + t.getMessage());
            return null;
        }
        return null;
    }

    /**
     * Convert text into a normalized multi-feature bag-of-tokens & character trigram vector.
     */
    public float[] vectorize(String text) {
        float[] vec = new float[vocabSize];
        if (text == null) return vec;

        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
        if (cleaned.isEmpty()) return vec;

        String[] words = cleaned.split("\\s+");
        for (String w : words) {
            if (w.isEmpty()) continue;
            Integer wordIdx = vocabMap.get(w);
            if (wordIdx != null && wordIdx < vocabSize) {
                vec[wordIdx] += 2.0f; // Whole word presence
            }
            if (w.length() >= 3) {
                for (int i = 0; i <= w.length() - 3; i++) {
                    String tri = w.substring(i, i + 3);
                    Integer triIdx = vocabMap.get(tri);
                    if (triIdx != null && triIdx < vocabSize) {
                        vec[triIdx] += 0.5f; // Character trigram presence
                    }
                }
            }
        }

        // L2 Normalization
        double sumSq = 0;
        for (float v : vec) sumSq += v * v;
        if (sumSq > 0) {
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < vec.length; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }

    public boolean isReady() {
        return isReady;
    }

    public int getVocabSize() {
        return vocabSize;
    }

    public String[] getIntents() {
        return intents;
    }

    private boolean loadVocab(Context context) {
        try {
            InputStream is = openAssetStream(context, VOCAB_FILE);
            if (is == null) return false;

            String jsonStr = readFully(is);
            JSONObject root = new JSONObject(jsonStr);
            this.vocabSize = root.optInt("vocab_size", 1024);

            JSONObject tokensObj = root.getJSONObject("tokens");
            vocabMap.clear();
            Iterator<String> keys = tokensObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                vocabMap.put(k, tokensObj.getInt(k));
            }

            JSONArray intentsArr = root.getJSONArray("intents");
            intents = new String[intentsArr.length()];
            for (int i = 0; i < intentsArr.length(); i++) {
                intents[i] = intentsArr.getString(i);
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Error loading vocab: " + t.getMessage());
            return false;
        }
    }

    private byte[] loadModelBytes(Context context) {
        try {
            InputStream is = openAssetStream(context, MODEL_FILE);
            if (is == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Throwable t) {
            Log.w(TAG, "Error loading model bytes: " + t.getMessage());
            return null;
        }
    }

    private InputStream openAssetStream(Context context, String fileName) {
        // 1. Try Android Context assets
        if (context != null) {
            try {
                return context.getAssets().open(fileName);
            } catch (Throwable ignored) {}
        }

        // 2. Try classpath / resource stream
        InputStream stream = getClass().getResourceAsStream("/assets/" + fileName);
        if (stream != null) return stream;
        stream = getClass().getResourceAsStream("/" + fileName);
        if (stream != null) return stream;

        // 3. Try direct file lookup (e.g. during local tests)
        File[] candidates = new File[]{
            new File("app/src/main/assets/" + fileName),
            new File("src/main/assets/" + fileName),
            new File(fileName)
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                try {
                    return new FileInputStream(candidate);
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    private String readFully(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        isReady = false;
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Throwable ignored) {}
    }
}
