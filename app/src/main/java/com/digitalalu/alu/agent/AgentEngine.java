package com.digitalalu.alu.agent;

import com.digitalalu.alu.model.UserProfile;
import android.content.Context;

import java.util.*;

/**
 * AI agent engine with hybrid intelligence:
 * 1. Offline lightweight ONNX Runtime Neural Classifier (~268 KB)
 * 2. Keyword & rule-matching pattern engine
 * 3. Contextual fallback
 *
 * Supports Hinglish (Hindi + English mixed) completely offline.
 */
public class AgentEngine {

    public static final float ONNX_CONFIDENCE_THRESHOLD = 0.40f;
    public static final float ONNX_FALLBACK_THRESHOLD = 0.25f;

    private final Context context;
    private final List<Rule> rules = new ArrayList<>();
    private OnnxAgentClassifier onnxClassifier;
    private OnnxAgentClassifier.Prediction lastPrediction;

    public AgentEngine(Context context) {
        this.context = context;
        try {
            this.onnxClassifier = new OnnxAgentClassifier(context);
        } catch (Throwable ignored) {
            this.onnxClassifier = null;
        }
        buildKnowledgeBase();
    }

    /** Returns whether the on-device ONNX model is initialized and ready */
    public boolean isOnnxReady() {
        return onnxClassifier != null && onnxClassifier.isReady();
    }

    /** Returns the latest ONNX prediction (or null if none) */
    public OnnxAgentClassifier.Prediction getLastPrediction() {
        return lastPrediction;
    }

