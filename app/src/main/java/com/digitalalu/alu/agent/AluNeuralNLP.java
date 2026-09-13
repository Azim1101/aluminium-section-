package com.digitalalu.alu.agent;

import com.digitalalu.alu.model.WindowItem;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-Device NLP & Semantic Intent Parser.
 * Handles broken ("tute-fute"), typo-ridden Hindi, Hinglish, Gujarati, and English commands
 * common in Indian aluminium fabrication workshops.
 */
public class AluNeuralNLP {

    public enum Intent {
        ADD_WINDOW,
        CALCULATE_SINGLE,
        VIEW_ESTIMATE,
        VIEW_CUTTING,
        VIEW_COSTING,
        UPDATE_PRICE,
        SHOW_WINDOWS,
        DELETE_LAST,
        CLEAR_SHEET,
        SAVE_CUSTOMER,
        LIST_CUSTOMERS,
        MANUAL_CUTTING,
        SHARE_EXPORT,
        EXPLAIN_SYSTEM,
        EXPLAIN_FORMULA,
        GREETING,
        HELP,
        UNKNOWN
    }

    public static class ParsedEntity {
        public Intent intent = Intent.UNKNOWN;
        public double height = 0;
        public double width = 0;
        public int sutter = 2;
        public int nos = 1;
        public int system = WindowItem.ZED;
        public boolean hasDimensions = false;

        public String customerName = "";
        public String mobileNumber = "";
        public String village = "";
        public double targetRate = 0;
        public String rateType = "ALU"; // "ALU" or "GLASS"
        public String rawInput = "";
    }

    public static ParsedEntity parse(String raw) {
        ParsedEntity entity = new ParsedEntity();
        if (raw == null) return entity;
        entity.rawInput = raw;

        String text = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\"\'’]", " ")
                .replaceAll("[\\t\\r\\n]+", " ");

        // Detect System (ZED or DOMAL)
        if (text.contains("domal") || text.contains("domel") || text.contains("doml")) {
            entity.system = WindowItem.DOMAL;
        } else if (text.contains("zed") || text.contains("jad") || text.contains("single track")) {
            entity.system = WindowItem.ZED;
        }

        // Detect Sutter count
        parseSutterCount(text, entity);

        // Detect Quantity / Nos
        parseQuantity(text, entity);

        // Detect Dimensions (e.g. 48x60, 4ft x 5ft, etc.)
        parseDimensions(text, entity);

        // Detect Rates
        parseRates(text, entity);

        // Detect Customer Info
        parseCustomerInfo(text, entity);

        // Determine Primary Intent
        entity.intent = classifyIntent(text, entity);

