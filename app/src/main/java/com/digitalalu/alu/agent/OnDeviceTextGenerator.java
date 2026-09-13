package com.digitalalu.alu.agent;

import com.digitalalu.alu.calc.Costing;
import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.ManualCuttingEngine;
import com.digitalalu.alu.calc.PriceBook;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.model.ChatMessage;
import com.digitalalu.alu.model.Customer;
import com.digitalalu.alu.model.UserProfile;
import com.digitalalu.alu.model.WindowItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * On-Device Text Generator Model for ALU Agent.
 * Generates natural, helpful, conversational Hinglish responses with accurate
 * mathematical calculations, engineering formulas, and full app-control actions.
 */
public class OnDeviceTextGenerator {

    private final AgentAppController controller;

    public static class ModelResponse {
        public String text;
        public List<ChatMessage.Action> actions = new ArrayList<>();
        public boolean actionExecuted = false;

        public ModelResponse(String text) {
            this.text = text;
        }

        public ModelResponse(String text, List<ChatMessage.Action> actions, boolean actionExecuted) {
            this.text = text;
            if (actions != null) this.actions.addAll(actions);
            this.actionExecuted = actionExecuted;
        }
    }

    public OnDeviceTextGenerator(AgentAppController controller) {
        this.controller = controller;
    }

    /** Generate response based on user input and live app state */
    public ModelResponse generateResponse(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return generateGreeting();
        }

        AluNeuralNLP.ParsedEntity entity = AluNeuralNLP.parse(userInput);

