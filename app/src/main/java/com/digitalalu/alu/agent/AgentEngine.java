package com.digitalalu.alu.agent;

import com.digitalalu.alu.model.UserProfile;
import android.content.Context;

import java.util.*;

/**
 * Local AI agent engine — pattern-matching chatbot with knowledge base.
 * Supports Hinglish (Hindi + English mixed).
 * No internet required.
 */
public class AgentEngine {

    private final Context context;
    private final List<Rule> rules = new ArrayList<>();

    public AgentEngine() {
        this(null);
    }

    public AgentEngine(Context context) {
        this.context = context;
        buildKnowledgeBase();
    }

    private UserProfile loadProfile() {
        if (context == null) return new UserProfile();
        return UserProfile.load(context);
    }

    /** Process user input and return agent response */
    public String respond(String userInput) {
        String input = normalize(userInput);

        if (input.isEmpty()) {
            return pickRandom(greetings());
        }

        // Score every rule and pick the best match (more keyword words = better).
        Rule best = null;
        int bestScore = 0;
        for (Rule rule : rules) {
            int score = rule.score(input);
            if (score > bestScore) {
                bestScore = score;
                best = rule;
            }
        }

        if (best != null) {
            return best.respond(input);
        }

        // Default fallback
        return fallback(input);
    }

