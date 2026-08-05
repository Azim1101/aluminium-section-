package com.digitalalu.alu.calc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.digitalalu.alu.util.SecurityUtil;

import org.junit.Test;

/** PIN hashing + SecurityUtil — the v1.7 security upgrade. */
public class SecurityAndPinTest {

    @Test
    public void sha256IsDeterministicHex() {
        String h = SecurityUtil.sha256("1101");
        assertEquals(64, h.length());
        assertEquals(h, SecurityUtil.sha256("1101"));
        // well-known empty-string digest
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                SecurityUtil.sha256(""));
    }

    @Test
    public void saltIsRandom() {
        String a = SecurityUtil.randomSalt();
        String b = SecurityUtil.randomSalt();
        assertEquals(32, a.length());
        assertNotEquals(a, b);
    }

    @Test
    public void newPinIsHashedAndChecked() {
        PriceBook pb = new PriceBook();
        pb.setPin("4321");
        assertTrue(pb.hasHashedPin());
        assertTrue(pb.checkPin("4321"));
        assertFalse(pb.checkPin("1101"));
        assertFalse(pb.checkPin(null));
        // surrounding spaces are tolerated
        assertTrue(pb.checkPin(" 4321 "));
    }

    @Test
    public void defaultPinWorksOnFreshInstance() {
        PriceBook pb = new PriceBook();
        assertFalse(pb.hasHashedPin());
        assertTrue(pb.checkPin(PriceBook.DEFAULT_PIN));
        // successful legacy check migrates to hashed format
        assertTrue(pb.hasHashedPin());
        assertTrue(pb.checkPin(PriceBook.DEFAULT_PIN));
    }

    @Test
    public void hashContainsNoPinText() {
        PriceBook pb = new PriceBook();
        pb.setPin("9876");
        assertFalse(pb.pinHash.contains("9876"));
    }
}
