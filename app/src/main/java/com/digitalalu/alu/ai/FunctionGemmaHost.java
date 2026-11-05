package com.digitalalu.alu.ai;

import android.content.Context;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;

import java.io.File;

/**
 * v1.1 — FunctionGemma (Gemma 3 270M) tier host — REAL runtime.
 *
 * Runs the downloaded model.litertlm (~283 MB, INT8) through Google's
 * LiteRT-LM engine on the CPU backend. The model is a tool-calling
 * specialist: given function declarations it emits
 *   <start_function_call>call:NAME{...}<end_function_call>
 * which {@link FunctionGemmaProtocol} parses into the shared ToolCall.
 *
 * Lifecycle: the Engine is loaded once and reused across turns (initialize()
 * is the expensive part); each turn gets a fresh Conversation with the
 * skill-filtered system prompt, then closes it.
 *
 * Why CPU backend: the 270M INT8 model is small enough that CPU is fast
 * (~sub-second TTFT class devices) and universally compatible; GPU/NPU
 * backends need per-device tuning we can't verify from here.
 */
public class FunctionGemmaHost {

    private static final FunctionGemmaHost INSTANCE = new FunctionGemmaHost();
    public static FunctionGemmaHost get() { return INSTANCE; }

    private FunctionGemmaHost() { }

    private Engine engine;
    private String loadedPath;
    private volatile boolean loadFailed;

    /** True when the LiteRT-LM runtime classes are bundled in the APK. */
    public boolean available() {
        try {
            Class.forName("com.google.ai.edge.litertlm.Engine");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the runtime is bundled AND the model file is fully downloaded. */
    public boolean isReady(Context ctx) {
        if (!available() || loadFailed) return false;
        ModelCatalog.ModelSpec m = ModelCatalog.functionGemma();
        return m != null && ModelStore.isInstalled(ctx, m);
    }

    /**
     * Loads (once) the engine for the downloaded model.
     * @return false when the model is missing or the engine cannot start —
     *         caller falls through to the MNN tier.
     */
    public synchronized boolean ensureLoaded(Context ctx) {
        ModelCatalog.ModelSpec m = ModelCatalog.functionGemma();
        if (m == null || !available() || loadFailed) return false;
        File f = ModelStore.fileFor(ctx, m);
        if (f == null || !f.exists() || !ModelStore.sizeMatches(f.length(), m.sizeBytes)) {
            return false;
        }
        if (engine != null && f.getAbsolutePath().equals(loadedPath)) return true;
        closeQuietly();
        try {
            EngineConfig cfg = new EngineConfig(
                    f.getAbsolutePath(),
                    new Backend.CPU(null, null),
                    null, null, null, null,
                    ctx.getCacheDir().getAbsolutePath());
            engine = new Engine(cfg);
            engine.initialize();
            loadedPath = f.getAbsolutePath();
            return true;
        } catch (Throwable t) {
            // A device that cannot run the engine should not retry every turn.
            loadFailed = true;
            closeQuietly();
            return false;
        }
    }

    /**
     * One FunctionGemma turn: declarations (system) + user line in, raw model
     * text out (parsed by FunctionGemmaProtocol on the caller side).
     * @return null when the tier cannot run — caller falls through to MNN.
     */
    public synchronized String generate(Context ctx, String systemPrompt,
                                        String userPrompt, int maxTokens) {
        if (!ensureLoaded(ctx) || engine == null) return null;
        Conversation conv = null;
        try {
            conv = engine.createConversation(
                    new ConversationConfig(Contents.Companion.of(systemPrompt)));
            Message reply = conv.sendMessage(userPrompt,
                    null, null, null, null,
                    maxTokens > 0 ? Integer.valueOf(maxTokens) : null,
                    null, null);
            if (reply == null || reply.getContents() == null) return "";
            StringBuilder sb = new StringBuilder();
            for (Content c : reply.getContents().getContents()) {
                if (c instanceof Content.Text) sb.append(((Content.Text) c).getText());
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (conv != null) try { conv.close(); } catch (Throwable ignored) { }
        }
    }

    /** Frees the engine (e.g. user deletes the model or switches tiers). */
    public synchronized void close() {
        closeQuietly();
        loadFailed = false;
    }

    private void closeQuietly() {
        if (engine != null) try { engine.close(); } catch (Throwable ignored) { }
        engine = null;
        loadedPath = null;
    }
}
