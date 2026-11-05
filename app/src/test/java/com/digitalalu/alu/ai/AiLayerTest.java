package com.digitalalu.alu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Tests for the hybrid AI layer that need neither a device nor a network:
 * device tiering, model recommendation, the JSON tool-call protocol, the
 * confirm-intent helpers, download math, readiness flow and crash guards.
 */
public class AiLayerTest {

    private static final long GB = 1073741824L;
    private static final long MB = 1048576L;

    /* ---------------------- device tiering ---------------------------- */

    @Test public void tierLadderMatchesRam() {
        assertEquals(DeviceProfile.TIER_TINY, DeviceProfile.tierFor(2L * GB, 8));
        assertEquals(DeviceProfile.TIER_SMALL, DeviceProfile.tierFor(3L * GB, 8));
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.tierFor(5L * GB, 8));
        assertEquals(DeviceProfile.TIER_LARGE, DeviceProfile.tierFor(8L * GB, 8));
    }

    @Test public void unknownRamDoesNotCrashOrOverreach() {
        assertEquals(DeviceProfile.TIER_SMALL, DeviceProfile.tierFor(0, 4));
        assertEquals(DeviceProfile.TIER_TINY, DeviceProfile.tierFor(1L * GB, 4));
    }

    @Test public void slowerOnFewerCores() {
        DeviceProfile a = new DeviceProfile();
        a.cores = 2; a.totalRam = 4L * GB;
        DeviceProfile b = new DeviceProfile();
        b.cores = 8; b.totalRam = 8L * GB;
        assertTrue(b.estimatedTokensPerSec(ModelCatalog.TIER_17B)
                > a.estimatedTokensPerSec(ModelCatalog.TIER_17B));
    }

    /** The user's real phone: 5.5 GB total, only ~1.1 GB free. */
    @Test public void actualPhoneProfileIsHandled() {
        assertEquals(DeviceProfile.TIER_MEDIUM, DeviceProfile.tierFor((long) (5.5 * GB), 8));
        DeviceProfile d = new DeviceProfile();
        d.totalRam = (long) (5.5 * GB);
        d.availRam = (long) (1.1 * GB);
        assertTrue("small model fits", LocalModelHost.canLoad(d.totalRam, d.availRam,
                378 * MB, 378 * MB).ok);
        assertTrue("1.7B Q4 fits the total RAM budget", LocalModelHost.canLoad(d.totalRam,
                d.availRam, 1056 * MB, 1056 * MB, 4L * GB).ok);
        // The catalog says 4B needs 6 GB -> the guard must agree, or we would be
        // recommending something we then refuse to load.
        assertFalse("4B needs 6GB, phone has 5.5GB",
                LocalModelHost.canLoad(d.totalRam, d.availRam, 2382 * MB, 2382 * MB, 6L * GB).ok);
        // and the free-RAM warning fires when free RAM < model size
        assertTrue("1.05GB model with 1.1GB free should warn",
                LocalModelHost.warnFreeRam(d.availRam, 1200 * MB).length() > 0);
        assertEquals("378MB model with 1.1GB free is fine",
                "", LocalModelHost.warnFreeRam(d.availRam, 378 * MB));
    }

    /* ---------------------- model catalogue --------------------------- */

    @Test public void recommendNeverExceedsRam() {
        for (long ram = 1L * GB; ram <= 12L * GB; ram += GB / 2) {
            ModelCatalog.ModelSpec m = ModelCatalog.recommend(ram);
            assertNotNull(m);
            boolean anythingFits = false;
            for (ModelCatalog.ModelSpec all : ModelCatalog.list()) {
                if (all.minRamBytes <= ram) anythingFits = true;
            }
            if (anythingFits) {
                assertTrue("recommendation must fit RAM: " + m.id + " @ " + ram,
                        m.minRamBytes <= ram);
            } else {
                // v1.1: recommend() picks for the MNN host only — the catalog now
                // also carries litertlm models (FunctionGemma) which LocalModelHost
                // cannot load, so compare against the smallest MNN-runtime model.
                ModelCatalog.ModelSpec smallest = null;
                for (ModelCatalog.ModelSpec all : ModelCatalog.list()) {
                    if (!"mnn".equals(all.runtime)) continue;
                    if (smallest == null || all.sizeBytes < smallest.sizeBytes) smallest = all;
                }
                assertEquals(smallest.id, m.id);
            }
        }
    }

    @Test public void recommendationAlwaysPassesTheLoadGuard() {
        for (long ram = 2L * GB; ram <= 12L * GB; ram += GB / 2) {
            ModelCatalog.ModelSpec m = ModelCatalog.recommend(ram);
            // only check when the recommendation was actually chosen to fit
            if (m.minRamBytes > ram) continue;
            LocalModelHost.Guard g = LocalModelHost.canLoad(ram, ram / 2,
                    m.sizeBytes, m.sizeBytes, m.minRamBytes);
            assertTrue("recommended model " + m.id + " must be loadable @ "
                    + (ram / GB) + "GB but guard said: " + g.reason, g.ok);
        }
    }

    /** The catalog minimum must be enforced, not just documented. */
    @Test public void catalogMinimumRamIsActuallyEnforced() {
        for (ModelCatalog.ModelSpec m : ModelCatalog.list()) {
            assertFalse("model claiming " + (m.minRamBytes / GB) + "GB must be refused below that: " + m.id,
                    LocalModelHost.canLoad(m.minRamBytes - 100 * MB, 2L * GB,
                            m.sizeBytes, m.sizeBytes, m.minRamBytes).ok);
            assertTrue("model must be allowed at its declared minimum: " + m.id,
                    LocalModelHost.canLoad(m.minRamBytes, 2L * GB,
                            m.sizeBytes, m.sizeBytes, m.minRamBytes).ok);
        }
    }

    @Test public void lowEndPhoneGetsSmallestModel() {
        assertEquals(ModelCatalog.TIER_06B, ModelCatalog.recommend(1L * GB).tier);
    }

    @Test public void everyPhoneGetsTheOneModel() {
        // v2.6: single-model app — recommend() always returns the default.
        assertEquals(ModelCatalog.DEFAULT().id, ModelCatalog.recommend(8L * GB).id);
        assertEquals(ModelCatalog.DEFAULT().id, ModelCatalog.recommend(2L * GB).id);
        // v1.1: catalog = exactly one MNN model (recommend()'s domain) plus
        // FunctionGemma whenever the LiteRT-LM runtime classes are bundled.
        // FGemma MUST be visible in list() — the UI chooser reads it from there.
        int mnn = 0;
        boolean hasFgemma = false;
        for (ModelCatalog.ModelSpec m : ModelCatalog.list()) {
            if ("mnn".equals(m.runtime)) mnn++;
            if ("litertlm".equals(m.runtime)) hasFgemma = true;
        }
        assertEquals(1, mnn);
        assertTrue("FunctionGemma must be listed when LiteRT-LM is on the classpath",
                hasFgemma);
    }

    @Test public void rankedListIsNeverEmpty_andRecommendationFirst() {
        for (long ram = 1L * GB; ram <= 12L * GB; ram += GB) {
            List<ModelCatalog.ModelSpec> ranked = ModelCatalog.rankedFor(ram);
            assertFalse("ranked list must never be empty @ " + ram, ranked.isEmpty());
            assertEquals(ModelCatalog.recommend(ram).id, ranked.get(0).id);
        }
    }

    @Test public void rankedListOnlyOffersFittingModelsWhenSomeFit() {
        long ram = 6L * GB;
        boolean anyFits = false;
        for (ModelCatalog.ModelSpec m : ModelCatalog.rankedFor(ram)) {
            if (m.minRamBytes + (long) (1.2 * GB) <= ram) anyFits = true;
        }
        assertTrue(anyFits);
    }

    @Test public void catalogueUrlsAndSizesAreSane() {
        List<ModelCatalog.ModelSpec> all = ModelCatalog.list();
        assertTrue("should offer at least 1 model", all.size() >= 1);
        for (ModelCatalog.ModelSpec m : all) {
            assertTrue(m.url.startsWith("https://huggingface.co/"));
            assertTrue(m.files.length > 0);
            long sum = 0; for (ModelCatalog.ModelFile f : m.files) sum += f.sizeBytes;
            assertEquals(sum, m.sizeBytes);
            assertTrue(m.urlFor(m.files[0]).contains("/resolve/main/"));
            assertTrue("size must be a real GGUF size", m.sizeBytes > 100L * MB);
            assertTrue(m.sizeBytes < 5L * GB);
            assertTrue(m.minRamBytes >= m.sizeBytes);
        }
    }

    @Test public void lookupHelpersWork() {
        ModelCatalog.ModelSpec m = ModelCatalog.list().get(0);
        assertEquals(m.id, ModelCatalog.byId(m.id).id);
        assertEquals(m.id, ModelCatalog.byFileName(m.fileName).id);
        assertNull(ModelCatalog.byId("nope"));
        assertNull(ModelCatalog.byId(null));
        assertNull(ModelCatalog.byFileName(null));
    }

    /* ---------------------- tool-call protocol ------------------------ */

    @Test public void parsesCleanJson() {
        IntentProtocol.ToolCall c = IntentProtocol.parse(
                "{\"say\":\"Theek hai\",\"tool\":\"add_window\",\"args\":{\"h\":48,\"w\":60}}");
        assertTrue(c.parsed);
        assertEquals("Theek hai", c.say);
        assertEquals("add_window", c.tool);
        assertEquals(48.0, c.args.optDouble("h"), 0.001);
        assertFalse(c.raw);
    }

    @Test public void parsesFencedJson() {
        IntentProtocol.ToolCall c = IntentProtocol.parse(
                "```json\n{\"say\":\"ho gaya\",\"tool\":null,\"args\":{}}\n```");
        assertTrue(c.parsed);
        assertEquals("ho gaya", c.say);
        assertFalse(c.hasTool());
    }

    @Test public void ignoresProseBeforeAndAfterJson() {
        IntentProtocol.ToolCall c = IntentProtocol.parse(
                "Zaroor! {\"say\":\"rate set\",\"tool\":\"update_rate\",\"args\":{\"kind\":\"alu\",\"rate\":320}} yeh ho gaya.");
        assertTrue(c.parsed);
        assertEquals("update_rate", c.tool);
        assertEquals(320.0, c.args.optDouble("rate"), 0.001);
    }

    @Test public void handlesToolNamedNullAsString() {
        IntentProtocol.ToolCall c = IntentProtocol.parse(
                "{\"say\":\"kaise ho\",\"tool\":\"null\",\"args\":{}}");
        assertFalse(c.hasTool());
        assertEquals("kaise ho", c.say);
    }

    @Test public void salvagesToolFromBrokenJson() {
        IntentProtocol.ToolCall c = IntentProtocol.parse(
                "theek hai, tool: \"show_costing\" use karta hoon aur args {}");
        assertEquals("show_costing", c.tool);
        assertTrue(c.raw);
    }

    /**
     * Regression: the salvage path used to read the tool name with wrong offsets
     * and return ":" — the agent then looked up a tool that never existed, which
     * is exactly the "hybrid theek se kaam nahi kar raha" symptom on the phone.
     */
    @Test public void salvagedToolNameIsTheRealOneNotAColon() {
        IntentProtocol.ToolCall a = IntentProtocol.parse(
                "{\"say\":\"Rate set kar raha hoon.\",\"tool\":\"update_rate\",\"args\":{\"kind\":\"al");
        assertEquals("update_rate", a.toolName());
        // v2.8: the truncated object is repaired and parsed (args survive) instead of salvaged.
        assertTrue(a.parsed);
        assertEquals("al", a.args.optString("kind"));
    }

    @Test public void bogusToolNamesAreNeverUsed() {
        assertFalse(IntentProtocol.parse(
                "{\"say\":\"hi\",\"tool\":\":\",\"args\":{}}").hasTool());
        assertFalse(IntentProtocol.parse(
                "{\"say\":\"hi\",\"tool\":\"12 34\",\"args\":{}}").hasTool());
        assertTrue(IntentProtocol.isPlausibleToolName("add_window"));
        assertTrue(IntentProtocol.isPlausibleToolName("set_stock_pipe"));
        assertFalse(IntentProtocol.isPlausibleToolName(":"));
        assertFalse(IntentProtocol.isPlausibleToolName(""));
        assertFalse(IntentProtocol.isPlausibleToolName(null));
        assertFalse(IntentProtocol.isPlausibleToolName("null"));
        assertFalse(IntentProtocol.isPlausibleToolName("4b"));
    }

    @Test public void fallsBackToRawTextWhenModelIgnoresFormat() {
        IntentProtocol.ToolCall c = IntentProtocol.parse("Main aapki madad kar sakta hoon.");
        assertFalse(c.parsed);
        assertFalse(c.hasTool());
        assertTrue(c.say.contains("madad"));
        assertTrue(c.raw);
    }

    @Test public void braceScannerHandlesNestedObjectsAndBracesInStrings() {
        String s = "prefix {\"a\":{\"b\":1},\"say\":\"use } carefully\",\"tool\":null} suffix";
        String json = IntentProtocol.firstJsonObject(s);
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}") && json.contains("\"tool\":null}"));
        assertTrue(json.contains("\"a\":{\"b\":1}"));
        assertEquals("use } carefully", IntentProtocol.parse(s).say);
    }

    @Test public void emptyOrNullInputIsSafe() {
        assertFalse(IntentProtocol.parse(null).parsed);
        assertFalse(IntentProtocol.parse("").parsed);
        assertFalse(IntentProtocol.parse("   ").parsed);
        assertEquals("", IntentProtocol.stripFence(null));
        assertNull(IntentProtocol.firstJsonObject("no json here"));
        assertEquals("", IntentProtocol.stripFence(""));
    }

    @Test public void systemPromptCarriesGuardrailsAndExamples() {
        String sys = IntentProtocol.SYSTEM;
        assertTrue("must forbid doing its own math", sys.contains("KABHI khud hisaab"));
        assertTrue("must show the JSON shape", sys.contains("\"say\""));
        assertTrue("few-shot examples help small models", sys.contains("UDAHARAN"));
        assertTrue(sys.contains("add_window"));
        assertTrue("needs a no-tool example for small models", sys.contains("\"tool\":null"));
    }

    @Test public void localPromptIsPassedThroughUnchanged() {
        // v2.8 (MNN): thinking is disabled through the chat-template flag, not a prompt suffix.
        assertEquals("abc", IntentProtocol.forLocalModel("abc"));
    }

    /* ---------------------- confirm intents --------------------------- */

    @Test public void recognisesYesAndNo() {
        assertTrue(HybridAgent.isYes("haan"));
        assertTrue(HybridAgent.isYes("Haan karo"));
        assertTrue(HybridAgent.isYes("ok"));
        assertTrue(HybridAgent.isYes("  Yes "));
        assertTrue(HybridAgent.isNo("nahi"));
        assertTrue(HybridAgent.isNo("no"));
        assertTrue(HybridAgent.isNo("cancel"));
        assertFalse(HybridAgent.isYes("nahi"));
        assertFalse(HybridAgent.isNo("haan"));
        assertFalse(HybridAgent.isYes("48x60 add karo"));
        assertFalse(HybridAgent.isNo(""));
    }

    /* ---------------------- settings semantics ------------------------ */

    @Test public void defaultModeIsOfflineFirstAndCloudNeedsAKey() {
        AiSettings s = new AiSettings();
        assertEquals(AiSettings.MODE_OFFLINE_FIRST, s.mode);
        assertFalse("no key -> cloud off", s.cloudEnabled());
        s.apiKey = "AIzaSyFAKEKEY1234567890";
        assertTrue(s.cloudEnabled());
        s.mode = AiSettings.MODE_OFFLINE_ONLY;
        assertFalse("NO INTERNET must disable cloud", s.cloudEnabled());
    }

    /**
     * THE BUG THE USER HIT: a loaded on-device model must keep working in
     * NO INTERNET mode — running a local model is offline by definition.
     */
    @Test public void noInternetModeStillAllowsTheOnDeviceModel() {
        AiSettings s = new AiSettings();
        s.mode = AiSettings.MODE_OFFLINE_ONLY;
        s.localModelPath = "/x/models/tiny.gguf";
        s.localHandlesTools = true;
        assertFalse("cloud must be off", s.cloudEnabled());
        assertTrue("local model must stay usable", s.localPermitted() && s.localConfigured());
        assertTrue("agent still has a brain", s.anyAiEnabled());
        assertEquals("NO INTERNET", s.modeName());
    }

    @Test public void userCanTurnTheModelOffRegardlessOfMode() {
        AiSettings s = new AiSettings();
        s.webBrain = false; // v2.4+: free web brain is on by default; isolate the local tier here
        s.localModelPath = "/x/models/tiny.gguf";
        s.localHandlesTools = false;
        assertFalse(s.anyAiEnabled());
        s.localHandlesTools = true;
        assertTrue(s.anyAiEnabled());
    }

    @Test public void threadAutoPicksSaneCount() {
        AiSettings s = new AiSettings();
        assertEquals(2, s.effectiveThreads(2));
        assertEquals(3, s.effectiveThreads(4));
        assertEquals(4, s.effectiveThreads(12));
        s.localThreads = 6;
        assertEquals(6, s.effectiveThreads(12));
    }

    @Test public void providerDefaultsAreSensible() {
        AiSettings g = new AiSettings();
        assertTrue(g.effectiveBaseUrl().contains("generativelanguage"));
        assertTrue(g.effectiveModel().contains("gemini"));
        AiSettings o = new AiSettings();
        o.provider = AiSettings.PROVIDER_OPENAI;
        assertTrue(o.effectiveBaseUrl().startsWith("https://api.openai.com"));
        assertEquals("gpt-4o-mini", o.effectiveModel());
    }

    @Test public void dailyCapProtectsTheUser() {
        AiSettings s = new AiSettings();
        s.dailyCap = 2;
        s.usedToday = 0;
        assertTrue(s.underCap());
        s.usedToday = 2;
        assertFalse(s.underCap());
    }

    /* ============ readiness / logical flow ============================ */

    @Test public void freshInstallAsksForSetupFirst() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                false, false, AiReadiness.STATE_NONE, false, true, false);
        assertEquals(AiReadiness.CODE_NEED_MODEL, s.code);
        assertFalse(s.ready);
        assertTrue(s.hasAction());
        assertEquals("AI_SETUP", s.actionType);
    }

    @Test public void everythingOffIsAQuietValidState() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, false, true,
                true, true, AiReadiness.STATE_NONE, false, true, false);
        assertEquals(AiReadiness.CODE_RULES_ONLY, s.code);
        assertTrue("turning AI off must not nag", s.ready);
    }

    @Test public void cloudKeyAloneMakesTheAgentUsable() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                false, false, AiReadiness.STATE_NONE, true, true, false);
        assertEquals(AiReadiness.CODE_CLOUD_READY, s.code);
        assertTrue(s.ready);
    }

    @Test public void exhaustedCloudQuotaIsNotTreatedAsReady() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                false, false, AiReadiness.STATE_NONE, true, false, false);
        assertFalse(s.ready);
    }

    @Test public void noInternetWithModelReadyIsFine() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_ONLY, true, true,
                true, true, AiReadiness.STATE_READY, false, true, false);
        assertEquals(AiReadiness.CODE_MODEL_READY, s.code);
        assertTrue(s.ready);
        assertEquals("MODEL_TEST", s.actionType);
    }

    @Test public void noInternetWithoutModelIsNotReady() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_ONLY, true, true,
                false, false, AiReadiness.STATE_NONE, false, true, false);
        assertEquals(AiReadiness.CODE_NEED_MODEL, s.code);
        assertFalse(s.ready);
        assertTrue(s.detail.contains("Internet band"));
    }

    @Test public void loadFailureIsSurfacedWithAFix() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                true, true, AiReadiness.STATE_FAILED, false, true, false);
        assertEquals(AiReadiness.CODE_LOAD_FAILED, s.code);
        assertFalse(s.ready);
        assertTrue(s.hasAction());
    }

    @Test public void downloadedButNotLoadedAsksTheUserToLoad() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                true, true, AiReadiness.STATE_NONE, false, true, false);
        assertEquals(AiReadiness.CODE_DOWNLOADED, s.code);
        assertFalse(s.ready);
    }

    @Test public void loadingShowsProgressNotAnError() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                true, true, AiReadiness.STATE_LOADING, false, true, false);
        assertEquals(AiReadiness.CODE_LOADING, s.code);
        assertFalse(s.ready);
    }

    @Test public void modelReadyPointsTheUserAtTheTest() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                true, true, AiReadiness.STATE_READY, false, true, false);
        assertEquals(AiReadiness.CODE_MODEL_READY, s.code);
        assertTrue(s.ready);
        assertEquals("MODEL_TEST", s.actionType);
    }

    @Test public void missingRuntimeFallsBackToCloudAdvice() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, false,
                false, false, AiReadiness.STATE_NONE, false, true, false);
        assertEquals(AiReadiness.CODE_RUNTIME_MISSING, s.code);
        assertEquals("CLOUD_KEY", s.actionType);
    }

    @Test public void downloadInFlightIsReportedAsSuch() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                true, false, AiReadiness.STATE_NONE, false, true, true);
        assertEquals(AiReadiness.CODE_DOWNLOADING, s.code);
        assertFalse(s.ready);
    }

    @Test public void hintTextAlwaysMentionsTheProblem() {
        AiReadiness.Step s = AiReadiness.of(AiSettings.MODE_OFFLINE_FIRST, true, true,
                false, false, AiReadiness.STATE_NONE, false, true, false);
        String hint = AiReadiness.shortHint(s);
        assertTrue(hint.startsWith("ℹ️"));
        assertTrue(hint.contains(s.title));
        assertEquals("", AiReadiness.shortHint(null));
    }

    /* ============ crash guards ======================================== */

    @Test public void refusesTruncatedModelFile() {
        LocalModelHost.Guard g = LocalModelHost.canLoad(8L * GB, 4L * GB, 1000L * MB, 400L * MB);
        assertFalse("half-downloaded file must never reach the native loader", g.ok);
        assertTrue(g.reason.contains("adhura"));
    }

    @Test public void refusesMissingModelFile() {
        assertFalse(LocalModelHost.canLoad(8L * GB, 4L * GB, 1000L * MB, 0).ok);
    }

    @Test public void refusesModelBiggerThanPhoneRam() {
        assertFalse(LocalModelHost.canLoad(3L * GB, 2L * GB, 2382L * MB, 2382L * MB).ok);
        assertTrue(LocalModelHost.canLoad(3L * GB, 2L * GB, 2382L * MB, 2382L * MB).reason.contains("RAM"));
        assertFalse(LocalModelHost.canLoad(2L * GB, GB, 1056L * MB, 1056L * MB).ok);
        assertTrue(LocalModelHost.canLoad(4L * GB, 2L * GB, 1056L * MB, 1056L * MB).ok);
    }

    @Test public void refusesWhenFreeRamIsTooLowRightNow() {
        LocalModelHost.Guard g = LocalModelHost.canLoad(6L * GB, 150L * MB, 1000L * MB, 1000L * MB);
        assertFalse(g.ok);
        assertTrue(g.reason.toLowerCase().contains("free ram"));
    }

    @Test public void allowsAHealthyLoad() {
        LocalModelHost.Guard g = LocalModelHost.canLoad(8L * GB, 4L * GB, 1000L * MB, 1000L * MB);
        assertTrue(g.ok);
        assertEquals("", g.reason);
    }

    @Test public void toleratesUnknownRamInsteadOfBlocking() {
        assertTrue(LocalModelHost.canLoad(0, 0, 1000L * MB, 1000L * MB).ok);
    }

    @Test public void freeRamWarningIsOnlyForTightSituations() {
        assertEquals("", LocalModelHost.warnFreeRam(3L * GB, 1000L * MB));
        assertTrue(LocalModelHost.warnFreeRam(500L * MB, 1000L * MB).length() > 0);
        assertTrue(LocalModelHost.warnFreeRam(150L * MB, 1000L * MB).contains("bahut kam"));
        assertEquals("", LocalModelHost.warnFreeRam(0, 1000L * MB));
    }

    /* ============ download math ======================================= */

    @Test public void sizeMatchAcceptsSmallMetadataDifferenceOnly() {
        assertTrue(ModelStore.sizeMatches(1000L * MB, 1000L * MB));
        assertTrue(ModelStore.sizeMatches(990L * MB, 1000L * MB));
        assertFalse(ModelStore.sizeMatches(600L * MB, 1000L * MB));
        assertFalse(ModelStore.sizeMatches(0, 1000L * MB));
        assertFalse(ModelStore.sizeMatches(1000L * MB, 0));
    }

    @Test public void prettyBytesAreReadable() {
        assertEquals("512 B", ModelStore.humanBytes(512));
        assertEquals("1 KB", ModelStore.humanBytes(1024));
        assertEquals("2 MB", ModelStore.humanBytes(2L * MB));
        assertEquals("1.00 GB", ModelStore.humanBytes(GB));
        assertEquals("0 B", ModelStore.humanBytes(-5));
    }

    @Test public void etaMathsIsSafe() {
        assertEquals(100L, ModelStore.etaSeconds(10L * MB, 1000L * MB));
        assertEquals(0L, ModelStore.etaSeconds(0, 1000L * MB));
        assertEquals(0L, ModelStore.etaSeconds(10L * MB, 0));
        assertEquals("45 sec", ModelStore.etaText(45));
        assertEquals("3 min", ModelStore.etaText(200));
        assertEquals("1 ghante 0 min", ModelStore.etaText(3600));
        assertEquals("", ModelStore.etaText(0));
    }

    @Test public void speedSmoothingNeverProducesNonsense() {
        long first = ModelStore.speed(0, 0, 1L * MB, 1000, 0);
        assertEquals(1L * MB, first);
        long smoothed = ModelStore.speed(1L * MB, 1000, 3L * MB, 3000, first);
        assertTrue(smoothed > 500L * 1024 && smoothed < 2L * MB);
        assertEquals(first, ModelStore.speed(5L * MB, 0, 1L * MB, 1000, first));
        assertEquals(first, ModelStore.speed(5L * MB, 5000, 5L * MB, 5000, first));
    }

    // ---- v2.8 MNN tier hardening ----
    @Test public void nearMissToolNamesResolve() {
        assertEquals("show_estimate", ToolRegistry.resolve("estimate"));
        assertEquals("show_cutting_plan", ToolRegistry.resolve("cutting_plan"));
        assertEquals("list_customers", ToolRegistry.resolve("list_customer"));
        assertEquals("add_window", ToolRegistry.resolve("add_window"));
        assertNull(ToolRegistry.resolve("ALU Window"));
        assertNull(ToolRegistry.resolve(""));
    }
    @Test public void truncatedJsonIsRepaired() {
        IntentProtocol.ToolCall c = IntentProtocol.parse("{\"tool\": \"update_rate\", \"args\": {\"kind\": \"alu\", \"rate\": 310}, \"say\": \"Rate set ho");
        assertTrue(c.parsed);
        assertEquals("update_rate", c.tool);
        assertEquals(310, c.args.optInt("rate"));
        IntentProtocol.ToolCall d = IntentProtocol.parse("{\"tool\": \"delete_last_window\", \"args\": {}},");
        assertEquals("delete_last_window", d.tool);
    }
    @Test public void sayIsCapped() {
        String longSay = "Theek hai. " + "bahut lamba ramble hai jo kabhi khatam nahi hota aur phir se dohraata hai ".repeat(5);
        String c = HybridAgent.capSay(longSay);
        assertTrue(c.length() <= 160);
        assertEquals("Pehli line", HybridAgent.capSay("Pehli line\n**Doosri** line"));
    }
}