        return entity;
    }

    private static void parseSutterCount(String text, ParsedEntity entity) {
        Pattern p = Pattern.compile("(\\d+)\\s*(?:sutter|shutter|sutar|sutr|track|door|panel|palla|palte)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                int s = Integer.parseInt(m.group(1));
                if (s >= 1 && s <= 6) entity.sutter = s;
            } catch (Exception ignored) {}
        } else if (text.contains("do sutter") || text.contains("double sutter") || text.contains("2 shutter")) {
            entity.sutter = 2;
        } else if (text.contains("teen sutter") || text.contains("triple sutter") || text.contains("3 shutter")) {
            entity.sutter = 3;
        } else if (text.contains("char sutter") || text.contains("4 shutter")) {
            entity.sutter = 4;
        } else if (text.contains("single sutter") || text.contains("ek sutter") || text.contains("1 shutter")) {
            entity.sutter = 1;
        }
    }

    private static void parseQuantity(String text, ParsedEntity entity) {
        // e.g., "2 piece", "3 nos", "4 khidki", "5 window", "2 banao"
        Pattern p = Pattern.compile("(\\d+)\\s*(?:pcs|pc|piece|peac|nos|no|khidki|khidkiya|window|widno|banao|chahiye)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                int q = Integer.parseInt(m.group(1));
                if (q > 0 && q <= 500) entity.nos = q;
            } catch (Exception ignored) {}
        }
    }

    private static void parseDimensions(String text, ParsedEntity entity) {
        // Check foot notation: "4 foot 5 foot", "4 ft 5 ft", "4' 5'"
        Pattern footP = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:foot|ft|feet|fute)\\s*(?:by|x|\\*|\\/|,|\\s+)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:foot|ft|feet|fute)?");
        Matcher footM = footP.matcher(text);
        if (footM.find()) {
            try {
                double h = Double.parseDouble(footM.group(1)) * 12.0;
                double w = Double.parseDouble(footM.group(2)) * 12.0;
                if (h > 0 && w > 0) {
                    entity.height = h;
                    entity.width = w;
                    entity.hasDimensions = true;
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Standard H x W patterns: 48x60, 48*60, 48 by 60, 48 X 60, 48/60, 48 60
        Pattern dimP = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:x|\\*|by|cross|\\/|\\s+)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:inch|in|mm)?");
        Matcher dimM = dimP.matcher(text);
        while (dimM.find()) {
            try {
                double v1 = Double.parseDouble(dimM.group(1));
                double v2 = Double.parseDouble(dimM.group(2));
                // Discard small numbers that might be quantity or sutter count
                if (v1 >= 6 && v2 >= 6) {
                    if (text.contains("mm") || text.contains("mili")) {
                        // convert mm to inch
                        v1 = v1 / 25.4;
                        v2 = v2 / 25.4;
                    }
                    entity.height = v1;
                    entity.width = v2;
                    entity.hasDimensions = true;
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private static void parseRates(String text, ParsedEntity entity) {
        Pattern rP = Pattern.compile("(?:rate|bhav|daam|price)\\s*(?:karo|rakho|set|badlo)?\\s*(?:rs|inr|₹)?\\s*(\\d+(?:\\.\\d+)?)");
        Matcher rM = rP.matcher(text);
        if (rM.find()) {
            try {
                entity.targetRate = Double.parseDouble(rM.group(1));
                if (text.contains("glass") || text.contains("kaanch") || text.contains("kanch")) {
                    entity.rateType = "GLASS";
                } else {
                    entity.rateType = "ALU";
                }
            } catch (Exception ignored) {}
        }
    }

    private static void parseCustomerInfo(String text, ParsedEntity entity) {
        // Mobile number (10 digits)
        Pattern mobP = Pattern.compile("\\b([6-9]\\d{9})\\b");
        Matcher mobM = mobP.matcher(text);
        if (mobM.find()) {
            entity.mobileNumber = mobM.group(1);
        }

        // Name after "customer" or "party" or "naam"
        Pattern nameP = Pattern.compile("(?:customer|party|grahak|client|naam|name)\\s+([a-zA-Z\\u0900-\\u097F]+(?:\\s+[a-zA-Z\\u0900-\\u097F]+)?)");
        Matcher nameM = nameP.matcher(text);
        if (nameM.find()) {
            String n = nameM.group(1).trim();
            if (!n.equalsIgnoreCase("save") && !n.equalsIgnoreCase("karo") && !n.equalsIgnoreCase("list")) {
                entity.customerName = n;
            }
        }
    }

    private static Intent classifyIntent(String text, ParsedEntity entity) {
        // Clear / Delete all
        if (text.contains("clear") || text.contains("sab hatao") || text.contains("sab saaf")
                || text.contains("delete all") || text.contains("khali kardo") || text.contains("remove all")) {
            return Intent.CLEAR_SHEET;
        }

        // Delete last / remove window
        if (text.contains("last window") || text.contains("aakhri khidki") || text.contains("delete window")
                || text.contains("hata do") || text.contains("remove last")) {
            return Intent.DELETE_LAST;
        }

        // Update rate / price
        if (entity.targetRate > 0 && (text.contains("rate") || text.contains("bhav") || text.contains("set") || text.contains("karo"))) {
            return Intent.UPDATE_PRICE;
        }

        // Add Window command (even with broken Hindi / Hinglish like "widno bna do", "khidki add kro")
        boolean hasAddVerb = text.contains("add") || text.contains("banao") || text.contains("bana do")
                || text.contains("daal do") || text.contains("dalo") || text.contains("likho")
                || text.contains("save karo") || text.contains("create") || text.contains("jodo")
                || text.contains("chahiye") || text.contains("nayi window");

        if (entity.hasDimensions && (hasAddVerb || text.contains("window") || text.contains("khidki") || text.contains("widno") || text.contains("piece") || text.contains("zed") || text.contains("domal"))) {
            // If user explicitly asks for calculation only (e.g. "size batao", "hisab batao", "sutter kitna hoga")
            if (text.contains("hisab") || text.contains("batao") || text.contains("kya aayega") || text.contains("check karo") || text.contains("calculate")) {
                return Intent.CALCULATE_SINGLE;
            }
            return Intent.ADD_WINDOW;
        }

        // Single Calculation with dimensions
        if (entity.hasDimensions) {
            return Intent.CALCULATE_SINGLE;
        }

        // Costing / Pricing inquiries
        if (text.contains("cost") || text.contains("price") || text.contains("kharcha") || text.contains("bhav")
                || text.contains("daam") || text.contains("paisa") || text.contains("kitne rupaye") || text.contains("rate list")) {
            return Intent.VIEW_COSTING;
        }

        // Cutting Plan / Pipe cutting / Bin-packing
        if (text.contains("cutting") || text.contains("katne") || text.contains("pipe plan") || text.contains("pipes")
                || text.contains("kitne pipe") || text.contains("waste") || text.contains("scrap") || text.contains("bin pack")) {
            return Intent.VIEW_CUTTING;
        }

        // Estimate / Summary
        if (text.contains("estimate") || text.contains("summary") || text.contains("sheet") || text.contains("total window")
                || text.contains("sab hisab") || text.contains("overall")) {
            return Intent.VIEW_ESTIMATE;
        }

        // Show current windows
        if (text.contains("windows dikhao") || text.contains("show window") || text.contains("list dikhao")
                || text.contains("kya kya add hai") || text.contains("entries") || text.contains("kitni khidki")) {
            return Intent.SHOW_WINDOWS;
        }

        // Save Customer
        if ((text.contains("customer") || text.contains("party") || text.contains("grahak"))
                && (text.contains("save") || text.contains("jodo") || !entity.mobileNumber.isEmpty() || !entity.customerName.isEmpty())) {
            return Intent.SAVE_CUSTOMER;
        }

        // List Customers
        if (text.contains("customer") || text.contains("party") || text.contains("grahak")) {
            return Intent.LIST_CUSTOMERS;
        }

        // Manual PCO / Sheet cutting
        if (text.contains("manual") || text.contains("pco") || text.contains("sheet cutting")) {
            return Intent.MANUAL_CUTTING;
        }

        // Share / Export
        if (text.contains("share") || text.contains("whatsapp") || text.contains("excel") || text.contains("export") || text.contains("bhejo")) {
            return Intent.SHARE_EXPORT;
        }

        // System Explanation (ZED, DOMAL, etc.)
        if (text.contains("zed") || text.contains("domal") || text.contains("system") || text.contains("track")
                || text.contains("section") || text.contains("farq") || text.contains("difference")) {
            return Intent.EXPLAIN_SYSTEM;
        }

        // Formula Explanation
        if (text.contains("formula") || text.contains("sutter") || text.contains("shutter") || text.contains("muliya")
                || text.contains("interlock") || text.contains("rp") || text.contains("glass size")) {
            return Intent.EXPLAIN_FORMULA;
        }

        // Greeting
        if (text.contains("hi") || text.contains("hello") || text.contains("hey") || text.contains("namaste")
                || text.contains("salaam") || text.contains("kem cho") || text.contains("kaise ho") || text.contains("kya haal")) {
            return Intent.GREETING;
        }

        // Help
        if (text.contains("help") || text.contains("madad") || text.contains("guide") || text.contains("kya kar sakte ho")) {
            return Intent.HELP;
        }

        return Intent.UNKNOWN;
    }
}
