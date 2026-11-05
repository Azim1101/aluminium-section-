package com.digitalalu.alu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Tests for the parts that make the "model decides → agent works → model
 * explains" loop hold together: the JSON grammar that pins a small model down,
 * and the Interactions API plumbing (parsed offline against real response shapes).
 */
public class CloudAgentTest {

    /* ----------------------------- grammar ---------------------------- */

    @Test public void grammarLocksEveryToolName() {
        // GBNF escapes the quotes it matches, so compare without backslashes
        String g = JsonGrammar.forTools().replace("\\", "");
        assertTrue(g.contains("root"));
        for (ToolRegistry.Tool t : ToolRegistry.all()) {
            assertTrue("grammar must allow tool " + t.name, g.contains("\"" + t.name + "\""));
        }
        assertTrue("null tool must stay allowed so chit-chat works", g.contains("\"null\""));
    }

    @Test public void grammarLocksArgumentNamesToo() {
        String g = JsonGrammar.forTools().replace("\\", "");
        // the junk-key bug: the model kept inventing "type"/"no" after the real args
        for (String key : new String[]{"h", "w", "nos", "sutter", "rate", "kind"}) {
            assertTrue("grammar must allow arg " + key, g.contains("\"" + key + "\""));
        }
        // values stay flat — nested objects are what made it ramble
        assertFalse("nested objects must not be allowed in args", g.contains("value  ::= string | number | \"true\" | \"false\" | \"null\" | object | array"));
    }

    @Test public void grammarBoundsTheNumberOfArgs() {
        String g = JsonGrammar.forTools();
        assertTrue("pair list must be bounded, otherwise the model rambles",
                g.contains("{0,"));
    }

    @Test public void sayOnlyGrammarPinsTheToolToNull() {
        String g = JsonGrammar.sayOnly().replace("\\", "");
        assertTrue(g.contains("root"));
        assertTrue("follow-up must not be able to restart the tool loop",
                g.contains("\"tool\"") && g.contains("\"null\""));
        assertFalse("no tool names in the follow-up grammar", g.contains("add_window"));
    }

    @Test public void argKeyParserReadsSignatures() {
        String[] a = JsonGrammar.argKeysOf("(h,w,sutter,nos,system,name)");
        assertEquals(6, a.length);
        assertEquals("h", a[0]);
        assertEquals("name", a[5]);
        assertEquals(0, JsonGrammar.argKeysOf("()").length);
        assertEquals(0, JsonGrammar.argKeysOf(null).length);
        assertEquals(0, JsonGrammar.argKeysOf("garbage").length);
        String[] b = JsonGrammar.argKeysOf("(section, length, qty)");
        assertEquals(3, b.length);
        assertEquals("length", b[1]);
    }

    /* ------------------------- interactions API ----------------------- */

    /** The exact response shape from the Interactions API docs. */
    @Test public void parsesAPlainModelOutputInteraction() throws Exception {
        String raw = "{\"id\":\"v2_Chd123\",\"status\":\"completed\","
                + "\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\","
                + "\"text\":\"There are 8 paws in your house.\"}]}],"
                + "\"object\":\"interaction\",\"model\":\"gemini-3.8-flash\"}";
        CloudLlm.Interaction it = new CloudLlm.Interaction();
        CloudLlm.parseInto(new JSONObject(raw), it);
        assertEquals("v2_Chd123", it.id);
        assertEquals("There are 8 paws in your house.", it.text);
        assertEquals(0, it.calls.size());
    }

    /** A function_call step must give us the tool name, id and arguments. */
    @Test public void parsesAFunctionCallStep() throws Exception {
        String raw = "{\"id\":\"v2_abc\",\"status\":\"completed\",\"steps\":["
                + "{\"type\":\"function_call\",\"name\":\"add_window\",\"id\":\"call_9\","
                + "\"arguments\":{\"h\":48,\"w\":60,\"nos\":2}}]}";
        CloudLlm.Interaction it = new CloudLlm.Interaction();
        CloudLlm.parseInto(new JSONObject(raw), it);
        assertEquals(1, it.calls.size());
        CloudLlm.ToolCallStep c = it.calls.get(0);
        assertEquals("add_window", c.name);
        assertEquals("call_9", c.callId);
        assertEquals(48.0, c.args.optDouble("h"), 0.001);
        assertEquals(2.0, c.args.optDouble("nos"), 0.001);
    }

