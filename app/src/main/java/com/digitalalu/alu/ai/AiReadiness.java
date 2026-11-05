package com.digitalalu.alu.ai;

/**
 * "Ab user ko kya karna chahiye?" — single source of truth for the logical setup
 * order. Android-free on purpose (unit tested), so the UI and the chat can never
 * disagree about what is missing.
 *
 * Order enforced everywhere:
 *   1. phone check          (auto)
 *   2. pick a brain         (model download  OR  cloud key)
 *   3. use it               (load / test → live)
 */
public class AiReadiness {

    /* LocalModelHost lifecycle states (mirrored here to keep this class pure). */
    public static final int STATE_NONE = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_FAILED = 3;

    public static final String CODE_NEED_MODEL = "NEED_MODEL";
    public static final String CODE_DOWNLOADING = "DOWNLOADING";
    public static final String CODE_DOWNLOADED = "DOWNLOADED";
    public static final String CODE_LOADING = "LOADING";
    public static final String CODE_LOAD_FAILED = "LOAD_FAILED";
    public static final String CODE_MODEL_READY = "MODEL_READY";
    public static final String CODE_CLOUD_READY = "CLOUD_READY";
    public static final String CODE_READY = "READY";
    public static final String CODE_RULES_ONLY = "RULES_ONLY";
    public static final String CODE_RUNTIME_MISSING = "RUNTIME_MISSING";

    public static class Step {
        public String code = CODE_NEED_MODEL;
        public String title = "";
        public String detail = "";
        public String actionLabel = "";
        public String actionType = "";
        /** true = nothing for the user to do (either all set up, or intentionally off) */
        public boolean ready;

        public Step(String code, String title, String detail, String actionLabel, String actionType, boolean ready) {
            this.code = code;
            this.title = title;
            this.detail = detail;
            this.actionLabel = actionLabel;
            this.actionType = actionType;
            this.ready = ready;
        }

        public boolean hasAction() { return actionLabel != null && actionLabel.length() > 0; }
    }

