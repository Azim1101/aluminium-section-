package com.digitalalu.alu.ai;

import android.content.Context;

import java.io.File;

/**
 * Owns the single loaded MNN model.
 *
 * This is the one place where a mistake turns into a native crash, so everything
 * is defensive: validate the file, check RAM, catch Throwable (including
 * OutOfMemoryError) before MNN is allowed to touch anything.
 */
public class LocalModelHost {

    /** Below this much *available* RAM we refuse rather than risk an OOM kill. */
    private static final long MIN_AVAIL_BYTES = 300L * 1048576L;

    private static LocalModelHost INSTANCE;

    private final LocalLlm llm = new LocalLlm();
    private volatile int state = AiReadiness.STATE_NONE;
    private volatile String status = "koi model load nahi hai";
    private volatile String lastError = "";
    private volatile String loadedPath = "";
    private volatile long loadedAt = 0L;
    private volatile long loadMs = 0L;

    public static synchronized LocalModelHost get() {
        if (INSTANCE == null) INSTANCE = new LocalModelHost();
        return INSTANCE;
    }

    public boolean isReady() { return state == AiReadiness.STATE_READY && llm.isLoaded(); }
    public int state() { return state; }
    public String status() { return status; }
    public String lastError() { return lastError; }
    public long loadedAt() { return loadedAt; }
    public long loadMs() { return loadMs; }
    public LocalLlm llm() { return llm; }

    public boolean runtimeAvailable() { return LocalLlm.available(); }

    /* ============================ guards ============================== */

    public static class Guard {
        public final boolean ok;
        public final String reason;
        Guard(boolean ok, String reason) { this.ok = ok; this.reason = reason; }
        public static Guard no(String r) { return new Guard(false, r); }
        public static Guard yes() { return new Guard(true, ""); }
    }

    /**
     * Pure decision function — unit tested. Refuses loads that would very likely
     * end in an OOM kill, and refuses files that are not complete model folders.
     *
     * @param minRamBytes the catalog's declared minimum for this model, or 0 when
     *                    unknown (then a size-based heuristic is used instead).
     */
    public static Guard canLoad(long totalRam, long availRam, long modelBytes,
                               long fileLen, long minRamBytes) {
        if (fileLen <= 0) return Guard.no("model file mili nahi — pehle download karein");
        if (modelBytes > 0 && fileLen < modelBytes * 0.98) {
            return Guard.no("download adhura hai (" + ModelStore.humanBytes(fileLen)
                    + " / " + ModelStore.humanBytes(modelBytes) + ")");
        }
        if (modelBytes > 0) {
            // The catalog's declared minimum is the source of truth, so the app can
            // never recommend a model it would then refuse to load. When the model is
            // unknown we fall back to a heuristic: the phone needs roughly twice the
            // model size, because the OS, the app and MNN's working buffers all
            // live alongside the weights.
            long need = minRamBytes > 0 ? minRamBytes : (modelBytes * 2 + 400L * 1048576L);
            if (totalRam > 0 && totalRam < need) {
                return Guard.no("is phone me itni RAM nahi (model "
                        + ModelStore.humanBytes(modelBytes) + " ko kam se kam "
                        + ModelStore.humanBytes(need) + " chahiye, phone me "
                        + ModelStore.humanBytes(totalRam) + ") — chhota model choose karein");
            }
            if (availRam > 0 && availRam < MIN_AVAIL_BYTES) {
                return Guard.no("abhi free RAM kam hai (" + ModelStore.humanBytes(availRam)
                        + ") — kuch apps band karke dobara try karein");
            }
        }
        return Guard.yes();
    }

    /** Convenience overload for callers that don't know the catalog minimum. */
    public static Guard canLoad(long totalRam, long availRam, long modelBytes, long fileLen) {
        return canLoad(totalRam, availRam, modelBytes, fileLen, 0L);
    }

    /** Non-blocking warning for a load that will probably work but be slow. */
    public static String warnFreeRam(long availRam, long modelBytes) {
        if (modelBytes <= 0 || availRam <= 0) return "";
        if (availRam < MIN_AVAIL_BYTES) {
            return "free RAM bahut kam hai (" + ModelStore.humanBytes(availRam) + ")";
        }
        if (availRam < modelBytes) {
            return "free RAM model se kam hai (" + ModelStore.humanBytes(availRam)
                 + " vs " + ModelStore.humanBytes(modelBytes) + ") — load slow hoga";
        }
        return "";
    }

    /* =========================== lifecycle ============================ */