    /** Process user input and return agent response */
    public String respond(String userInput) {
        if (userInput == null) userInput = "";
        String input = userInput.trim().toLowerCase();
        lastPrediction = null;

        if (input.isEmpty()) {
            return pickRandom(greetings());
        }

        // 1. Primary: ONNX Neural Network Model Inference
        if (onnxClassifier != null && onnxClassifier.isReady()) {
            try {
                OnnxAgentClassifier.Prediction pred = onnxClassifier.predict(userInput);
                if (pred != null) {
                    lastPrediction = pred;
                    if (pred.confidence >= ONNX_CONFIDENCE_THRESHOLD) {
                        String resp = getResponseForIntent(pred.intent, input);
                        if (resp != null && !resp.isEmpty()) {
                            return resp;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Secondary: Rule-matching pattern engine
        for (Rule rule : rules) {
            if (rule.matches(input)) {
                return rule.respond(input);
            }
        }

        // 3. Tertiary: Moderate-confidence ONNX prediction fallback
        if (lastPrediction != null && lastPrediction.confidence >= ONNX_FALLBACK_THRESHOLD) {
            String resp = getResponseForIntent(lastPrediction.intent, input);
            if (resp != null && !resp.isEmpty()) {
                return resp;
            }
        }

        // 4. Default fallback
        return fallback(input);
    }

    /** Get initial greeting message */
    public String getGreeting() {
        UserProfile profile = context != null ? UserProfile.load(context) : new UserProfile();
        String name = profile.name.isEmpty() ? "" : " " + profile.name;
        return "Namaste" + name + "!\uD83D\uDE4F\n\n" +
               "Main aapka ALU Assistant hoon (Powered by ONNX AI \uD83E\uDD16).\n\n" +
               "Aap mujhse yeh sab poochh sakte ho:\n\n" +
               "\u2022 App ke baare mein\n" +
               "\u2022 Aluminium calculations & naap\n" +
               "\u2022 Window types (ZED, DOMAL)\n" +
               "\u2022 Sutter & Muliya formulas\n" +
               "\u2022 Pipe cutting & waste bachat\n" +
               "\u2022 Price setup & customer data\n" +
               "\u2022 Manual PCO & sheet cutting\n" +
               "\u2022 Backup & Excel export\n\n" +
               "Bataiye, kaise help kar sakta hoon? \uD83D\uDE0A";
    }

    /** Central response dispatcher for intent labels */
    public String getResponseForIntent(String intent, String input) {
        if (intent == null) return null;

        switch (intent) {
            case "GREETINGS":
                return pickRandom(greetings());

            case "HOW_ARE_YOU":
                return "Main bilkul theek hoon! \uD83D\uDE0A Aap bataiye, aluminium ka kaam kaisa chal raha hai?";

            case "WHO_ARE_YOU":
                return "Main **ALU Assistant** hoon! \uD83E\uDD16\n\n" +
                    "Aapka on-device AI aluminium window calculation helper (ONNX Powered).\n" +
                    "App ke baare mein koi bhi sawaal poochh sakte ho.\n\n" +
                    "Main 100% offline kaam karta hoon — internet ki zaroorat nahi! \u2708";

            case "APP_INFO":
                return "Yeh **ALU Window** app hai! \uD83D\uDEE0\n\n" +
                    "Yeh aluminium window/door manufacturers ke liye banaya gaya hai.\n\n" +
                    "Features:\n" +
                    "\u2022 Window sections calculate karein\n" +
                    "\u2022 Sutter, Muliya, RP calculations\n" +
                    "\u2022 Pipe cutting plan banayein\n" +
                    "\u2022 Price book manage karein\n" +
                    "\u2022 Customer records save karein\n" +
                    "\u2022 Excel export karein\n" +
                    "\u2022 Manual PCO & Sheet cutting\n\n" +
                    "Sab kuch offline kaam karta hai! \uD83D\uDCAF";

            case "WINDOW_TYPES":
                return "App mein yeh window systems hain:\n\n" +
                    "\uD83D\uDD35 **ZED (Single Track)** — Standard single slider window\n" +
                    "\uD83D\uDFE2 **ZED (Double Track)** — Double track sliding window\n" +
                    "\uD83D\uDFE3 **DOMAL** — Premium double track system\n\n" +
                    "Har system ke different section sizes hote hain:\n" +
                    "\u2022 Frame\n" +
                    "\u2022 Sutter (shutter)\n" +
                    "\u2022 Muliya (interlock)\n" +
                    "\u2022 Glass bead / RP\n\n" +
                    "Settings mein apna system configure kar sakte ho!";

            case "CALCULATION":
                return "Calculation karne ke liye:\n\n" +
                    "1. **Height & Width** enter karein (inch ya mm mein)\n" +
                    "2. **Quantity** set karein\n" +
                    "3. **System** select karein (ZED/DOMAL)\n" +
                    "4. Auto-calculate ho jayega!\n\n" +
                    "App automatically:\n" +
                    "\u2022 Sutter size calculate karta hai\n" +
                    "\u2022 Muliya pieces batata hai\n" +
                    "\u2022 Pipe cutting plan banata hai\n" +
                    "\u2022 Waste percentage dikhata hai\n\n" +
                    "Sab real-time hota hai! \u26A1";

            case "SUTTER":
                return "**Sutter** matlab window ka shutter (sliding panel).\n\n" +
                    "Sutter size calculate karne ke liye:\n" +
                    "\u2022 Frame ki inner width li jaati hai\n" +
                    "\u2022 Track depth subtract hota hai\n" +
                    "\u2022 Overlap adjust hota hai\n\n" +
                    "Formula:\n" +
                    "`Sutter Width = (Frame Width - overlaps) / 2`\n" +
                    "`Sutter Height = Frame Height - clearances`\n\n" +
                    "Settings mein overlap/clearance change kar sakte ho!";

            case "MULIYA":
                return "**Muliya** (Interlock) — yeh sutter ko frame se lock karta hai.\n\n" +
                    "Types:\n" +
                    "\u2022 **Short Muliya** — small windows\n" +
                    "\u2022 **Long Muliya** — large windows\n" +
                    "\u2022 **T-Muliya** — T-shape windows\n\n" +
                    "Quantity window ki height pe depend karti hai:\n" +
                    "\u2022 24\" se kam = 1 muliya\n" +
                    "\u2022 24\"-48\" = 2 muliya\n" +
                    "\u2022 48\"+ = 3 muliya\n\n" +
                    "Formula settings mein adjust ho sakta hai! \uD83D\uDD27";

            case "PIPE_CUTTING":
                return "**Pipe Cutting Plan** automatically banta hai!\n\n" +
                    "App bin-packing algorithm use karta hai:\n" +
                    "\u2022 ZED pipes (usually 21ft/252\")\n" +
                    "\u2022 DOMAL pipes (usually 21ft/252\")\n\n" +
                    "Har pipe mein multiple cuts fit hote hain\n" +
                    "Waste minimum karne ke liye optimize hota hai\n\n" +
                    "Result mein dikhega:\n" +
                    "\u2022 Total pipes chahiye\n" +
                    "\u2022 Har pipe mein kya-kya cut hoga\n" +
                    "\u2022 Kitna waste hoga\n" +
                    "\u2022 Waste %\n\n" +
                    "Manual PCO mode bhi hai custom cutting ke liye!";

            case "PRICE":
                return "**Price System** setup karne ke liye:\n\n" +
                    "1. Top bar mein \u20B9 (Rupee) button dabao\n" +
                    "2. Section-wise rate enter karo:\n" +
                    "   \u2022 Frame rate (per inch/mm)\n" +
                    "   \u2022 Sutter rate\n" +
                    "   \u2022 Muliya rate\n" +
                    "   \u2022 RP/Glass bead rate\n" +
                    "   \u2022 Labour charge\n" +
                    "   \u2022 Profit margin\n\n" +
                    "3. Alag-alag systems ke different rates rakh sakte ho\n\n" +
                    "Total cost automatically calculate hoga! \uD83D\uDCB0";

            case "CUSTOMER":
                return "**Customer Records** manage karne ke liye:\n\n" +
                    "\uD83D\uDCBE **Save:**\n" +
                    "Menu \u2192 Save to Customer\n" +
                    "Name, Mobile, Village enter karo\n\n" +
                    "\uD83D\uDCDC **View:**\n" +
                    "Profile icon (top bar) \u2192 PIN enter karo\n" +
                    "Saare saved customers dikhenge\n\n" +
                    "\u2022 Customer ki saari window details\n" +
                    "\u2022 Pipe cutting plan\n" +
                    "\u2022 Load karke edit kar sakte ho\n" +
                    "\u2022 Share (JSON) kar sakte ho\n\n" +
                    "PIN protected hai — data safe hai! \uD83D\uDD12";

            case "USER_PROFILE":
                return getProfileResponse();

            case "BACKUP":
                return "**Data Backup** karne ke liye:\n\n" +
                    "Menu \u2192 \"Backup data\"\n\n" +
                    "Saara data ek JSON file mein save hoga:\n" +
                    "\u2022 Customer records\n" +
                    "\u2022 Price settings\n" +
                    "\u2022 User profile\n" +
                    "\u2022 Custom formulas\n\n" +
                    "File ko Google Drive / email pe save karo\n\n" +
                    "**Restore:** Menu \u2192 \"Restore data\" \u2192 select backup file\n\n" +
                    "Regular backup lena zaroori hai! \uD83D\uDCC1";

            case "EXPORT":
                return "**Excel Export** karne ke liye:\n\n" +
                    "Menu \u2192 \"Share cutting images\"\n\n" +
                    "Ya customer detail mein \"Share\" button dabao\n\n" +
                    "Export formats:\n" +
                    "\u2022 JSON (app-to-app transfer)\n" +
                    "\u2022 Cutting images (share via WhatsApp etc)\n\n" +
                    "Customer data JSON mein share kar sakte ho —\n" +
                    "doosre phone pe restore ho jayega!";

            case "SETTINGS":
                return "**Settings** mein aap configure kar sakte ho:\n\n" +
                    "\u2022 **Unit** — Inch ya Millimeter\n" +
                    "\u2022 **Stock length** — Pipe ki default length\n" +
                    "\u2022 **Overlap** — Window overlap amounts\n" +
                    "\u2022 **Muliya formula** — Custom muliya rules\n" +
                    "\u2022 **PIN** — App security PIN\n" +
                    "\u2022 **Custom formulas** — Apne hisaab se\n\n" +
                    "Gear icon (top bar) se settings open karo! \u2699";

            case "MANUAL_PCO":
                return "**Manual PCO & Sheet Cutting** — custom cutting mode!\n\n" +
                    "Jab aapko:\n" +
                    "\u2022 Custom pipe lengths chahiye\n" +
                    "\u2022 Sheet material cut karna ho\n" +
                    "\u2022 Non-standard windows banani ho\n\n" +
                    "Menu \u2192 \"Manual PCO & Sheet Cutting\"\n\n" +
                    "Manual entries karke cutting plan bana sakte ho!";

            case "THANKS":
                return pickRandom(thanksResponses());

            case "GOODBYE":
                return pickRandom(goodbyeResponses());

            case "ZED":
                return "**ZED** — Single/Double track aluminium window system.\n\n" +
                    "Types:\n" +
                    "\u2022 **ZED Single Track** — Basic sliding window\n" +
                    "\u2022 **ZED Double Track** — Do tracks pe sliding\n\n" +
                    "Sections:\n" +
                    "\u2022 Frame — Outer structure\n" +
                    "\u2022 Sutter — Sliding panel\n" +
                    "\u2022 Muliya — Locking interlock\n" +
                    "\u2022 RP — Glass bead/retainer\n\n" +
                    "ZED ke pipe length usually 252\" (21ft) hoti hai.";

            case "DOMAL":
                return "**DOMAL** — Premium double track aluminium system.\n\n" +
                    "Features:\n" +
                    "\u2022 Stronger frame\n" +
                    "\u2022 Better insulation\n" +
                    "\u2022 Smooth sliding\n" +
                    "\u2022 Premium finish\n\n" +
                    "DOMAL sections ZED se different hote hain:\n" +
                    "\u2022 Alag dimensions\n" +
                    "\u2022 Alag pricing\n" +
                    "\u2022 Alag pipe stock\n\n" +
                    "Settings mein DOMAL ke specific rates set kar sakte ho!";

            case "HELP":
                return "Main aapki in cheezon mein help kar sakta hoon:\n\n" +
                    "\uD83D\uDD27 **App Guide**\n" +
                    "\u2022 \"app kya hai\" — App ke features\n" +
                    "\u2022 \"settings kya hai\" — Settings guide\n" +
                    "\u2022 \"price kaise set kare\" — Price setup\n\n" +
                    "\uD83D\uDCCA **Calculations**\n" +
                    "\u2022 \"calculation kaise kare\" — Calc guide\n" +
                    "\u2022 \"sutter kya hai\" — Sutter details\n" +
                    "\u2022 \"muliya kya hai\" — Muliya details\n" +
                    "\u2022 \"pipe cutting\" — Cutting plan\n\n" +
                    "\uD83D\uDC64 **Profile**\n" +
                    "\u2022 \"my profile\" — Aapki details\n\n" +
                    "\uD83D\uDCC1 **Data**\n" +
                    "\u2022 \"backup kaise kare\" — Data backup\n" +
                    "\u2022 \"export kaise kare\" — Excel/JSON export";

            case "UNITS":
                return "**Units** — App mein do units hain:\n\n" +
                    "\u2022 **Inch (in)** — Common measurement\n" +
                    "\u2022 **Millimeter (mm)** — Metric measurement\n\n" +
                    "Settings mein change kar sakte ho.\n" +
                    "Conversion: 1 inch = 25.4 mm\n\n" +
                    "Jo unit select karte ho, sab calculations usi mein hongi!";

            case "WASTE":
                return "**Waste** — Bachi hui pipe jo use nahi hoti.\n\n" +
                    "App **bin-packing algorithm** use karta hai:\n" +
                    "\u2022 Minimum waste nikalta hai\n" +
                    "\u2022 Cuts optimally arrange karta hai\n" +
                    "\u2022 Pipe count minimize karta hai\n\n" +
                    "Result mein dikhta hai:\n" +
                    "\u2022 Total waste (inches/mm)\n" +
                    "\u2022 Waste percentage (%)\n\n" +
                    "Lower waste = zyada profit! \uD83D\uDCC8";

            case "FORMULA":
                return "**Custom Formulas** — Apne hisaab se calculations!\n\n" +
                    "Menu \u2192 \"Formula\"\n\n" +
                    "Aap bana sakte ho:\n" +
                    "\u2022 Custom muliya rules\n" +
                    "\u2022 Special overlap values\n" +
                    "\u2022 Non-standard clearances\n" +
                    "\u2022 Apne section dimensions\n\n" +
                    "Har system (ZED/DOMAL) ke alag formulas rakh sakte ho.\n\n" +
                    "Custom formulas aapke business ke unique requirements ke liye hain!";

            default:
                return null;
        }
    }

    // ======================== KNOWLEDGE BASE RULES ========================

    private void buildKnowledgeBase() {
        addRule(new Rule(
            new String[]{"hi", "hello", "hey", "namaste", "namaskar", "hlo", "hii"},
            (input) -> getResponseForIntent("GREETINGS", input)
        ));

        addRule(new Rule(
            new String[]{"how are you", "kaise ho", "kaisa hai", "kaise ho bhai", "kya haal"},
            (input) -> getResponseForIntent("HOW_ARE_YOU", input)
        ));

        addRule(new Rule(
            new String[]{"app kya hai", "app ke baare", "about app", "what is this app", "ye app", "ye kya hai", "app ka naam"},
            (input) -> getResponseForIntent("APP_INFO", input)
        ));

        addRule(new Rule(
            new String[]{"window types", "window ka type", "kitne type", "types of window", "window system", "system types"},
            (input) -> getResponseForIntent("WINDOW_TYPES", input)
        ));

        addRule(new Rule(
            new String[]{"calculation", "calculate", "hisab", "calc kaise", "calculation kaise", "kaise calculate", "measurement"},
            (input) -> getResponseForIntent("CALCULATION", input)
        ));

        addRule(new Rule(
            new String[]{"sutter", "shutter", "sutter kya", "sutter calculation", "shutter size"},
            (input) -> getResponseForIntent("SUTTER", input)
        ));

        addRule(new Rule(
            new String[]{"muliya", "muliya kya", "interlock", "muliya calculation"},
            (input) -> getResponseForIntent("MULIYA", input)
        ));

        addRule(new Rule(
            new String[]{"pipe cutting", "pipe cut", "cutting plan", "cutting details", "pipe kaise kate"},
            (input) -> getResponseForIntent("PIPE_CUTTING", input)
        ));

        addRule(new Rule(
            new String[]{"price", "daam", "rate", "ke rate", "price kaise", "price setup", "cost"},
            (input) -> getResponseForIntent("PRICE", input)
        ));

        addRule(new Rule(
            new String[]{"customer", "customer record", "save customer", "customer kaise", "customer data"},
            (input) -> getResponseForIntent("CUSTOMER", input)
        ));

        addRule(new Rule(
            new String[]{"my profile", "user profile", "mera profile", "profile kya", "owner profile", "apna profile", "profile update"},
            (input) -> getResponseForIntent("USER_PROFILE", input)
        ));

        addRule(new Rule(
            new String[]{"backup", "backup kaise", "data save", "data backup", "data kahan"},
            (input) -> getResponseForIntent("BACKUP", input)
        ));

        addRule(new Rule(
            new String[]{"export", "excel", "xlsx", "export kaise", "excel export"},
            (input) -> getResponseForIntent("EXPORT", input)
        ));

        addRule(new Rule(
            new String[]{"settings", "setting", "configuration", "setup", "settings kya", "settings kaise"},
            (input) -> getResponseForIntent("SETTINGS", input)
        ));

        addRule(new Rule(
            new String[]{"manual pco", "pco", "manual cutting", "sheet cutting", "manual", "manual kaise"},
            (input) -> getResponseForIntent("MANUAL_PCO", input)
        ));

        addRule(new Rule(
            new String[]{"thanks", "thank you", "shukriya", "dhanyavad", "thank"},
            (input) -> getResponseForIntent("THANKS", input)
        ));

        addRule(new Rule(
            new String[]{"bye", "goodbye", "alvida", "tata", "bye bye", "chalta hoon"},
            (input) -> getResponseForIntent("GOODBYE", input)
        ));

        addRule(new Rule(
            new String[]{"who are you", "tum kaun", "kaun ho", "your name", "tera naam", "apna naam"},
            (input) -> getResponseForIntent("WHO_ARE_YOU", input)
        ));

        addRule(new Rule(
            new String[]{"zed", "zed kya hai", "zed system", "zed section"},
            (input) -> getResponseForIntent("ZED", input)
        ));

        addRule(new Rule(
            new String[]{"domal", "domal kya", "domal system", "domal section"},
            (input) -> getResponseForIntent("DOMAL", input)
        ));

        addRule(new Rule(
            new String[]{"help", "madad", "help karo", "madad karo", "kya kar sakte ho", "help me"},
            (input) -> getResponseForIntent("HELP", input)
        ));

        addRule(new Rule(
            new String[]{"unit", "inch", "mm", "millimeter", "inch vs mm", "unit kya"},
            (input) -> getResponseForIntent("UNITS", input)
        ));

        addRule(new Rule(
            new String[]{"waste", "waste kya", "kitna waste", "waste percentage", "scrap"},
            (input) -> getResponseForIntent("WASTE", input)
        ));

        addRule(new Rule(
            new String[]{"formula", "custom formula", "apna formula", "formula kaise", "formula kya"},
            (input) -> getResponseForIntent("FORMULA", input)
        ));
    }

    private void addRule(Rule rule) {
        rules.add(rule);
    }

    private static class Rule {
        final String[] keywords;
        final ResponseGenerator generator;

        Rule(String[] keywords, ResponseGenerator generator) {
            this.keywords = keywords;
            this.generator = generator;
        }

        boolean matches(String input) {
            for (String kw : keywords) {
                if (input.contains(kw)) return true;
            }
            return false;
        }

        String respond(String input) {
            return generator.generate(input);
        }
    }

    interface ResponseGenerator {
        String generate(String input);
    }

    // ======================== HELPERS ========================

    private String[] greetings() {
        UserProfile p = context != null ? UserProfile.load(context) : new UserProfile();
        String name = p.name.isEmpty() ? "" : " " + p.name;
        return new String[]{
            "Namaste" + name + "! \uD83D\uDE4F Bataiye, kya help chahiye?",
            "Hello" + name + "! \uD83D\uDE0A Kaise help kar sakta hoon?",
            "Hi" + name + "! \uD83D\uDC4B Kya sawaal hai aapka?"
        };
    }

    private String[] thanksResponses() {
        return new String[]{
            "Aapka swagat hai! \uD83D\uDE0A Aur koi sawaal ho toh poochhiye.",
            "Koi baat nahi! Kabhi bhi help chahiye toh yahan hoon. \uD83D\uDE4F",
            "Thank you! Aluminium ka koi aur sawaal ho toh zaroor poochhiye! \uD83D\uDE0A"
        };
    }

    private String[] goodbyeResponses() {
        return new String[]{
            "Alvida! \uD83D\uDE4F Jab bhi zaroorat ho, yahan hoon.",
            "Bye bye! Kaam mein shubhkamnayein! \uD83D\uDE0A",
            "Tata! Aluminium ka kaam badhiya chale! \uD83D\uDCAA"
        };
    }

    private String pickRandom(String[] options) {
        return options[new Random().nextInt(options.length)];
    }

    private String fallback(String input) {
        String[] fallbacks = new String[]{
            "Hmm, yeh mujhe samajh nahi aaya. \uD83E\uDD14\n\n" +
            "\"help\" likhiye — main bataunga kya-kya kar sakta hoon!",

            "Sorry, is baare mein mujhe zyada info nahi hai. \uD83D\uDE15\n\n" +
            "Aap \"help\" type karke dekhein — saare topics mil jayenge!",

            "Yeh topic abhi mere paas nahi hai. \u2753\n\n" +
            "Aap try karein:\n\u2022 \"app kya hai\"\n\u2022 \"calculation kaise\"\n\u2022 \"help\"",

            "Mujhe maaf karein, samajh nahi aaya. \uD83D\uDE4F\n\n" +
            "Different words mein poochhiye ya \"help\" likhiye!"
        };
        return fallbacks[new Random().nextInt(fallbacks.length)];
    }

    private String getProfileResponse() {
        UserProfile p = context != null ? UserProfile.load(context) : new UserProfile();
        if (p.isEmpty()) {
            return "Profile abhi set nahi hai!\n\n" +
                   "Menu \u2192 \"My Profile\" se set karo:\n" +
                   "\u2022 Name\n" +
                   "\u2022 Mobile Number\n" +
                   "\u2022 Address\n\n" +
                   "Profile mein aapki company/dukaan ki details save hongi.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDC64 **Aapka Profile:**\n\n");
        sb.append("Name: ").append(p.name.isEmpty() ? "-" : p.name).append("\n");
        sb.append("Mobile: ").append(p.mobile.isEmpty() ? "-" : p.mobile).append("\n");
        sb.append("Address: ").append(p.address.isEmpty() ? "-" : p.address).append("\n");
        sb.append("\nMenu \u2192 \"My Profile\" se update kar sakte ho!");
        return sb.toString();
    }

    public void close() {
        if (onnxClassifier != null) {
            onnxClassifier.close();
            onnxClassifier = null;
        }
    }
}
