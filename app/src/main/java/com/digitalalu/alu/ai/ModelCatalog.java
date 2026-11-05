package com.digitalalu.alu.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Curated, verified on-device model list (Alibaba MNN runtime, Qwen3.5 family).
 * URLs and byte sizes checked against the Hugging Face API on 2026-10-06.
 */
public class ModelCatalog {

    public static final String TIER_06B = "0.6B";
    public static final String TIER_17B = "1.7B";
    public static final String TIER_4B  = "4B";

    /** One file of a (multi-file) MNN model. */
    public static class ModelFile {
        public final String name;
        public final long sizeBytes;
        public ModelFile(String name, long sizeBytes) { this.name = name; this.sizeBytes = sizeBytes; }
    }

    public static class ModelSpec {
        /** Hugging Face repo id, e.g. "taobao-mnn/Qwen3.5-0.8B-MNN". */
        public String repo = "";
        /** All files that must be present; fileName (config.json) is the entry point. */
        public ModelFile[] files = new ModelFile[0];
        public String urlFor(ModelFile f) {
            return "https://huggingface.co/" + repo + "/resolve/main/" + f.name;
        }
        public final String id;
        public final String title;
        public final String tier;
        public final String fileName;
        public final String url;
        public final long sizeBytes;
        public final long minRamBytes;
        public final boolean hindi;
        public final String note;
        /** v1.0: "mnn" (default) or "litertlm" — which runtime loads this model. */
        public String runtime = "mnn";

        public ModelSpec(String id, String title, String tier, String fileName, String url,
                         long sizeBytes, long minRamBytes, boolean hindi, String note) {
            this.id = id;
            this.title = title;
            this.tier = tier;
            this.fileName = fileName;
            this.url = url;
            this.sizeBytes = sizeBytes;
            this.minRamBytes = minRamBytes;
            this.hindi = hindi;
            this.note = note;
        }

        public String sizeText() {
            return String.format(java.util.Locale.US, "%.0f MB", sizeBytes / 1048576.0);
        }
    }

    private static final long MB = 1048576L;
    private static final long GB = 1073741824L;

    /** v2.6: ONE model only. Official Qwen3-0.6B, Q4_K_M — smallest that reliably
     *  understands Hinglish + picks the right tool (tested 2026-10-07). */
    private static final ModelSpec[] ALL = new ModelSpec[] { mnnQwen35Claude() };

    /**
     * v2.8: Alibaba MNN model — exactly the one that runs in the MNN Chat app:
     * taobao-mnn/Qwen3.5-0.8B-Claude-4.6-Opus-Reasoning-Distilled-MNN (4-bit).
     * File sizes from the Hugging Face API, 2026-10-07.
     */
    private static ModelSpec mnnQwen35Claude() {
        ModelFile[] files = new ModelFile[] {
            new ModelFile("config.json", 652L),
            new ModelFile("llm_config.json", 4900L),
            new ModelFile("llm.mnn", 2148136L),
            new ModelFile("llm.mnn.json", 5343877L),
            new ModelFile("llm.mnn.weight", 470382614L),
            new ModelFile("tokenizer.txt", 2955203L),
            new ModelFile("visual.mnn", 251528L),
            new ModelFile("visual.mnn.weight", 63043212L),
        };
        long total = 0; for (ModelFile f : files) total += f.sizeBytes;
        ModelSpec m = new ModelSpec(
            "mnn_qwen35_08b_claude", "Qwen3.5 0.8B Claude-Distilled (MNN 4-bit)", TIER_06B,
            "config.json",
            "https://huggingface.co/taobao-mnn/Qwen3.5-0.8B-Claude-4.6-Opus-Reasoning-Distilled-MNN",
            total, 2 * GB, true,
            "Alibaba MNN format — wahi model jo MNN Chat app me fast chalta hai. "
          + "~520 MB (8 files), ek baar download, phir hamesha offline.");
        m.repo = "taobao-mnn/Qwen3.5-0.8B-Claude-4.6-Opus-Reasoning-Distilled-MNN";
        m.files = files;
        return m;
    }


    /**
     * v1.0 — FunctionGemma 270M (Gemma 3 derivative, function-calling tuned,
     * ~283 MB, LiteRT-LM format). Only listed when the LiteRT-LM runtime is
     * actually bundled in the APK — otherwise downloading it would produce a
     * model the app cannot run. See FunctionGemmaHost.
     */
    private static ModelSpec fgemma() {
        // Verified 2026-11-02 against the Hugging Face API:
        //   repo soniqo/FunctionGemma-270M-LiteRT-LM, file model.litertlm,
        //   x-linked-size = 297,212,528 bytes (~283 MB).
        long total = 297212528L;
        ModelSpec m = new ModelSpec(
            "fgemma_270m_litertlm", "FunctionGemma 270M (tool-calling)", TIER_06B,
            "model.litertlm",
            "https://huggingface.co/soniqo/FunctionGemma-270M-LiteRT-LM",
            total, 3 * GB, false,
            "Google ka tool-calling specialist 270M model — ~283 MB (MNN model se aadha). "
          + "Tool choose karne me tez aur pakka; jawab phrasing rules tier karta hai. "
          + "Download ke baad offline chalta hai.");
        m.repo = "soniqo/FunctionGemma-270M-LiteRT-LM";
        m.files = new ModelFile[] { new ModelFile("model.litertlm", total) };
        m.runtime = "litertlm";
        return m;
    }

    /** @return the FunctionGemma spec (may be absent from list() until the runtime is bundled). */
    public static ModelSpec functionGemma() { return FGEMMA; }
    private static final ModelSpec FGEMMA = fgemma();

    /** The one and only model the app uses. */
    public static ModelSpec DEFAULT() { return ALL[0]; }

    public static List<ModelSpec> list() {
        List<ModelSpec> out = new ArrayList<ModelSpec>();
        for (ModelSpec m : ALL) out.add(m);
        if (FunctionGemmaHost.get().available()) out.add(FGEMMA);
        return out;
    }

    public static ModelSpec byId(String id) {
        if (id == null) return null;
        for (ModelSpec m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    public static ModelSpec byFileName(String fileName) {
        if (fileName == null) return null;
        for (ModelSpec m : ALL) if (m.fileName.equals(fileName)) return m;
        return null;
    }

    /** Biggest model the RAM can hold, keeping ~1.2 GB headroom for Android. */
    public static ModelSpec recommend(long totalRam) {
        ModelSpec best = null;
        for (ModelSpec m : ALL) {
            if (totalRam >= m.minRamBytes + (long) (1.2 * GB)) {
                if (best == null || m.minRamBytes > best.minRamBytes) best = m;
            }
        }
        if (best != null) return best;
        ModelSpec smallest = null;
        for (ModelSpec m : ALL) {
            if (smallest == null || m.sizeBytes < smallest.sizeBytes) smallest = m;
        }
        return smallest;
    }

    /**
     * "What should I download" list: fits-first. Never empty — a small phone still
     * needs to see its options (with the min-RAM warning), otherwise the screen
     * would look broken.
     */
    public static List<ModelSpec> rankedFor(long totalRam) {
        List<ModelSpec> out = new ArrayList<ModelSpec>();
        for (ModelSpec m : ALL) {
            if (totalRam >= m.minRamBytes + (long) (1.2 * GB)) out.add(m);
        }
        if (out.isEmpty()) out.addAll(list());
        out.sort((a, b) -> Long.compare(a.minRamBytes, b.minRamBytes));
        ModelSpec rec = recommend(totalRam);
        if (rec != null) {
            out.remove(rec);
            out.add(0, rec);
        }
        return out;
    }
}