    @Test public void functionResultMatchesTheWireFormat() {
        CloudLlm.ToolCallStep c = new CloudLlm.ToolCallStep();
        c.name = "show_estimate";
        c.callId = "call_42";
        JSONObject fr = CloudLlm.functionResult(c, "Total 12 pipes lagenge.");
        assertEquals("function_result", fr.optString("type"));
        assertEquals("show_estimate", fr.optString("name"));
        assertEquals("call_42", fr.optString("call_id"));
        JSONArray parts = fr.optJSONArray("result");
        assertNotNull(parts);
        assertEquals(1, parts.length());
        assertEquals("Total 12 pipes lagenge.", parts.optJSONObject(0).optString("text"));
    }

    @Test public void toolSchemaDescribesEveryTool() {
        JSONArray tools = CloudLlm.toolSchema();
        assertEquals(ToolRegistry.all().size(), tools.length());
        for (int i = 0; i < tools.length(); i++) {
            JSONObject t = tools.optJSONObject(i);
            assertEquals("function", t.optString("type"));
            assertTrue(t.optString("name").length() > 0);
            assertTrue("every tool needs a description the model can read",
                    t.optString("description").length() > 5);
            assertEquals("object", t.optJSONObject("parameters").optString("type"));
        }
    }

    @Test public void toolSchemaListsArgsForAddWindow() {
        JSONArray tools = CloudLlm.toolSchema();
        JSONObject add = null;
        for (int i = 0; i < tools.length(); i++) {
            if ("add_window".equals(tools.optJSONObject(i).optString("name"))) {
                add = tools.optJSONObject(i);
            }
        }
        assertNotNull(add);
        JSONObject props = add.optJSONObject("parameters").optJSONObject("properties");
        assertNotNull(props);
        for (String k : new String[]{"h", "w", "nos", "sutter", "system", "name"}) {
            assertTrue("add_window must declare " + k, props.has(k));
        }
    }

    /* ------------- response shapes (verified against the live API) ----- */

    /**
     * The live Interactions endpoint wraps errors in an ARRAY. Before this was
     * handled the app threw a JSONException, called it a failure and silently
     * dropped to rules — the exact "key daali hai par kaam nahi kar rahi".
     */
    @Test public void interactionsErrorCanBeAnArray() {
        String raw = "[{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass"
                + " a valid API key.\",\"status\":\"INVALID_ARGUMENT\",\"details\":["
                + "{\"reason\":\"API_KEY_INVALID\"}]}}]";
        CloudLlm.Interaction out = new CloudLlm.Interaction();
        CloudLlm.parseResponse(raw, out);
        assertFalse(out.ok);
        assertTrue("must read the message out of the array-wrapped error",
                out.error.contains("API key not valid"));
        assertTrue(out.error.contains("INVALID_ARGUMENT"));
        assertTrue(CloudLlm.errorMessage(raw).contains("API key not valid"));
    }

    /** A successful streamed turn: events array with deltas + a final model_output. */
    @Test public void interactionsSuccessCanBeAnEventsArray() {
        String raw = "[{\"type\":\"step.delta\",\"delta\":{\"type\":\"text\",\"text\":\"Namaste \"}},"
                + "{\"type\":\"step.delta\",\"delta\":{\"type\":\"text\",\"text\":\"ji!\"}},"
                + "{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":\"Namaste ji!\"}]}]";
        CloudLlm.Interaction out = new CloudLlm.Interaction();
        CloudLlm.parseResponse(raw, out);
        assertTrue(out.ok);
        assertEquals("Namaste ji!", out.text);
    }

    @Test public void interactionsFunctionCallInsideAnEventsArray() {
        String raw = "[{\"type\":\"function_call\",\"name\":\"show_costing\",\"id\":\"call_7\","
                + "\"arguments\":{\"kind\":\"alu\"}}]";
        CloudLlm.Interaction out = new CloudLlm.Interaction();
        CloudLlm.parseResponse(raw, out);
        assertTrue(out.ok);
        assertEquals(1, out.calls.size());
        assertEquals("show_costing", out.calls.get(0).name);
        assertEquals("call_7", out.calls.get(0).callId);
    }

    /** Some responses nest the whole interaction under an "interaction" key. */
    @Test public void interactionsCanBeWrappedInAnEnvelope() {
        String raw = "{\"interaction\":{\"id\":\"v2_xyz\",\"steps\":[{\"type\":\"model_output\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}}";
        CloudLlm.Interaction out = new CloudLlm.Interaction();
        CloudLlm.parseResponse(raw, out);
        assertTrue(out.ok);
        assertEquals("v2_xyz", out.id);
        assertEquals("hi", out.text);
    }

    @Test public void parseResponseSurvivesJunk() {
        CloudLlm.Interaction a = new CloudLlm.Interaction();
        CloudLlm.parseResponse("", a);
        assertFalse(a.ok);
        CloudLlm.Interaction b = new CloudLlm.Interaction();
        CloudLlm.parseResponse("<html>502 Bad Gateway</html>", b);
        assertFalse(b.ok);
        assertTrue(b.error.contains("502"));
    }

