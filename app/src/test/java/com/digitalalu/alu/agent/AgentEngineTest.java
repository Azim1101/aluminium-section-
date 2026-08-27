package com.digitalalu.alu.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for AgentEngine and OnnxAgentClassifier.
 */
public class AgentEngineTest {

    @Test
    public void engineInitializesSafelyWithoutContext() {
        AgentEngine engine = new AgentEngine(null);
        String greeting = engine.getGreeting();
        assertNotNull(greeting);
        assertTrue(greeting.contains("ALU Assistant"));
    }

    @Test
    public void rulesMatchCoreIntents() {
        AgentEngine engine = new AgentEngine(null);

        // Greetings
        String resHi = engine.respond("hello");
        assertTrue(resHi.contains("Namaste") || resHi.contains("Hello") || resHi.contains("Hi"));

        // Calculation
        String resCalc = engine.respond("calculation kaise kare");
        assertTrue(resCalc.contains("Height & Width"));

        // Shutter / Sutter
        String resSutter = engine.respond("sutter calculation formula");
        assertTrue(resSutter.contains("Sutter Width"));

        // Muliya
        String resMuliya = engine.respond("muliya details");
        assertTrue(resMuliya.contains("Muliya"));

        // Pipe cutting
        String resPipe = engine.respond("pipe cutting plan");
        assertTrue(resPipe.contains("Pipe Cutting Plan"));

        // Price
        String resPrice = engine.respond("price setup");
        assertTrue(resPrice.contains("Price System"));

        // Customer
        String resCustomer = engine.respond("customer record");
        assertTrue(resCustomer.contains("Customer Records"));

        // Backup
        String resBackup = engine.respond("backup kaise");
        assertTrue(resBackup.contains("Data Backup"));

        // Export
        String resExport = engine.respond("excel export");
        assertTrue(resExport.contains("Excel Export"));

        // DOMAL & ZED
        String resDomal = engine.respond("domal system");
        assertTrue(resDomal.contains("DOMAL"));

        String resZed = engine.respond("zed section");
        assertTrue(resZed.contains("ZED"));

        // Manual PCO
        String resPco = engine.respond("manual pco");
        assertTrue(resPco.contains("Manual PCO"));
    }

    @Test
    public void intentDispatcherProducesResponses() {
        AgentEngine engine = new AgentEngine(null);

        assertNotNull(engine.getResponseForIntent("GREETINGS", "hi"));
        assertNotNull(engine.getResponseForIntent("HOW_ARE_YOU", "kaise ho"));
        assertNotNull(engine.getResponseForIntent("WHO_ARE_YOU", "who are you"));
        assertNotNull(engine.getResponseForIntent("APP_INFO", "app kya hai"));
        assertNotNull(engine.getResponseForIntent("WINDOW_TYPES", "window types"));
        assertNotNull(engine.getResponseForIntent("CALCULATION", "calculation"));
        assertNotNull(engine.getResponseForIntent("SUTTER", "sutter"));
        assertNotNull(engine.getResponseForIntent("MULIYA", "muliya"));
        assertNotNull(engine.getResponseForIntent("PIPE_CUTTING", "pipe cutting"));
        assertNotNull(engine.getResponseForIntent("PRICE", "price"));
        assertNotNull(engine.getResponseForIntent("CUSTOMER", "customer"));
        assertNotNull(engine.getResponseForIntent("USER_PROFILE", "my profile"));
        assertNotNull(engine.getResponseForIntent("BACKUP", "backup"));
        assertNotNull(engine.getResponseForIntent("EXPORT", "export"));
        assertNotNull(engine.getResponseForIntent("SETTINGS", "settings"));
        assertNotNull(engine.getResponseForIntent("MANUAL_PCO", "manual pco"));
        assertNotNull(engine.getResponseForIntent("ZED", "zed"));
        assertNotNull(engine.getResponseForIntent("DOMAL", "domal"));
        assertNotNull(engine.getResponseForIntent("HELP", "help"));
        assertNotNull(engine.getResponseForIntent("UNITS", "unit"));
        assertNotNull(engine.getResponseForIntent("WASTE", "waste"));
        assertNotNull(engine.getResponseForIntent("FORMULA", "formula"));
        assertNotNull(engine.getResponseForIntent("THANKS", "thanks"));
        assertNotNull(engine.getResponseForIntent("GOODBYE", "bye"));
    }

    @Test
    public void fallbackOnUnknownInput() {
        AgentEngine engine = new AgentEngine(null);
        String response = engine.respond("xyzrandomgibberish987654321");
        assertNotNull(response);
        assertTrue(response.contains("help") || response.contains("samajh nahi"));
    }

    @Test
    public void classifierVectorizeProducesValidVector() {
        OnnxAgentClassifier classifier = new OnnxAgentClassifier(null);
        float[] vec = classifier.vectorize("shutter sutter pipe cutting calculation");
        assertNotNull(vec);
        assertEquals(classifier.getVocabSize(), vec.length);

        // Verify L2 norm is either 0 (empty) or approx 1.0
        double sumSq = 0;
        for (float v : vec) sumSq += v * v;
        if (sumSq > 0) {
            assertTrue(Math.abs(Math.sqrt(sumSq) - 1.0) < 1e-4);
        }
    }

    @Test
    public void qwenIntegrationHandlesUninstalledStateGracefully() {
        AgentEngine engine = new AgentEngine(null);
        assertFalse(engine.isQwenActive());
        assertNotNull(engine.getGreeting());
    }
}