        switch (entity.intent) {
            case ADD_WINDOW:
                return handleAddWindow(entity);

            case CALCULATE_SINGLE:
                return handleCalculateSingle(entity);

            case VIEW_ESTIMATE:
                return handleViewEstimate();

            case VIEW_CUTTING:
                return handleViewCutting();

            case VIEW_COSTING:
                return handleViewCosting();

            case UPDATE_PRICE:
                return handleUpdatePrice(entity);

            case SHOW_WINDOWS:
                return handleShowWindows();

            case DELETE_LAST:
                return handleDeleteLast();

            case CLEAR_SHEET:
                return handleClearSheet();

            case SAVE_CUSTOMER:
                return handleSaveCustomer(entity);

            case LIST_CUSTOMERS:
                return handleListCustomers();

            case MANUAL_CUTTING:
                return handleManualCutting(entity);

            case SHARE_EXPORT:
                return handleShareExport();

            case EXPLAIN_SYSTEM:
                return handleExplainSystem(userInput);

            case EXPLAIN_FORMULA:
                return handleExplainFormula(userInput);

            case GREETING:
                return generateGreeting();

            case HELP:
                return generateHelp();

            case UNKNOWN:
            default:
                return handleFuzzyOrFallback(entity, userInput);
        }
    }

    /* ================= INTENT HANDLERS ================= */

    private ModelResponse handleAddWindow(AluNeuralNLP.ParsedEntity entity) {
        Settings st = controller.getSettings();
        String sysName = entity.system == WindowItem.DOMAL ? "DOMAL" : "ZED";

        WindowItem item = controller.addWindow(
                entity.height, entity.width, entity.sutter, entity.nos, entity.system, ""
        );

        Engine.WinResult r = Engine.calc(item, st);
        AgentAppController.AppCalculationState state = controller.getLiveState();

        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Window Sheet me ADD ho gayi hai!*\n\n");
        sb.append("🪟 *Details (").append(sysName).append("):*\n");
        sb.append("• Size: *").append(st.fmt(item.h)).append(" × ").append(st.fmt(item.w)).append(" ").append(st.unit()).append("*\n");
        sb.append("• Shutter: *").append(item.sutter).append("-Sutter*  |  Qty: *").append(item.nos).append(" pc*\n\n");

        sb.append("✂ *Cut Sizes:*\n");
        sb.append("• Sutter: *").append(st.fmt(r.sutterH)).append(" × ").append(st.fmt(r.sutterW)).append("* (").append(r.q * r.nos).append(" pcs)\n");
        if (r.muliyaQ > 0) {
            sb.append("• ").append(r.muliyaLabel()).append(": *").append(st.fmt(r.muliyaH)).append("* (").append(r.muliyaQ * r.nos).append(" pcs)\n");
        }
        sb.append("• Glass: *").append(st.fmt(r.glassH)).append(" × ").append(st.fmt(r.glassW)).append("* (").append(String.format(Locale.US, "%.1f sq.ft", r.glassSqft() * r.glassQty())).append(")\n\n");

        sb.append("📊 *Total Project Update:*\n");
        sb.append("• Total Windows: *").append(state.grand.windows).append("*\n");
        sb.append("• Stock Pipes (21ft): *").append(state.grand.stockPipes).append(" pcs*\n");
        sb.append("• Waste: *").append(String.format(Locale.US, "%.1f%%", state.grand.wastePct())).append("*\n");
        if (state.cost != null && state.cost.total > 0) {
            sb.append("• Est. Cost: *₹").append(String.format(Locale.US, "%,.0f", state.cost.total)).append("*\n");
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("📊 Open Estimate", "ESTIMATE"));
        actions.add(new ChatMessage.Action("✂ Cutting Plan", "CUTTING"));
        actions.add(new ChatMessage.Action("📋 Windows Sheet", "WINDOWS"));

        return new ModelResponse(sb.toString(), actions, true);
    }

    private ModelResponse handleCalculateSingle(AluNeuralNLP.ParsedEntity entity) {
        Settings st = controller.getSettings();
        String sysName = entity.system == WindowItem.DOMAL ? "DOMAL" : "ZED";

        Engine.WinResult r = controller.calculateWindow(
                entity.height, entity.width, entity.sutter, entity.nos, entity.system
        );

        StringBuilder sb = new StringBuilder();
        sb.append("📐 *Window Calculation (").append(sysName).append(")*\n\n");
        sb.append("• Frame Size: *").append(st.fmt(entity.height)).append(" × ").append(st.fmt(entity.width)).append(" ").append(st.unit()).append("*\n");
        sb.append("• Track/Sutter: *").append(entity.sutter).append(" Shutter* (Qty: ").append(entity.nos).append(")\n\n");

        sb.append("📏 *Cutting Breakdown:*\n");
        sb.append("• *Frame*: 2 Height (").append(st.fmt(r.H)).append(") + 2 Width (").append(st.fmt(r.W)).append(")\n");
        sb.append("• *Sutter*: *").append(st.fmt(r.sutterH)).append(" × ").append(st.fmt(r.sutterW)).append("* (Total ").append(r.q * r.nos).append(" pcs)\n");
        if (r.muliyaQ > 0) {
            sb.append("• *").append(r.muliyaLabel()).append("*: *").append(st.fmt(r.muliyaH)).append("* (").append(r.muliyaQ * r.nos).append(" pcs)\n");
        }
        sb.append("• *Glass*: *").append(st.fmt(r.glassH)).append(" × ").append(st.fmt(r.glassW)).append("* — *").append(String.format(Locale.US, "%.2f sq.ft", r.glassSqft() * r.glassQty())).append("*\n\n");

        sb.append("💡 Kya aap is window ko *Sheet me add* karna chahte hain?");

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("➕ Add to Sheet", "ADD_WINDOW",
                entity.height + "," + entity.width + "," + entity.sutter + "," + entity.nos + "," + entity.system));
        actions.add(new ChatMessage.Action("📊 View Current Sheet", "WINDOWS"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleViewEstimate() {
        AgentAppController.AppCalculationState s = controller.getLiveState();
        if (s.items.isEmpty()) {
            List<ChatMessage.Action> act = new ArrayList<>();
            act.add(new ChatMessage.Action("➕ Add Window", "ADD_DEFAULT"));
            return new ModelResponse("Abhi sheet me koi window nahi hai.\n\nType karein jaise: *\"48x60 ki 2 window banao\"* ya button dabayein!", act, false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Current Project Estimate Summary:*\n\n");
        sb.append("• Total Windows: *").append(s.grand.windows).append("* (ZED: ").append(s.grand.zedWindows).append(" | DOMAL: ").append(s.grand.domalWindows).append(")\n");
        sb.append("• Total Cut Pieces: *").append(s.grand.pcs).append(" pcs*\n");
        sb.append("• Total Aluminium: *").append(s.settings.fmtU(s.grand.totalLen)).append("*\n");
        sb.append("• Stock Pipes (21ft / 252\"): *").append(s.grand.stockPipes).append(" pipes*\n");
        sb.append("• Efficiency / Usage: *").append(String.format(Locale.US, "%.1f%%", s.grand.usePct())).append("*\n");
        sb.append("• Waste: *").append(String.format(Locale.US, "%.1f%%", s.grand.wastePct())).append("* (").append(s.settings.fmtU(s.grand.waste)).append(")\n\n");

        if (s.cost != null && s.cost.total > 0) {
            sb.append("💰 *Estimated Cost: ₹").append(String.format(Locale.US, "%,.0f", s.cost.total)).append("*\n");
            sb.append("• Pipe Material: ₹").append(String.format(Locale.US, "%,.0f", s.cost.pipeAmt)).append(" (").append(String.format(Locale.US, "%.1f kg", s.cost.weight)).append(")\n");
            sb.append("• Glass Cost: ₹").append(String.format(Locale.US, "%,.0f", s.cost.glassAmt)).append("\n");
            if (s.cost.extraAmt > 0) {
                sb.append("• Extras/Labour: ₹").append(String.format(Locale.US, "%,.0f", s.cost.extraAmt)).append("\n");
            }
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("✂ Cutting Plan", "CUTTING"));
        actions.add(new ChatMessage.Action("💰 Price Book", "PRICE"));
        actions.add(new ChatMessage.Action("📋 Windows List", "WINDOWS"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleViewCutting() {
        AgentAppController.AppCalculationState s = controller.getLiveState();
        if (s.items.isEmpty()) {
            return new ModelResponse("Cutting plan dekhne ke liye pehle windows add karein!\n\nType karein: *\"48x48 ki 3 window add karo\"*", null, false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("✂ *Best Fit Cutting Plan (BFD Optimization):*\n\n");
        sb.append("• Total Pipes Required: *").append(s.grand.stockPipes).append(" pcs*\n");
        sb.append("• Material Usage: *").append(String.format(Locale.US, "%.1f%%", s.grand.usePct())).append("*\n");
        sb.append("• Total Scrap / Waste: *").append(String.format(Locale.US, "%.1f%%", s.grand.wastePct())).append("*\n\n");

        sb.append("📦 *Section-wise Stock Requirement:*\n");
        for (Engine.TypeSummary ts : s.summaries.values()) {
            if (ts.pcs <= 0) continue;
            sb.append("• *").append(Engine.nameOf(ts.type)).append("*: ")
              .append(ts.stockNeeded()).append(" pipes (").append(ts.pcs).append(" cuts, waste: ")
              .append(s.settings.fmtU(ts.waste(s.settings.stock))).append(")\n");
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("✂ Open Cutting Tab", "CUTTING"));
        actions.add(new ChatMessage.Action("📤 Share WhatsApp", "SHARE_WHATSAPP"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleViewCosting() {
        AgentAppController.AppCalculationState s = controller.getLiveState();
        PriceBook pb = s.priceBook;

        StringBuilder sb = new StringBuilder();
        sb.append("💰 *Costing & Rates Breakdown:*\n\n");
        sb.append("🏷 *Current Price Book:*\n");
        sb.append("• Aluminium Rate: *₹").append(String.format(Locale.US, "%.0f", pb.aluRate)).append("/kg*\n");
        sb.append("• Glass Rate: *₹").append(String.format(Locale.US, "%.0f", pb.glassRate)).append("/sq.ft*\n\n");

        if (s.cost != null && s.cost.total > 0) {
            sb.append("📊 *Current Project Total: ₹").append(String.format(Locale.US, "%,.0f", s.cost.total)).append("*\n");
            sb.append("• Total Aluminium Weight: *").append(String.format(Locale.US, "%.2f kg", s.cost.weight)).append("*\n");
            sb.append("• Pipe Cost: ₹").append(String.format(Locale.US, "%,.0f", s.cost.pipeAmt)).append("\n");
            sb.append("• Glass Cost: ₹").append(String.format(Locale.US, "%,.0f", s.cost.glassAmt)).append("\n");
            if (s.cost.extraAmt > 0) {
                sb.append("• Extras/Charges: ₹").append(String.format(Locale.US, "%,.0f", s.cost.extraAmt)).append("\n");
            }
        } else {
            sb.append("Kisi window ka hisab dekhne ke liye window add karein.\n");
        }

        sb.append("\n💡 Rate change karne ke liye likhein: *\"rate 460 kardo\"*");

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("💰 Open Price Setup", "PRICE"));
        actions.add(new ChatMessage.Action("📊 Estimate Sheet", "ESTIMATE"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleUpdatePrice(AluNeuralNLP.ParsedEntity entity) {
        if ("GLASS".equals(entity.rateType)) {
            controller.updateGlassRate(entity.targetRate);
            return new ModelResponse("✅ *Glass Rate update ho gaya:* ₹" + (int)entity.targetRate + "/sq.ft", null, true);
        } else {
            controller.updateAluRate(entity.targetRate);
            AgentAppController.AppCalculationState s = controller.getLiveState();
            StringBuilder sb = new StringBuilder();
            sb.append("✅ *Aluminium Rate update ho gaya:* *₹").append((int)entity.targetRate).append("/kg*\n\n");
            if (s.cost != null && s.cost.total > 0) {
                sb.append("📊 *New Project Total Cost:* *₹").append(String.format(Locale.US, "%,.0f", s.cost.total)).append("*");
            }
            List<ChatMessage.Action> actions = new ArrayList<>();
            actions.add(new ChatMessage.Action("💰 View Price Book", "PRICE"));
            return new ModelResponse(sb.toString(), actions, true);
        }
    }

    private ModelResponse handleShowWindows() {
        List<WindowItem> items = controller.loadWindows();
        if (items.isEmpty()) {
            return new ModelResponse("Abhi sheet khali hai! Windows add karne ke liye likhein jaise:\n*\"48x48 ki 2 window add karo\"*", null, false);
        }

        Settings st = controller.getSettings();
        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Current Windows in Sheet (").append(items.size()).append(" total):*\n\n");
        for (int i = 0; i < items.size(); i++) {
            WindowItem w = items.get(i);
            String sys = w.system == WindowItem.DOMAL ? "DOMAL" : "ZED";
            sb.append("• *#").append(i + 1).append(" (").append(w.name).append(")*: ")
              .append(st.fmt(w.h)).append(" × ").append(st.fmt(w.w)).append(" ").append(st.unit())
              .append("  |  ").append(w.sutter).append("-Sutter  |  ").append(w.nos).append(" pc (").append(sys).append(")\n");
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("📊 Open Sheet", "WINDOWS"));
        actions.add(new ChatMessage.Action("✂ Cutting Plan", "CUTTING"));
        actions.add(new ChatMessage.Action("🗑 Clear All", "CLEAR_ALL"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleDeleteLast() {
        boolean ok = controller.deleteLastWindow();
        if (ok) {
            AgentAppController.AppCalculationState s = controller.getLiveState();
            return new ModelResponse("🗑 *Aakhri window delete kar di gayi hai.*\nAb total " + s.grand.windows + " windows hain.", null, true);
        } else {
            return new ModelResponse("Sheet pehle se hi khali hai.", null, false);
        }
    }

    private ModelResponse handleClearSheet() {
        int count = controller.clearAllWindows();
        return new ModelResponse("🧹 *Sheet bilkul saaf kar di gayi hai!* (" + count + " windows removed).", null, true);
    }

    private ModelResponse handleSaveCustomer(AluNeuralNLP.ParsedEntity entity) {
        String name = entity.customerName.isEmpty() ? "New Client" : entity.customerName;
        Customer c = controller.saveCurrentSheetToCustomer(name, entity.mobileNumber, entity.village, "Saved by AI Agent");

        StringBuilder sb = new StringBuilder();
        sb.append("💾 *Customer Record Save Ho Gaya!*\n\n");
        sb.append("• Naam: *").append(c.name).append("*\n");
        if (!c.mobile.isEmpty()) sb.append("• Mobile: *").append(c.mobile).append("*\n");
        sb.append("• Windows Saved: *").append(c.windows.size()).append("*\n");
        sb.append("• Date: ").append(c.dateStr()).append("\n\n");
        sb.append("Aap customer list me ja kar kabhi bhi yeh data load kar sakte hain!");

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("👤 Open Customers", "CUSTOMERS"));

        return new ModelResponse(sb.toString(), actions, true);
    }

    private ModelResponse handleListCustomers() {
        List<Customer> list = controller.getCustomers();
        if (list.isEmpty()) {
            return new ModelResponse("Abhi koi customer record save nahi hai.\n\nSave karne ke liye likhein: *\"customer Ramesh mobile 9825012345 save karo\"*", null, false);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("👤 *Saved Customers (").append(list.size()).append(" records):*\n\n");
        int max = Math.min(list.size(), 5);
        for (int i = 0; i < max; i++) {
            Customer c = list.get(i);
            sb.append("• *").append(c.name).append("*");
            if (!c.mobile.isEmpty()) sb.append(" (").append(c.mobile).append(")");
            sb.append(" — ").append(c.windows.size()).append(" windows (").append(c.dateStr()).append(")\n");
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("👤 Open Customer Records", "CUSTOMERS"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleManualCutting(AluNeuralNLP.ParsedEntity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("✂ *Manual PCO & Sheet Cutting Tool*\n\n");
        sb.append("Aap custom pipe cuts aur sheet material ko optimize kar sakte hain.\n");
        sb.append("App Best-Fit-Decreasing (BFD) aur MaxRects algorithm use karta hai taaki waste minimum ho.\n\n");
        sb.append("Direct screen open karne ke liye niche button dabayein!");

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("✂ Open Manual Cutting", "MANUAL_PCO"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleShareExport() {
        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("📤 WhatsApp Share", "SHARE_WHATSAPP"));
        actions.add(new ChatMessage.Action("📊 Export Excel", "EXPORT_EXCEL"));

        return new ModelResponse("Aap current calculation WhatsApp pe text bhejna chahte hain ya Excel file (.xlsx) export karna chahte hain?", actions, false);
    }

    private ModelResponse handleExplainSystem(String text) {
        if (text.contains("domal")) {
            return new ModelResponse("🏢 *DOMAL System (Premium Double Track):*\n\n"
                    + "• Badi aur heavy windows ke liye best system hai\n"
                    + "• Frame zyada mazboot aur wide hota hai\n"
                    + "• Shutter sections mein smooth ball-bearing rollers aate hain\n"
                    + "• Better sound proofing aur dust protection deta hai\n"
                    + "• Iska glass aur pipe weight ZED se zyada hota hai\n\n"
                    + "Settings me aap DOMAL ke specific rates aur overlaps set kar sakte hain!", null, false);
        } else {
            return new ModelResponse("🪟 *ZED System (Standard Single/Double Track):*\n\n"
                    + "• Sabse popular aur cost-effective system\n"
                    + "• Residential aur normal commercial windows me sabse zyada use hota hai\n"
                    + "• Standard pipe stock: 252\" (21 Feet)\n"
                    + "• Sections: Frame, Sutter, Muliya (Interlock), aur RP Grill\n"
                    + "• Fast fabrication aur low wastage!", null, false);
        }
    }

    private ModelResponse handleExplainFormula(String text) {
        return new ModelResponse("📐 *Aluminium Cutting Formulas:*\n\n"
                + "• *Sutter Height* = Frame Height - Track Clearance (approx 2\")\n"
                + "• *Sutter Width* = (Frame Width + Overlaps) ÷ Sutter Count\n"
                + "• *Muliya (Interlock)* = Same as Sutter Height\n"
                + "• *Glass Size* = Sutter Size - 4\" (beading allowance)\n"
                + "• *RP Grill Length* = Sutter Width\n\n"
                + "Settings ya Formula option se aap in clearances ko apni factory ke according customize bhi kar sakte hain! 🛠️", null, false);
    }

    private ModelResponse generateGreeting() {
        UserProfile profile = UserProfile.load(controller.getContext());
        String name = profile.name.isEmpty() ? "" : " " + profile.name;
        AgentAppController.AppCalculationState s = controller.getLiveState();

        StringBuilder sb = new StringBuilder();
        sb.append("Namaste").append(name).append("! 🙏\n\n");
        sb.append("Main aapka *ALU AI Assistant* hoon. Main on-device AI model se chalta hoon aur aapki puri app ko control kar sakta hoon!\n\n");
        sb.append("Aap mujhse aam bolchal (Hinglish/Hindi) me baat karke kaam karwa sakte hain, jaise:\n");
        sb.append("• *\"48x60 ki 2 window add karo\"*\n");
        sb.append("• *\"Sutter aur muliya size batao\"*\n");
        sb.append("• *\"Kitne pipe lagenge cutting me?\"*\n");
        sb.append("• *\"Kharcha kitna hua total?\"*\n");
        sb.append("• *\"Rate 450 per kg kardo\"*\n\n");

        if (s.grand.windows > 0) {
            sb.append("📊 *Current Sheet Status:* ").append(s.grand.windows).append(" windows, ").append(s.grand.stockPipes).append(" pipes needed.\n");
        }

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("➕ 48×48 Window Add", "ADD_WINDOW", "48,48,2,1,0"));
        actions.add(new ChatMessage.Action("📊 Estimate Summary", "ESTIMATE"));
        actions.add(new ChatMessage.Action("✂ Cutting Plan", "CUTTING"));
        actions.add(new ChatMessage.Action("💰 Total Cost", "PRICE"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse generateHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("🛠️ *ALU AI Agent Command Guide:*\n\n");
        sb.append("1. *Window Add Karna:*\n   • \"48x60 ki 2 window banao\"\n   • \"36x48 domal 3 piece add karo\"\n\n");
        sb.append("2. *Hisab & Formula Check:*\n   • \"50x50 ka sutter aur glass batao\"\n   • \"Muliya formula kya hai?\"\n\n");
        sb.append("3. *Cutting Plan & Pipes:*\n   • \"Cutting plan dikhao\"\n   • \"Kitna waste bachega?\"\n\n");
        sb.append("4. *Price & Costing:*\n   • \"Total cost kitna hua?\"\n   • \"Rate 450 rs kardo\"\n\n");
        sb.append("5. *Customer & Sheets:*\n   • \"Customer Ramesh save karo\"\n   • \"Sab windows dikhao\"\n   • \"Sab delete kardo\"");

        List<ChatMessage.Action> actions = new ArrayList<>();
        actions.add(new ChatMessage.Action("📊 View Estimate", "ESTIMATE"));
        actions.add(new ChatMessage.Action("✂ View Cutting", "CUTTING"));

        return new ModelResponse(sb.toString(), actions, false);
    }

    private ModelResponse handleFuzzyOrFallback(AluNeuralNLP.ParsedEntity entity, String userInput) {
        // If user gave numbers or broken text without clear intent
        if (entity.hasDimensions) {
            return handleCalculateSingle(entity);
        }

        // Conversational fallback
        return new ModelResponse("Main samajh nahi paya. 🤔\n\nAap window add karne ke liye size likhiye, jaise:\n• *\"48x60 ki 2 window add karo\"*\n• *\"Total cost kitna hua?\"*\n• *\"Cutting plan dikhao\"*\n\nYa *\"help\"* likhein!", null, false);
    }
}