    /* -------------------- model discovery (cloud fix) ------------------ */

    /** Real shape of GET /v1beta/models. */
    @Test public void parsesTheModelsList() {
        String raw = "{\"models\":["
          + "{\"name\":\"models/gemini-2.5-flash\",\"supportedGenerationMethods\":[\"generateContent\",\"countTokens\"]},"
          + "{\"name\":\"models/gemini-embedding-001\",\"supportedGenerationMethods\":[\"embedContent\"]},"
          + "{\"name\":\"models/gemini-3.8-flash\",\"supportedGenerationMethods\":[\"generateContent\"]},"
          + "{\"name\":\"models/imagen-3.0\",\"supportedGenerationMethods\":[\"predict\"]}"
          + "]}";
        java.util.List<String> names = CloudLlm.parseModelNames(raw);
        assertTrue(names.contains("gemini-2.5-flash"));
        assertTrue(names.contains("gemini-3.8-flash"));
        assertFalse("embedding models must be dropped", names.contains("gemini-embedding-001"));
        assertFalse("imagen must be dropped", names.contains("imagen-3.0"));
    }

    @Test public void parseModelNamesSurvivesGarbage() {
        assertTrue(CloudLlm.parseModelNames("").isEmpty());
        assertTrue(CloudLlm.parseModelNames("not json").isEmpty());
        assertTrue(CloudLlm.parseModelNames("{}").isEmpty());
        assertTrue(CloudLlm.parseModelNames("{\"error\":{\"message\":\"bad key\"}}").isEmpty());
    }

    @Test public void picksTheBestAvailableModel() {
        java.util.List<String> have = java.util.Arrays.asList(
                "gemini-2.5-pro", "gemini-2.0-flash", "gemini-2.5-flash");
        assertEquals("gemini-2.5-flash", CloudLlm.pickBestModel(have));

        have = java.util.Arrays.asList("gemini-2.0-flash", "gemini-2.5-pro");
        assertEquals("gemini-2.0-flash", CloudLlm.pickBestModel(have));

        have = java.util.Arrays.asList("gemini-9.9-super");
        assertEquals("gemini-9.9-super", CloudLlm.pickBestModel(have));

        assertEquals("", CloudLlm.pickBestModel(new java.util.ArrayList<String>()));
        assertEquals("", CloudLlm.pickBestModel(null));
    }

    @Test public void chatModelFilterKeepsOnlyUsableModels() {
        assertTrue(CloudLlm.isChatModel("gemini-3.8-flash"));
        assertTrue(CloudLlm.isChatModel("gemini-2.5-pro"));
        assertFalse(CloudLlm.isChatModel("gemini-embedding-001"));
        assertFalse(CloudLlm.isChatModel("text-embedding-004"));
        assertFalse(CloudLlm.isChatModel("imagen-3.0-generate"));
        assertFalse(CloudLlm.isChatModel("gemini-2.5-flash-image"));
        assertFalse(CloudLlm.isChatModel("veo-3"));
        assertFalse(CloudLlm.isChatModel(null));
    }

    /** The exact error shape Google returns — this is what the user sees. */
    @Test public void readsGoogleErrorMessages() {
        String keyInvalid = "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass"
                + " a valid API key.\",\"status\":\"INVALID_ARGUMENT\"}}";
        String msg = CloudLlm.errorMessage(keyInvalid);
        assertTrue(msg.contains("API key not valid"));
        assertTrue(msg.contains("INVALID_ARGUMENT"));

        String notFound = "{\"error\":{\"code\":404,\"message\":\"models/gemini-9.9 is not found\","
                + "\"status\":\"NOT_FOUND\"}}";
        assertTrue(CloudLlm.errorMessage(notFound).contains("is not found"));

        assertEquals("(khali jawab)", CloudLlm.errorMessage(""));
        assertTrue(CloudLlm.errorMessage("plain text boom").contains("boom"));
    }

    @Test public void keyFingerprintNeverLeaksTheWholeKey() {
        String f = CloudLlm.fingerprint("AIzaSyABCDEFGHIJKLMNOP1234");
        assertTrue(f.startsWith("AIza"));
        assertFalse("full key must never be printed", f.contains("ABCDEFGHIJKLMNOP"));
        assertTrue(f.contains("chars"));
        assertEquals("not set", CloudLlm.fingerprint(""));
        assertEquals("***", CloudLlm.fingerprint("abc"));
    }