    public synchronized void ensureLoaded(final Context c, final AiSettings s) {
        try {
            if (!LocalLlm.available()) {
                state = AiReadiness.STATE_FAILED;
                lastError = "on-device runtime is build me nahi hai";
                status = lastError;
                return;
            }
            if (s == null || !s.localPermitted()) {
                state = AiReadiness.STATE_NONE;
                status = "on-device model OFF hai";
                return;
            }
            if (s.localModelPath == null || s.localModelPath.length() == 0) {
                state = AiReadiness.STATE_NONE;
                status = "AI Setup me model download karein";
                return;
            }
            if (state == AiReadiness.STATE_LOADING) return;
            if (state == AiReadiness.STATE_READY && loadedPath.equals(s.localModelPath)) return;

            // ---- validate the file BEFORE the native loader sees it ----
            ModelCatalog.ModelSpec spec = ModelCatalog.byFileName(fileName(s.localModelPath));
            File f = new File(s.localModelPath);
            if (!f.exists()) {
                state = AiReadiness.STATE_FAILED;
                lastError = "model file mili nahi: " + f.getName();
                status = lastError;
                return;
            }
            if (spec != null) {
                String problem = ModelStore.verify(c, spec);
                if (problem != null && problem.length() > 0) {
                    state = AiReadiness.STATE_FAILED;
                    lastError = problem;
                    status = "model theek nahi: " + problem;
                    return;
                }
            }

            // ---- RAM guard (prevents the OOM-kill crash) ----
            DeviceProfile dp = DeviceProfile.probe(c);
            long modelBytes = spec != null ? spec.sizeBytes : f.length();
            Guard g = canLoad(dp.totalRam, dp.availRam, modelBytes, modelBytes,
                    spec != null ? spec.minRamBytes : 0L);
            if (!g.ok) {
                state = AiReadiness.STATE_FAILED;
                lastError = g.reason;
                status = g.reason;
                return;
            }

            final int threads = s.effectiveThreads(dp.cores);
            final int ctx = s.localCtx > 0 ? s.localCtx : 2048;
            final String path = s.localModelPath;
            final String warn = warnFreeRam(dp.availRam, modelBytes);

            state = AiReadiness.STATE_LOADING;
            lastError = "";
            loadedPath = path;
            status = "model load ho raha hai… (" + shortName(path) + ")";

            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    long t0 = System.currentTimeMillis();
                    boolean ok = false;
                    try {
                        ok = llm.load(path, threads, ctx, 256);
                    } catch (OutOfMemoryError oom) {
                        lastError = "phone ki memory khatam ho gayi — chhota model try karein";
                    } catch (Throwable th) {
                        lastError = th.getClass().getSimpleName()
                                + (th.getMessage() == null ? "" : ": " + th.getMessage());
                    }
                    if (ok) {
                        // Warm-up: prefill the static system prompt (rules + tool list,
                        // ~1000 tokens ≈ 40-60 s on a budget phone) into the KV cache NOW,
                        // so the first real question only pays for its own ~60 tokens.
                        try {
                            status = "prompt taiyaar ho raha hai…";
                            llm.generate(IntentProtocol.localSystem(ToolRegistry.promptList()),
                                    IntentProtocol.localUser("warmup", ""), 1, 0f, 0f, null, null);
                        } catch (Throwable ignored) { }
                    }
                    loadMs = System.currentTimeMillis() - t0;
                    if (ok) {
                        state = AiReadiness.STATE_READY;
                        loadedAt = System.currentTimeMillis();
                        lastError = "";
                        status = shortName(path) + " ready (" + (loadMs / 1000) + "s, "
                                + threads + " threads)" + (warn.length() > 0 ? " · " + warn : "");
                    } else {
                        state = AiReadiness.STATE_FAILED;
                        if (lastError == null || lastError.length() == 0) {
                            lastError = "model load nahi ho paya (file ya RAM ki dikkat)";
                        }
                        status = lastError;
                    }
                }
            }, "alu-model-load");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        } catch (Throwable th) {
            state = AiReadiness.STATE_FAILED;
            lastError = th.getClass().getSimpleName();
            status = "model load start nahi ho paya";
        }
    }

    public synchronized void unload() {
        try { llm.close(); } catch (Throwable ignored) { }
        state = AiReadiness.STATE_NONE;
        status = "unloaded";
        loadedPath = "";
        loadedAt = 0L;
    }

    public static String fileName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    public static String shortName(String path) {
        String n = fileName(path);
        if (n.equals("config.json")) {
            // models/<id>/config.json -> catalog title
            try {
                String[] parts = path.split("/");
                ModelCatalog.ModelSpec m = parts.length >= 2 ? ModelCatalog.byId(parts[parts.length - 2]) : null;
                if (m != null) n = m.title;
            } catch (Throwable ignored) { }
        }
        if (n.endsWith(".gguf")) n = n.substring(0, n.length() - 5);
        return n.length() > 34 ? n.substring(0, 31) + "…" : n;
    }

    /** What the chat header should show, e.g. "On-Device AI · 0.6B Hindi". */
    public String shortLabel() {
        if (state == AiReadiness.STATE_READY) return "On-Device AI";
        if (state == AiReadiness.STATE_LOADING) return "model load ho raha hai…";
        if (state == AiReadiness.STATE_FAILED) return "model load fail";
        return "on-device model off";
    }
}