    /**
     * @param mode             AiSettings.MODE_*
     * @param localPermitted   user has the on-device model switched ON
     * @param runtimeAvailable libaluai.so loaded for this ABI
     * @param modelConfigured  a model path is selected
     * @param modelDownloaded  that file is complete on disk
     * @param hostState        STATE_* above
     * @param cloudKeySet      api key present
     * @param capLeft          daily cloud cap not exhausted
     * @param downloading      a download is in flight
     */
    public static Step of(int mode, boolean localPermitted, boolean runtimeAvailable,
                          boolean modelConfigured, boolean modelDownloaded, int hostState,
                          boolean cloudKeySet, boolean capLeft, boolean downloading) {

        boolean cloudUsable = mode != AiSettings.MODE_OFFLINE_ONLY && cloudKeySet && capLeft;

        // ---- 0) the user turned everything off: that is a valid, quiet choice ---
        if (!localPermitted && !cloudUsable) {
            String detail = (mode == AiSettings.MODE_OFFLINE_ONLY)
                    ? "Internet band hai aur on-device model OFF hai — sirf app ke rules chalenge."
                    : "AI off hai — sirf app ke rules chalenge (window add, estimate, cutting plan).";
            return new Step(CODE_RULES_ONLY, "Rules-only mode (AI off)", detail,
                    "AI ON KARO", "AI_SETUP", true);
        }

        // ---- 1) cloud is enough to be usable; model is a bonus ---------------
        if (cloudUsable) {
            boolean modelBusy = localPermitted && modelConfigured
                    && (downloading || hostState == STATE_LOADING);
            if (hostState == STATE_READY && localPermitted && modelDownloaded) {
                return new Step(CODE_READY, "Agent ready",
                        "On-device model aur cloud dono tayyar hai.", "", "", true);
            }
            if (modelBusy) {
                return new Step(CODE_CLOUD_READY, "Cloud AI ready — model bhi tayyar ho raha hai",
                        "Agent abhi cloud se chal raha hai. Model khatam hone par internet ke bina bhi chalega.",
                        "AI SETUP", "AI_SETUP", true);
            }
            return new Step(CODE_CLOUD_READY, "Cloud AI ready",
                    "Cloud key set hai — agent abhi cloud se chalega. Model download karke internet ke "
                  + "bina bhi chala sakte hai.", "MODEL DOWNLOAD KARO", "DOWNLOAD_MODEL", true);
        }

        // ---- 2) no cloud: the on-device model is the only brain --------------
        if (!localPermitted) {
            return new Step(CODE_RULES_ONLY, "Rules-only mode (AI off)",
                    "AI off hai — sirf app ke rules chalenge.", "AI ON KARO", "AI_SETUP", true);
        }

        if (!runtimeAvailable) {
            return new Step(CODE_RUNTIME_MISSING, "Cloud key daalein",
                    "Is build me on-device runtime nahi hai, isliye agent sirf basic commands samajhta hai. "
                  + "Cloud AI key daal kar poora agent on kar sakte hai.",
                    "CLOUD KEY DAALO", "CLOUD_KEY", false);
        }

        if (modelConfigured) {
            if (!modelDownloaded && downloading) {
                return new Step(CODE_DOWNLOADING, "Model download ho raha hai",
                        "Download chal raha hai — khatam hone par 'USE THIS MODEL' dabaayein.",
                        "DOWNLOAD DEKHO", "AI_SETUP", false);
            }
            if (!modelDownloaded) {
                return new Step(CODE_NEED_MODEL, "Model download adhura hai",
                        "Download complete nahi hua — dobara download karein.",
                        "MODEL DOWNLOAD KARO", "DOWNLOAD_MODEL", false);
            }
            if (hostState == STATE_LOADING) {
                return new Step(CODE_LOADING, "Model load ho raha hai",
                        "Thoda intezaar karein — 5 se 20 second lagte hai.", "AI SETUP", "AI_SETUP", false);
            }
            if (hostState == STATE_READY) {
                return new Step(CODE_MODEL_READY, "On-device AI ready",
                        "Model load ho gaya hai — internet ke bina bhi agent chalega. "
                      + "Test karke dekh sakte hai ki kitna sahi jawab deta hai.",
                        "MODEL TEST KARO", "MODEL_TEST", true);
            }
            if (hostState == STATE_FAILED) {
                return new Step(CODE_LOAD_FAILED, "Model load fail hua",
                        "File ya RAM ki dikkat ho sakti hai. Dobara try karein ya chhota model choose karein.",
                        "AI SETUP", "AI_SETUP", false);
            }
            return new Step(CODE_DOWNLOADED, "Model download ho gaya",
                    "Ab isse chalane ke liye 'USE THIS MODEL' dabaayein.",
                    "MODEL LOAD KARO", "AI_SETUP", false);
        }

        if (downloading) {
            return new Step(CODE_DOWNLOADING, "Model download ho raha hai",
                    "Download chal raha hai — app band karke bhi chalta rahega.",
                    "DOWNLOAD DEKHO", "AI_SETUP", false);
        }

        // ---- 3) nothing at all yet: the very first logical step ---------------
        String why = (mode == AiSettings.MODE_OFFLINE_ONLY)
                ? "Internet band hai, isliye on-device model zaroori hai."
                : "Apne phone ke hisaab se model download karein, ya cloud key daal dein.";
        return new Step(CODE_NEED_MODEL, "Pehla kaam: AI setup karo",
                "Agent abhi sirf basic commands samajhta hai. " + why,
                "AI SETUP KARO", "AI_SETUP", false);
    }

    /** Chat hint text for the current step. */
    public static String shortHint(Step s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("ℹ️ ").append(s.title);
        if (s.detail != null && s.detail.length() > 0) sb.append("\n").append(s.detail);
        return sb.toString();
    }
}