    @Test public void diagReportTellsTheTruthWhenNothingWorks() {
        AiSettings s = new AiSettings();
        s.apiKey = "AIzaSyExampleKeyValue123456";
        CloudLlm.Diag d = new CloudLlm.Diag();
        d.keyPresent = true;
        d.modelsError = "HTTP 400: API key not valid. [INVALID_ARGUMENT]";
        d.generateTest = new CloudLlm.Reply();
        d.generateTest.error = "HTTP 400: API key not valid. [INVALID_ARGUMENT]";
        d.somethingWorks = false;

        String rep = d.report(s);
        assertTrue(rep.contains("ALU CLOUD TEST"));
        assertTrue(rep.contains("API key not valid"));
        assertTrue(rep.contains("KOI BHI WIRE CHALA NAHI"));
        assertFalse("the key itself must never appear", rep.contains("AIzaSyExampleKeyValue123456"));
    }

    @Test public void diagReportRecommendsWhatWorked() {
        AiSettings s = new AiSettings();
        s.apiKey = "AIzaSyExampleKeyValue123456";
        CloudLlm.Diag d = new CloudLlm.Diag();
        d.keyPresent = true;
        d.models.add("gemini-2.5-flash");
        d.bestModel = "gemini-2.5-flash";
        d.generateTest = new CloudLlm.Reply();
        d.generateTest.ok = true;
        d.generateTestBest = new CloudLlm.Reply();
        d.generateTestBest.ok = true;
        d.recommendModel = "gemini-2.5-flash";
        d.recommendWire = CloudLlm.WIRE_INTERACTIONS;
        d.somethingWorks = true;

        String rep = d.report(s);
        assertTrue(rep.contains("CHALEGA"));
        assertTrue(rep.contains("gemini-2.5-flash"));
        assertTrue(rep.contains("Interactions API"));
    }

    /** Default must be the portable wire — the chat should never depend on the
     *  newer endpoint being enabled for the key. */
    @Test public void chatDefaultsToGenerateContentWIRE() {
        AiSettings s = new AiSettings();
        assertEquals("auto", s.cloudWireName());
        assertEquals(CloudLlm.WIRE_GENERATE, CloudLlm.wireFor(s));
        s.cloudWire = CloudLlm.WIRE_INTERACTIONS;
        assertEquals(CloudLlm.WIRE_INTERACTIONS, CloudLlm.wireFor(s));
        s.provider = AiSettings.PROVIDER_OPENAI;
        assertEquals(-1, CloudLlm.wireFor(s));
    }

    @Test public void interactionsInputUsesTheTypedRestForm() {
        // The REST API wants [{"type":"user_input","content":"..."}] — a bare string
        // is an SDK convenience and is rejected by the raw endpoint on some keys.
        AiSettings s = new AiSettings();
        s.apiKey = "";
        CloudLlm.Interaction it = CloudLlm.create(s, "hello", null, null);
        assertFalse(it.ok);
        assertEquals("API key set nahi hai", it.error);
    }

    /* --------------------------- wires / modes ------------------------ */

    @Test public void noInternetModeNeverUsesTheCloud() {
        AiSettings s = new AiSettings();
        s.mode = AiSettings.MODE_OFFLINE_ONLY;
        s.apiKey = "AIzaSyExampleKeyValue123";
        assertFalse("NO INTERNET means cloud off", s.cloudEnabled());
        assertTrue("but the on-device model must still be allowed", s.localPermitted());
        assertTrue(s.internetOff());
    }

    @Test public void cloudIsOffWithoutAKey() {
        AiSettings s = new AiSettings();
        s.mode = AiSettings.MODE_OFFLINE_FIRST;
        s.apiKey = "";
        assertFalse(s.cloudEnabled());
        s.apiKey = "short";
        assertFalse("a 5-char string is not an API key", s.cloudEnabled());
        s.apiKey = "AIzaSyExampleKeyValue123";
        assertTrue(s.cloudEnabled());
    }

    @Test public void geminiDefaultsToTheInteractionsWire() {
        AiSettings s = new AiSettings();
        assertEquals(AiSettings.PROVIDER_GEMINI, s.provider);
        assertFalse(s.isOpenAi());
        assertTrue(s.effectiveBaseUrl().startsWith("https://generativelanguage.googleapis.com"));
        assertTrue(s.effectiveModel().startsWith("gemini"));
        s.provider = AiSettings.PROVIDER_OPENAI;
        assertTrue(s.isOpenAi());
        assertTrue(s.effectiveBaseUrl().contains("openai.com"));
    }

    @Test public void followUpIsOnByDefaultAndPersistedInTheModel() {
        AiSettings s = new AiSettings();
        assertTrue("model dekhega, batayega — on by default", s.followUp);
    }
}