    /**
     * Normalize user text: null-safe, lowercase, punctuation/extra spaces
     * collapsed. Words are kept so rule matching can use word boundaries.
     */
    static String normalize(String s) {
        if (s == null) return "";
        // Replace anything that is not a letter/digit/whitespace with a space,
        // then collapse whitespace. Devanagari letters are preserved by \p{L}.
        String t = s.toLowerCase(java.util.Locale.US)
                .replaceAll("[^\\p{L}\\p{N}\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }

    /** Get initial greeting message */
    public String getGreeting() {
        UserProfile profile = loadProfile();
        String name = profile.name.isEmpty() ? "" : " " + profile.name;
        return "Namaste" + name + "!\uD83D\uDE4F\n\n" +
               "Main aapka ALU Assistant hoon. Aap mujhse yeh sab poochh sakte ho:\n\n" +
               "\u2022 App ke baare mein\n" +
               "\u2022 Aluminium calculations\n" +
               "\u2022 Window types aur sections\n" +
               "\u2022 Pipe cutting details\n" +
               "\u2022 Price setup\n" +
               "\u2022 Data backup/restore\n" +
               "\u2022 Koi bhi sawaal!\n\n" +
               "Bataiye, kaise help kar sakta hoon? \uD83D\uDE0A";
    }

    // ======================== KNOWLEDGE BASE ========================

    private void buildKnowledgeBase() {

        // ----- Greetings -----
        addRule(new Rule(
            new String[]{"hi", "hello", "hey", "namaste", "namaskar", "namaskaram",
                         "hlo", "hii", "helo", "salaam", "salam", "ram ram",
                         "good morning", "good evening", "good night", "kya hal",
                         "kya haal hai", "kya haal", "kaise hain"},
            (input) -> pickRandom(greetings())
        ));

        // ----- How are you (moved after greetings; phrase matching disambiguates) -----
        addRule(new Rule(
            new String[]{"how are you", "how r u", "kaise ho aap", "kaisi ho",
                         "kya haal hai", "kya hal hai", "kya chala", "kya chal raha",
                         "kya ho raha"},
            (input) -> "Main bilkul theek hoon! \uD83D\uDE0A Aap bataiye, aluminium ka kaam kaisa chal raha hai?"
        ));

        // ----- App info -----
        addRule(new Rule(
            new String[]{"app kya hai", "app ke baare", "app ke bare", "app kya ha",
                         "app kya he", "yeh app", "ye app", "is app", "iss app",
                         "app kya karta", "app ka kaam", "app ka naam", "app features",
                         "about app", "what is this app", "what app", "ye kya hai",
                         "yeh kya hai", "kya hai app", "application", "app explain",
                         "app ke features", "app ke baare mein", "app ke bare mein",
                         "app kya kam karta", "app kya kaam karta"},
            (input) -> "Yeh **ALU Window** app hai! \uD83D\uDEE0\n\n" +
                "Yeh aluminium window/door manufacturers ke liye banaya gaya hai.\n\n" +
                "Features:\n" +
                "\u2022 Window sections calculate karein\n" +
                "\u2022 Sutter, Muliya, RP calculations\n" +
                "\u2022 Pipe cutting plan banayein\n" +
                "\u2022 Price book manage karein\n" +
                "\u2022 Customer records save karein\n" +
                "\u2022 Excel export karein\n" +
                "\u2022 Manual PCO & Sheet cutting\n\n" +
                "Sab kuch offline kaam karta hai! \uD83D\uDCAF"
        ));

        // ----- Window types -----
        addRule(new Rule(
            new String[]{"window types", "window ka type", "window ke type", "kitne type",
                         "kitne tarah", "types of window", "window system", "system types",
                         "kaunse system", "konse system", "which system", "which windows",
                         "window kitne", "systems", "window systems"},
            (input) -> "App mein yeh window systems hain:\n\n" +
                "\uD83D\uDD35 **ZED (Single Track)** — Standard single slider window\n" +
                "\uD83D\uDFE2 **ZED (Double Track)** — Double track sliding window\n" +
                "\uD83D\uDFE3 **DOMAL** — Premium double track system\n\n" +
                "Har system ke different section sizes hote hain:\n" +
                "\u2022 Frame\n" +
                "\u2022 Sutter (shutter)\n" +
                "\u2022 Muliya (interlock)\n" +
                "\u2022 Glass bead / RP\n\n" +
                "Settings mein apna system configure kar sakte ho!"
        ));

        // ----- Calculation -----
        addRule(new Rule(
            new String[]{"calculation", "calculate", "calc", "hisab", "hisaab",
                         "hisab kaise", "hisaab kaise", "calc kaise", "calculation kaise",
                         "calculation kaise kare", "calculation kaise karte", "kaise calculate",
                         "kaise calculate kare", "kaise count", "kaise gin", "kaise nikale",
                         "kaise nikal", "measurement", "measure", "measurements",
                         "kaam kaise kare", "kaise use kare", "how to calculate",
                         "how to use", "kaise banate", "kaise banta", "section kaise",
                         "size kaise", "section calculate", "window calculate",
                         "window kaise", "kaise nikalein", "kaise karte"},
            (input) -> "Calculation karne ke liye:\n\n" +
                "1. **Height & Width** enter karein (inch ya mm mein)\n" +
                "2. **Quantity** set karein\n" +
                "3. **System** select karein (ZED/DOMAL)\n" +
                "4. Auto-calculate ho jayega!\n\n" +
                "App automatically:\n" +
                "\u2022 Sutter size calculate karta hai\n" +
                "\u2022 Muliya pieces batata hai\n" +
                "\u2022 Pipe cutting plan banata hai\n" +
                "\u2022 Waste percentage dikhata hai\n\n" +
                "Sab real-time hota hai! \u26A1"
        ));

        // ----- Sutter -----
        addRule(new Rule(
            new String[]{"sutter", "sutar", "shutter", "shuter", "sutter kya",
                         "sutter kya hai", "sutter kya hota", "sutter calculation",
                         "sutter size", "shutter size", "sutter ka size", "sutter formula",
                         "sutter kaise", "sutter kitna", "shutter kya", "sliding panel"},
            (input) -> "**Sutter** matlab window ka shutter (sliding panel).\n\n" +
                "Sutter size calculate karne ke liye:\n" +
                "\u2022 Frame ki inner width li jaati hai\n" +
                "\u2022 Track depth subtract hota hai\n" +
                "\u2022 Overlap adjust hota hai\n\n" +
                "Formula:\n" +
                "`Sutter Width = (Frame Width - overlaps) / 2`\n" +
                "`Sutter Height = Frame Height - clearances`\n\n" +
                "Settings mein overlap/clearance change kar sakte ho!"
        ));

        // ----- Muliya -----
        addRule(new Rule(
            new String[]{"muliya", "muliya kya", "muliya kya hai", "muliya kya hota",
                         "muliya kitne", "muliya kitna", "muliya calculation",
                         "muliya formula", "muliya ka size", "muliya size",
                         "muliya kaise", "mulia", "interlock",
                         "interlocking", "t muliya", "long muliya", "short muliya"},
            (input) -> "**Muliya** (Interlock) — yeh sutter ko frame se lock karta hai.\n\n" +
                "Types:\n" +
                "\u2022 **Short Muliya** — small windows\n" +
                "\u2022 **Long Muliya** — large windows\n" +
                "\u2022 **T-Muliya** — T-shape windows\n\n" +
                "Quantity window ki height pe depend karti hai:\n" +
                "\u2022 24\" se kam = 1 muliya\n" +
                "\u2022 24\"-48\" = 2 muliya\n" +
                "\u2022 48\"+ = 3 muliya\n\n" +
                "Formula settings mein adjust ho sakta hai! \uD83D\uDD27"
        ));

        // ----- Pipe cutting -----
        addRule(new Rule(
            new String[]{"pipe cutting", "pipe cut", "pipe kat", "pipe kaise",
                         "pipe kaise kate", "pipe kaise katen", "pipe kitna",
                         "cutting plan", "cutting details", "cutting", "cut plan",
                         "kaise kate", "kaise katenge", "kaise kaaten", "kaise cutting",
                         "pipe detail", "pipe plan", "kitne pipe", "kitne pipe chahiye",
                         "pipe optimize", "stock length", "bin packing", "pipes",
                         "rod", "rod cutting", "patta cutting", "patta"},
            (input) -> "**Pipe Cutting Plan** automatically banta hai!\n\n" +
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
                "Manual PCO mode bhi hai custom cutting ke liye!"
        ));

        // ----- Price -----
        addRule(new Rule(
            new String[]{"price", "prices", "daam", "daam kya", "rate", "rates",
                         "ke rate", "rate kya", "rate kitna", "kitna rate",
                         "price kaise", "price setup", "price set", "price kaise set",
                         "price kaise set kare", "price kaise daale", "price kaise dale",
                         "cost", "costing", "paisa", "paise", "mehnga", "sasta",
                         "price book", "rate book", "price list", "rate list",
                         "kitna kharcha", "kitna paisa", "labour charge", "profit margin",
                         "mrp", "quotation", "quote"},
            (input) -> "**Price System** setup karne ke liye:\n\n" +
                "1. Top bar mein \u20B9 (Rupee) button dabao\n" +
                "2. Section-wise rate enter karo:\n" +
                "   \u2022 Frame rate (per inch/mm)\n" +
                "   \u2022 Sutter rate\n" +
                "   \u2022 Muliya rate\n" +
                "   \u2022 RP/Glass bead rate\n" +
                "   \u2022 Labour charge\n" +
                "   \u2022 Profit margin\n\n" +
                "3. Alag-alag systems ke different rates rakh sakte ho\n\n" +
                "Total cost automatically calculate hoga! \uD83D\uDCB0"
        ));

        // ----- Customer -----
        addRule(new Rule(
            new String[]{"customer", "customer record", "save customer", "customer kaise",
                         "customer kaise save", "customer data", "customer save",
                         "customer add", "customer list", "customer view", "customer dekh",
                         "customer dekho", "customer detail", "customer details",
                         "grahak", "grahak kaise", "grahak save", "grahak jodo",
                         "client", "clients", "customer delete", "customer edit",
                         "customer load", "customer share", "pin enter", "pin se customer"},
            (input) -> "**Customer Records** manage karne ke liye:\n\n" +
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
                "PIN protected hai — data safe hai! \uD83D\uDD12"
        ));

        // ----- User Profile -----
        addRule(new Rule(
            new String[]{"my profile", "user profile", "mera profile", "meri profile",
                         "profile kya", "profile kya hai", "owner profile", "apna profile",
                         "apni profile", "profile update", "profile kaise", "profile set",
                         "profile edit", "meri detail", "meri details", "my details",
                         "meri jankari", "shop profile", "company profile", "owner detail"},
            (input) -> getProfileResponse()
        ));
        // ----- Backup -----
        addRule(new Rule(
            new String[]{"backup", "backup kaise", "backup kaise le", "backup kaise kare",
                         "data backup", "data save", "data kahan", "data restore",
                         "restore", "restore kaise", "data wapas", "data recover",
                         "data transfer", "phone change", "naya phone", "new phone",
                         "data loss", "data chala gaya", "data kaise bachaye",
                         "data kaise save", "data export file", "json backup"},
            (input) -> "**Data Backup** karne ke liye:\n\n" +
                "Menu \u2192 \"Backup data\"\n\n" +
                "Saara data ek JSON file mein save hoga:\n" +
                "\u2022 Customer records\n" +
                "\u2022 Price settings\n" +
                "\u2022 User profile\n" +
                "\u2022 Custom formulas\n\n" +
                "File ko Google Drive / email pe save karo\n\n" +
                "**Restore:** Menu \u2192 \"Restore data\" \u2192 select backup file\n\n" +
                "Regular backup lena zaroori hai! \uD83D\uDCC1"
        ));

        // ----- Export -----
        addRule(new Rule(
            new String[]{"export", "export kaise", "export kaise kare", "excel",
                         "excel export", "excel file", "xlsx", "spreadsheet",
                         "share", "share kaise", "whatsapp", "whatsapp par",
                         "print", "print kaise", "pdf", "pdf kaise", "share cutting",
                         "share image", "share json", "send", "bhejo", "bhejna"},
            (input) -> "**Excel Export** karne ke liye:\n\n" +
                "Menu \u2192 \"Share cutting images\"\n\n" +
                "Ya customer detail mein \"Share\" button dabao\n\n" +
                "Export formats:\n" +
                "\u2022 JSON (app-to-app transfer)\n" +
                "\u2022 Cutting images (share via WhatsApp etc)\n\n" +
                "Customer data JSON mein share kar sakte ho —\n" +
                "doosre phone pe restore ho jayega!"
        ));

        // ----- Settings -----
        addRule(new Rule(
            new String[]{"settings", "setting", "configuration", "configure",
                         "setup", "set up", "settings kya", "settings kya hai",
                         "settings kaise", "settings kaise khole", "settings kaise open",
                         "gear", "gear icon", "change unit", "unit change",
                         "overlap change", "stock length change", "pin change",
                         "pin set", "pin set kaise", "change pin"},
            (input) -> "**Settings** mein aap configure kar sakte ho:\n\n" +
                "\u2022 **Unit** — Inch ya Millimeter\n" +
                "\u2022 **Stock length** — Pipe ki default length\n" +
                "\u2022 **Overlap** — Window overlap amounts\n" +
                "\u2022 **Muliya formula** — Custom muliya rules\n" +
                "\u2022 **PIN** — App security PIN\n" +
                "\u2022 **Custom formulas** — Apne hisaab se\n\n" +
                "Gear icon (top bar) se settings open karo! \u2699"
        ));

        // ----- Manual PCO -----
        addRule(new Rule(
            new String[]{"manual pco", "pco", "pco kya", "pco kya hai", "manual cutting",
                         "sheet cutting", "sheet cut", "manual cut", "manual",
                         "manual kaise", "manual mode", "custom cutting", "custom cut",
                         "custom pipe", "sheet kaise", "sheet kaise kate",
                         "manual sheet", "pco kaise"},
            (input) -> "**Manual PCO & Sheet Cutting** — custom cutting mode!\n\n" +
                "Jab aapko:\n" +
                "\u2022 Custom pipe lengths chahiye\n" +
                "\u2022 Sheet material cut karna ho\n" +
                "\u2022 Non-standard windows banani ho\n\n" +
                "Menu \u2192 \"Manual PCO & Sheet Cutting\"\n\n" +
                "Manual entries karke cutting plan bana sakte ho!"
        ));

        // ----- Thanks -----
        addRule(new Rule(
            new String[]{"thanks", "thank", "thankyou", "thank you", "thx",
                         "shukriya", "sukriya", "dhanyavad", "dhanyawad",
                         "meherbani", "bahut bahut dhanyavad", "thanks bhai",
                         "thanks yaar", "thank u", "thank you so much"},
            (input) -> pickRandom(new String[]{
                "Aapka swagat hai! \uD83D\uDE0A Aur koi sawaal ho toh poochhiye.",
                "Koi baat nahi! Kabhi bhi help chahiye toh yahan hoon. \uD83D\uDE4F",
                "Thank you! Aluminium ka koi aur sawaal ho toh zaroor poochhiye! \uD83D\uDE0A"
            })
        ));

        // ----- Goodbye -----
        addRule(new Rule(
            new String[]{"bye", "goodbye", "good bye", "alvida", "alvida",
                         "tata", "bye bye", "byebye", "chalta hoon", "chaliye",
                         "good night", "see you", "see u", "phir milte",
                         "ab chalta hoon", "band karo", "exit", "close app"},
            (input) -> pickRandom(new String[]{
                "Alvida! \uD83D\uDE4F Jab bhi zaroorat ho, yahan hoon.",
                "Bye bye! Kaam mein shubhkamnayein! \uD83D\uDE0A",
                "Tata! Aluminium ka kaam badhiya chale! \uD83D\uDCAA"
            })
        ));

        // ----- Who are you -----
        addRule(new Rule(
            new String[]{"who are you", "who r u", "tum kaun", "tum kon", "tu kaun",
                         "kaun ho", "kaun ho tum", "aap kaun", "aap kon",
                         "your name", "tumhara naam", "tumhara kya naam",
                         "tera naam", "apna naam", "apna naam batao",
                         "naam kya hai", "tumhara naam kya", "assistant kaun",
                         "robot", "bot", "chatbot", "ai kon", "ai kaun"},
            (input) -> "Main **ALU Assistant** hoon! \uD83E\uDD16\n\n" +
                "Aapka aluminium window calculation helper.\n" +
                "App ke baare mein koi bhi sawaal poochh sakte ho.\n\n" +
                "Main offline kaam karta hoon — internet ki zaroorat nahi! \u2708"
        ));

        // ----- ZED -----
        addRule(new Rule(
            new String[]{"zed", "zed kya", "zed kya hai", "zed kya hota",
                         "zed system", "zed section", "zed sections", "zed single",
                         "zed double", "zed track", "zed pipe", "zed size",
                         "single track", "double track"},
            (input) -> "**ZED** — Single/Double track aluminium window system.\n\n" +
                "Types:\n" +
                "\u2022 **ZED Single Track** — Basic sliding window\n" +
                "\u2022 **ZED Double Track** — Do tracks pe sliding\n\n" +
                "Sections:\n" +
                "\u2022 Frame — Outer structure\n" +
                "\u2022 Sutter — Sliding panel\n" +
                "\u2022 Muliya — Locking interlock\n" +
                "\u2022 RP — Glass bead/retainer\n\n" +
                "ZED ke pipe length usually 252\" (21ft) hoti hai."
        ));

        // ----- DOMAL -----
        addRule(new Rule(
            new String[]{"domal", "domal kya", "domal kya hai", "domal kya hota",
                         "domal system", "domal section", "domal sections",
                         "domal pipe", "domal size", "domal vs zed",
                         "premium system", "premium window"},
            (input) -> "**DOMAL** — Premium double track aluminium system.\n\n" +
                "Features:\n" +
                "\u2022 Stronger frame\n" +
                "\u2022 Better insulation\n" +
                "\u2022 Smooth sliding\n" +
                "\u2022 Premium finish\n\n" +
                "DOMAL sections ZED se different hote hain:\n" +
                "\u2022 Alag dimensions\n" +
                "\u2022 Alag pricing\n" +
                "\u2022 Alag pipe stock\n\n" +
                "Settings mein DOMAL ke specific rates set kar sakte ho!"
        ));

        // ----- Help -----
        addRule(new Rule(
            new String[]{"help", "hlp", "madad", "sahayata", "sahayak",
                         "help karo", "madad karo", "maddad karo", "help chahiye",
                         "madad chahiye", "kya kar sakte ho", "kya kar sakte",
                         "kya kya kar sakte", "what can you do", "help me",
                         "kya poocho", "kya poochoon", "kya puchu", "kya batayega",
                         "kya bataoge", "kya jaante ho", "kya jante ho", "guide",
                         "tutorial", "sikhao", "sikhao kaise", "kaise sikho",
                         "kaise shuru kare", "kaise start kare", "options",
                         "kya options", "menu", "kya karoon", "kya karu"},
            (input) -> "Main aapki in cheezon mein help kar sakta hoon:\n\n" +
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
                "\u2022 \"export kaise kare\" — Excel/JSON export"
        ));

        // ----- Units -----
        addRule(new Rule(
            new String[]{"unit", "units", "inch", "inches", "mm", "millimeter",
                         "millimeters", "millimetre", "centimeter", "cm",
                         "feet", "foot", "ft", "inch vs mm", "unit kya",
                         "unit kaise", "unit kaise change", "kaunse unit",
                         "konse unit", "which unit", "mm mein", "inch mein",
                         "mm ya inch", "inch ya mm", "metric", "unit select"},
            (input) -> "**Units** — App mein do units hain:\n\n" +
                "\u2022 **Inch (in)** — Common measurement\n" +
                "\u2022 **Millimeter (mm)** — Metric measurement\n\n" +
                "Settings mein change kar sakte ho.\n" +
                "Conversion: 1 inch = 25.4 mm\n\n" +
                "Jo unit select karte ho, sab calculations usi mein hongi!"
        ));

        // ----- Waste -----
        addRule(new Rule(
            new String[]{"waste", "west", "waste kya", "waste kya hai",
                         "kitna waste", "waste kitna", "waste percentage",
                         "waste percent", "scrap", "bachat", "bacha hua",
                         "remaining pipe", "cut waste", "optimize", "optimization",
                         "minimum waste", "kam waste", "fayda", "loss"},
            (input) -> "**Waste** — Bachi hui pipe jo use nahi hoti.\n\n" +
                "App **bin-packing algorithm** use karta hai:\n" +
                "\u2022 Minimum waste nikalta hai\n" +
                "\u2022 Cuts optimally arrange karta hai\n" +
                "\u2022 Pipe count minimize karta hai\n\n" +
                "Result mein dikhta hai:\n" +
                "\u2022 Total waste (inches/mm)\n" +
                "\u2022 Waste percentage (%)\n\n" +
                "Lower waste = zyada profit! \uD83D\uDCC8"
        ));

        // ----- Formula -----
        addRule(new Rule(
            new String[]{"formula", "formulae", "custom formula", "apna formula",
                         "formula kaise", "formula kaise banaye", "formula kya",
                         "formula kya hai", "custom rule", "custom rules",
                         "overlap formula", "muliya formula", "clearance",
                         "apna hisaab", "custom calculation", "formula editor"},
            (input) -> "**Custom Formulas** — Apne hisaab se calculations!\n\n" +
                "Menu \u2192 \"Formula\"\n\n" +
                "Aap bana sakte ho:\n" +
                "\u2022 Custom muliya rules\n" +
                "\u2022 Special overlap values\n" +
                "\u2022 Non-standard clearances\n" +
                "\u2022 Apne section dimensions\n\n" +
                "Har system (ZED/DOMAL) ke alag formulas rakh sakte ho.\n\n" +
                "Custom formulas aapke business ke unique requirements ke liye hain!"
        ));

        // ----- RP / Glass bead -----
        addRule(new Rule(
            new String[]{"rp", "r p", "glass bead", "glassbead", "bead",
                         "rp kya", "rp kya hai", "rp kitna", "rp kitne",
                         "rp formula", "rp size", "rp spacing", "rp gap",
                         "grill", "glass fitting", "glass fitting",
                         "glass kaise", "glass size", "retainer", "glass patti"},
            (input) -> "**RP (Glass Bead / Retainer)** — glass ko frame mein pakadne wali patti.\\n\\n" +
                "RP ki quantity window ke height pe depend karti hai. Settings mein:\\n" +
                "\\u2022 **rpGap** — har RP ke beech ka gap\\n" +
                "\\u2022 **rpMin / rpMax** — allowed spacing range\\n\\n" +
                "App automatically valid spacing ke hisaab se RP count suggest karta hai.\\n" +
                "RP Sutter ke andar fit hota hai aur glass ko hold karta hai! \\uD83E\\uDE9F"
        ));

        // ----- Frame / Section / Track (general parts) -----
        addRule(new Rule(
            new String[]{"frame", "section", "sections", "track", "tracks",
                         "frame kya", "section kya", "section kya hai",
                         "outer frame", "frame size", "frame kitna",
                         "aluminium section", "part", "parts", "parts name",
                         "konse part", "which parts", "components", "window parts",
                         "window ke part", "window parts name"},
            (input) -> "Window ke **4 main sections** hote hain:\\n\\n" +
                "\\uD83D\\uDD32 **Frame** — Outer structure, window ka boundary\\n" +
                "\\uD83D\\uDEAA **Sutter (Shutter)** — Sliding panel jo khulta/band hota hai\\n" +
                "\\uD83D\\uDD12 **Muliya (Interlock)** — Sutter ko frame se lock karta hai\\n" +
                "\\uD83E\\uDE9F **RP (Glass Bead)** — Glass ko frame mein pakadta hai\\n\\n" +
                "ZED aur DOMAL systems ke sections alag-alag size ke hote hain.\\n" +
                "Har section ki length Height/Width aur quantity se calculate hoti hai!"
        ));
    }

    // ======================== RULE SYSTEM ========================

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

        /**
         * Score a (already normalized) input against this rule.
         * - Phrases (keywords with spaces) must appear as a contiguous phrase;
         *   each one contributes (wordCount * 2) points.
         * - Single words must match on word boundaries (so "mm" does not match
         *   inside "hmm"); each contributes 1 point.
         * Returns 0 when nothing matches.
         */
        int score(String input) {
            int total = 0;
            for (String kw : keywords) {
                String k = kw.trim().toLowerCase(java.util.Locale.US);
                if (k.isEmpty()) continue;
                if (k.contains(" ")) {
                    if (containsPhrase(input, k)) {
                        total += 2 * k.split(" ").length;
                    }
                } else if (containsWord(input, k)) {
                    total += 1;
                }
            }
            return total;
        }

        /** True if the normalized input contains {@code word} as a whole word. */
        private static boolean containsWord(String input, String word) {
            int from = 0;
            while (true) {
                int i = input.indexOf(word, from);
                if (i < 0) return false;
                boolean leftOk = (i == 0) || input.charAt(i - 1) == ' ';
                int after = i + word.length();
                boolean rightOk = (after == input.length()) || input.charAt(after) == ' ';
                if (leftOk && rightOk) return true;
                from = i + 1;
            }
        }

        /** True if the normalized input contains the multi-word phrase. */
        private static boolean containsPhrase(String input, String phrase) {
            int i = input.indexOf(phrase);
            if (i < 0) return false;
            int after = i + phrase.length();
            boolean leftOk = (i == 0) || input.charAt(i - 1) == ' ';
            boolean rightOk = (after == input.length()) || input.charAt(after) == ' ';
            return leftOk && rightOk;
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
        UserProfile p = loadProfile();
        String name = p.name.isEmpty() ? "" : " " + p.name;
        return new String[]{
            "Namaste" + name + "! \uD83D\uDE4F Bataiye, kya help chahiye?",
            "Hello" + name + "! \uD83D\uDE0A Kaise help kar sakta hoon?",
            "Hi" + name + "! \uD83D\uDC4B Kya sawaal hai aapka?"
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
        UserProfile p = loadProfile();
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
}
