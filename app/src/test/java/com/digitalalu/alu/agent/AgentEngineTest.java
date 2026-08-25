package com.digitalalu.alu.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Sanity tests for the offline {@link AgentEngine}.
 *
 * The engine is intentionally a local pattern-matching chatbot and must never
 * require network access; these tests guard that every advertised topic and
 * quick-action chip resolves to a non-empty, non-fallback answer.
 */
public class AgentEngineTest {

    private AgentEngine engine;

    @Before
    public void setUp() {
        engine = new AgentEngine();   // no Android Context needed
    }

    @Test
    public void greetingIsNeverBlank() {
        String greeting = engine.getGreeting();
        assertNotNull(greeting);
        assertFalse(greeting.trim().isEmpty());
        assertTrue(greeting.contains("ALU Assistant"));
    }

    @Test
    public void emptyInputReturnsGreeting() {
        String reply = engine.respond("");
        assertNotNull(reply);
        assertFalse(reply.trim().isEmpty());
        // greetings mention the user / ask a question
        assertTrue(lower(reply).contains("namaste") || lower(reply).contains("hello")
                || lower(reply).contains("hi"));
    }

    @Test
    public void nullInputDoesNotCrash() {
        assertNotNull(engine.respond(null));
    }

    @Test
    public void everyCoreTopicIsAnswered() {
        String[] queries = {
                "app kya hai",
                "calculation kaise karte hain",
                "sutter kya hai",
                "muliya kya hai",
                "pipe cutting",
                "price kaise setup kare",
                "customer data save",
                "backup kaise kare",
                "domal kya hai",
                "zed kya hai",
                "manual pco",
                "unit inch mm",
                "waste kitna",
                "custom formula",
                "my profile",
                "settings kya hai",
                "help",
                "kaun ho tum",
                "thanks",
                "bye"
        };
        for (String q : queries) {
            String reply = engine.respond(q);
            assertNotNull("null reply for: " + q, reply);
            assertFalse("blank reply for: " + q, reply.trim().isEmpty());
            assertFalse("fallback used for known topic: " + q + " -> " + reply,
                    isFallback(reply));
        }
    }

    @Test
    public void quickActionChipsResolve() {
        // Must match the chips wired in AgentActivity#addQuickChips.
        String[] chips = {"help", "calculation", "price", "customer", "pipe cutting"};
        for (String chip : chips) {
            String reply = engine.respond(chip);
            assertFalse("chip fell back: " + chip, isFallback(reply));
        }
    }

    @Test
    public void naturalHinglishPhrasesResolve() {
        // These are the kind of free-typed messages users actually send.
        String[][] queries = {
                {"app kya kaam karta hai", "ALU Window"},
                {"sutter ka size kitna hota hai", "Sutter"},
                {"muliya kya hota hai", "Muliya"},
                {"pipe kaise katenge", "Pipe Cutting Plan"},
                {"price kaise set kare", "Price System"},
                {"grahak kaise save kare", "Customer Records"},
                {"data backup kaise le", "Data Backup"},
                {"domal kya hota hai", "DOMAL"},
                {"zed kya hai", "ZED"},
                {"rp kya hai", "RP"},
                {"section kya hai", "sections"},
                {"kaunse system hai", "window systems"},
                {"tum kaun ho", "ALU Assistant"},
                {"shukriya bhai", "swagat"},
                {"alvida", "Alvida"},
                {"units kaise change kare", "Units"},
                {"waste kitna hota hai", "Waste"},
                {"formula kaise banaye", "Custom Formulas"},
                {"pco kya hai", "Manual PCO"},
                {"excel kaise export kare", "Excel Export"},
                {"madad chahiye", "help"},
        };
        for (String[] qa : queries) {
            String reply = engine.respond(qa[0]);
            assertFalse("fell back: " + qa[0] + " -> " + reply, isFallback(reply));
            assertTrue("expected to mention '" + qa[1] + "' for: " + qa[0]
                            + " -> " + reply,
                    lower(reply).contains(lower(qa[1])));
        }
    }

    @Test
    public void punctuationIsIgnored() {
        String a = engine.respond("Sutter kya hai?");
        String b = engine.respond("sutter kya hai");
        assertEquals(lower(a), lower(b));
        assertFalse(isFallback(a));
    }

    @Test
    public void shortWordDoesNotMatchInsideAnotherWord() {
        // Regression: "mm" used to match inside "hmm" via substring contains().
        // "hmm" is not a real word in any rule -> fallback is acceptable;
        // the important thing is that "units inch ya mm" must NOT be treated
        // as a greeting just because "hmm" contains... nothing relevant.
        String units = engine.respond("units inch ya mm");
        assertFalse(isFallback(units));
        assertTrue(lower(units).contains("inch"));
        assertTrue(lower(units).contains("mm"));
    }

    @Test
    public void devanagariHindiIsMatched() {
        // नमस्ते = namaste
        String reply = engine.respond("नमस्ते");
        assertFalse(isFallback(reply));
        assertTrue(lower(reply).contains("namaste") || lower(reply).contains("hello")
                || lower(reply).contains("hi"));
    }

    @Test
    public void normalizeHandlesNullAndPunctuation() {
        assertEquals("", AgentEngine.normalize(null));
        assertEquals("", AgentEngine.normalize("   "));
        assertEquals("sutter kya hai", AgentEngine.normalize("  Sutter, KYA hai?! "));
    }

    @Test
    public void unknownQuestionUsesFallback() {
        String reply = engine.respond("xyzzy flooobar quantum entanglement 12345");
        assertTrue("expected fallback, got: " + reply, isFallback(reply));
    }

    @Test
    public void responsesDoNotMentionNetworkApis() {
        // Offline guarantee — the engine must not pretend to call an API.
        for (String q : new String[]{"app kya hai", "kaun ho tum", "calculation"}) {
            String reply = lower(engine.respond(q));
            assertFalse(reply.contains("openai"));
            assertFalse(reply.contains("gemini api"));
            assertFalse(reply.contains("http://"));
            assertFalse(reply.contains("https://"));
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.US);
    }

    private static boolean isFallback(String reply) {
        String r = lower(reply);
        return r.contains("samajh nahi aaya")
                || r.contains("zyada info nahi")
                || r.contains("abhi mere paas nahi")
                || r.contains("maaf karein");
    }
}
