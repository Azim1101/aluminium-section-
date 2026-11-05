package com.digitalalu.alu.ai;

import com.digitalalu.alu.agent.AgentAppController;
import com.digitalalu.alu.calc.Costing;
import com.digitalalu.alu.calc.Engine;
import com.digitalalu.alu.calc.PriceBook;
import com.digitalalu.alu.calc.Settings;
import com.digitalalu.alu.model.Customer;
import com.digitalalu.alu.model.WindowItem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "hands" of the agent: a declarative tool surface over AgentAppController.
 * Nothing here is AI — every tool is deterministic code, so the model cannot
 * hallucinate a pipe count.
 */
public class ToolRegistry {

    public static class Tool {
        public final String name;
        public final String signature;
        public final String help;
        public final boolean destructive;

        Tool(String name, String signature, String help, boolean destructive) {
            this.name = name;
            this.signature = signature;
            this.help = help;
            this.destructive = destructive;
        }

        public String promptLine() { return name + signature + " — " + help; }
    }

    private static final List<Tool> TOOLS = new ArrayList<Tool>();

    static {
        TOOLS.add(new Tool("get_state", "()",
                "app ka pura current state (windows, rates, pipe, total) — sawal ka jawab dene ke liye", false));
        TOOLS.add(new Tool("add_window", "(h,w,sutter,nos,system,name)",
                "nayi window sheet me add karo; h/w inch me, sutter 1-6, nos default 1, system ZED ya DOMAL", false));
        TOOLS.add(new Tool("show_windows", "()",
                "sheet ki saari windows ki list", false));
        TOOLS.add(new Tool("delete_last_window", "()",
                "aakhri window hatao", true));
        TOOLS.add(new Tool("clear_windows", "()",
                "puri sheet khali karo", true));
        TOOLS.add(new Tool("show_estimate", "()",
                "estimate / kitne pipe lagega wo batana", false));
        TOOLS.add(new Tool("show_cutting_plan", "()",
                "cutting plan (kaunsi pipe, kaunsa tukda) batana", false));
        TOOLS.add(new Tool("show_costing", "()",
                "kharcha / total rate batana", false));
        TOOLS.add(new Tool("show_bom", "()",
                "material order (BOM) list — supplier ko bhejne layak pipe/glass/hardware list", false));
        TOOLS.add(new Tool("update_rate", "(kind,rate)",
                "kind = alu (Rs/kg) ya glass (Rs/sqft), naya rate set karo", false));
        TOOLS.add(new Tool("set_stock_pipe", "(section,length)",
                "kisi section ka stock pipe badlo; section = frame/sutter/muliya/rt/rp, length inch me", false));
        TOOLS.add(new Tool("add_extra_pipe", "(section,length,qty)",
                "bacha hua / extra pipe cutting me add karo taaki optimizer use kare", false));
        TOOLS.add(new Tool("reset_stock_pipes", "()",
                "saare stock pipe wapas default par", false));
        TOOLS.add(new Tool("list_customers", "()",
                "customer list", false));
        TOOLS.add(new Tool("save_customer", "(name,mobile,village,note)",
                "current sheet ko customer ke naam se save karo", false));
        TOOLS.add(new Tool("load_customer", "(name)",
                "customer ka naam se uska sheet wapas kholo", false));
        TOOLS.add(new Tool("list_models", "()",
                "kaun kaun se AI model download ho sakte hai + kaunsa is phone me chalega", false));
    }

    public static List<Tool> all() { return TOOLS; }

    public static Tool byName(String name) {
        if (name == null) return null;
        for (Tool t : TOOLS) if (t.name.equalsIgnoreCase(name.trim())) return t;
        return null;
    }

    public static boolean isDestructive(String name) {
        Tool t = byName(name);
        return t != null && t.destructive;
    }

    /** Just the tool names — used to build the on-device JSON grammar. */
    /**
     * Small models return near-miss names ("estimate", "cutting_plan", "list_customer").
     * Map them onto the one registered tool they unambiguously point at; null if none.
     */
    public static String resolve(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9_]", "_");
        if (n.length() == 0) return null;
        if (byName(n) != null) return n;
        String bare = n.replaceAll("^(show_|get_|do_|run_)", "").replaceAll("s$", "");
        String hit = null;
        for (String k : names()) {
            String kb = k.replaceAll("^(show_|get_|do_|run_)", "").replaceAll("s$", "");
            boolean m = kb.equals(bare)
                    || (bare.length() >= 5 && (kb.startsWith(bare + "_") || kb.endsWith("_" + bare)));
            if (m) {
                if (hit != null && !hit.equals(k)) return null;   // ambiguous
                hit = k;
            }
        }
        return hit;
    }

    public static String[] names() {
        String[] out = new String[TOOLS.size()];
        for (int i = 0; i < TOOLS.size(); i++) out[i] = TOOLS.get(i).name;
        return out;
    }

    public static String promptList() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : TOOLS) sb.append("- ").append(t.promptLine()).append('\n');
        return sb.toString();
    }

    /**
     * v1.0 skill packs: same format as promptList() but only the given tools,
     * in registry order. Unknown names are skipped. Never empty — callers fall
     * back to promptList() when a skill filter would produce nothing.
     */
    public static String promptSubset(java.util.Collection<String> names) {
        if (names == null || names.isEmpty()) return promptList();
        java.util.Set<String> want = new java.util.HashSet<String>(names);
        StringBuilder sb = new StringBuilder();
        for (Tool t : TOOLS) if (want.contains(t.name)) sb.append("- ").append(t.promptLine()).append('\n');
        return sb.length() == 0 ? promptList() : sb.toString();
    }

    /* ============================== STATE ============================== */

    public static String stateText(AgentAppController c) {
        StringBuilder sb = new StringBuilder();
        try {
            AgentAppController.AppCalculationState st = c.getLiveState();
            Settings s = st.settings;
            PriceBook pb = st.priceBook;

            sb.append("unit=").append(s != null && s.mm ? "mm" : "inch");
            if (pb != null) sb.append(", aluRate=").append(fmt(pb.aluRate))
                    .append("Rs/kg, glassRate=").append(fmt(pb.glassRate)).append("Rs/sqft");
            sb.append('\n');

            sb.append("stockPipes: ");
            if (st.summaries != null && st.summaries.size() > 0) {
                for (Map.Entry<String, Engine.TypeSummary> e : st.summaries.entrySet()) {
                    Engine.TypeSummary ts = e.getValue();
                    List<Engine.StockBar> bars = ts.bars;
                    sb.append(e.getKey()).append('=');
                    if (bars != null && bars.size() > 0) {
                        for (int i = 0; i < bars.size(); i++) {
                            Engine.StockBar b = bars.get(i);
                            if (i > 0) sb.append('+');
                            sb.append(fmt(b.len));
                            if (b.qty >= 0) sb.append("x").append(b.qty).append("(leftover)");
                        }
                    } else if (s != null) {
                        sb.append(fmt(s.stock));
                    }
                    sb.append("  ");
                }
            } else if (s != null) {
                sb.append("default ").append(fmt(s.stock)).append("\"");
            }
            sb.append('\n');

            List<WindowItem> items = st.items == null ? new ArrayList<WindowItem>() : st.items;
            int shown = 0;
            StringBuilder win = new StringBuilder();
            for (WindowItem w : items) {
                if (w == null || w.isEmpty()) continue;
                win.append('#').append(shown + 1).append(' ')
                   .append(WindowItem.systemName(w.system)).append(' ')
                   .append(fmt(w.h)).append('x').append(fmt(w.w)).append('"')
                   .append(" sutter=").append(w.sutter)
                   .append(" nos=").append(Math.max(1, w.nos));
                if (w.name != null && w.name.length() > 0) win.append(" [").append(w.name).append(']');
                win.append(" | ");
                shown++;
                if (shown >= 12) { win.append("..."); break; }
            }
            sb.append("windows(").append(shown).append("): ")
              .append(shown == 0 ? "(khali)" : win.toString()).append('\n');

            Engine.Grand g = st.grand;
            if (g != null) {
                sb.append("grand: pcs=").append(g.pcs)
                  .append(", totalLen=").append(fmt(g.totalLen))
                  .append(", pipes=").append(g.stockPipes)
                  .append(", waste=").append(fmt(g.waste))
                  .append('"').append(" (").append(fmt(g.wastePct())).append("%)")
                  .append(", glass=").append(fmt(g.glassSqft)).append("sqft\n");
            }
            Costing.Total cost = st.cost;
            if (cost != null) {
                sb.append("cost: pipe=").append(fmt(cost.pipeAmt))
                  .append(", glass=").append(fmt(cost.glassAmt))
                  .append(", extra=").append(fmt(cost.extraAmt))
                  .append(", TOTAL=").append(fmt(cost.total)).append("Rs\n");
            }
        } catch (Throwable t) {
            sb.append("state unavailable: ").append(t.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private static String fmt(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.format(Locale.US, "%.2f", d);
    }

    /* ============================ EXECUTION ============================ */

    public static class ExecResult {
        public String text = "";
        public boolean ok = true;
        public boolean needsConfirm = false;
        public String confirmTool = "";
        public JSONObject confirmArgs = new JSONObject();
    }

    /**
     * Runs a tool. Destructive tools are NOT executed here — they come back as
     * needsConfirm so the UI can ask "haan / nahi" first.
     */
    /** Runs a tool even if destructive — call only after the user confirmed. */
    public static ExecResult executeConfirmed(AgentAppController c, String toolName, JSONObject args) {
        ExecResult r = new ExecResult();
        Tool tool = byName(toolName);
        if (tool == null) { r.ok = false; r.text = "Ye tool mujhe nahi pata: " + toolName; return r; }
        if (args == null) args = new JSONObject();
        try { r.text = run(c, tool.name, args); }
        catch (Throwable t) { r.ok = false; r.text = "Tool chalane me dikkat: " + t.getClass().getSimpleName(); }
        return r;
    }

    public static ExecResult execute(AgentAppController c, String toolName, JSONObject args) {
        ExecResult r = new ExecResult();
        Tool tool = byName(toolName);
        if (tool == null) {
            r.ok = false;
            r.text = "Ye tool mujhe nahi pata: " + toolName;
            return r;
        }
        if (tool.destructive) {
            r.needsConfirm = true;
            r.confirmTool = tool.name;
            r.confirmArgs = args == null ? new JSONObject() : args;
            r.text = "confirm_required";
            return r;
        }
        if (args == null) args = new JSONObject();
        try {
            r.text = run(c, tool.name, args);
        } catch (Throwable t) {
            r.ok = false;
            r.text = "Tool chalane me dikkat: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " — " + t.getMessage());
        }
        return r;
    }

    private static String run(AgentAppController c, String tool, JSONObject a) {
        if ("get_state".equals(tool)) return stateText(c);

        if ("add_window".equals(tool)) {
            double h = a.optDouble("h", 0);
            double w = a.optDouble("w", 0);
            if (h <= 0 || w <= 0) return "Height aur width dono chahiye (jaise 48x60).";
            int sutter = (int) Math.round(a.optDouble("sutter", 2));
            if (sutter < 1) sutter = 1;
            if (sutter > 6) sutter = 6;
            int nos = (int) Math.round(a.optDouble("nos", 1));
            if (nos < 1) nos = 1;
            String sysRaw = a.optString("system", "ZED");
            int sys = (sysRaw != null && sysRaw.toLowerCase(Locale.US).contains("dom"))
                    ? WindowItem.DOMAL : WindowItem.ZED;
            c.addWindow(h, w, sutter, nos, sys, a.optString("name", ""));
            AgentAppController.AppCalculationState st = c.getLiveState();
            StringBuilder sb = new StringBuilder();
            sb.append("Add ho gaya: ").append(WindowItem.systemName(sys)).append(' ')
              .append(fmt(h)).append('x').append(fmt(w)).append("\" sutter ").append(sutter)
              .append(" x").append(nos).append('\n');
            if (st.grand != null) {
                sb.append("Ab total: ").append(st.grand.pcs).append(" pcs aluminium, ")
                  .append(st.grand.stockPipes).append(" pipe, waste ")
                  .append(fmt(st.grand.wastePct())).append('%');
                if (st.cost != null) sb.append(", kharcha Rs ").append(fmt(st.cost.total));
            }
            return sb.toString();
        }
        if ("show_windows".equals(tool)) {
            List<WindowItem> items = c.getLiveState().items;
            if (items == null) items = new ArrayList<WindowItem>();
            int n = 0;
            StringBuilder sb = new StringBuilder();
            for (WindowItem w : items) {
                if (w == null || w.isEmpty()) continue;
                n++;
                sb.append(n).append(". ").append(WindowItem.systemName(w.system)).append(' ')
                  .append(fmt(w.h)).append('x').append(fmt(w.w)).append("\" sutter ")
                  .append(w.sutter).append(" x").append(Math.max(1, w.nos)).append('\n');
            }
            return n == 0 ? "Sheet khali hai." : "Sheet me " + n + " window hai:\n" + sb;
        }
        if ("delete_last_window".equals(tool)) {
            return c.deleteLastWindow() ? "Aakhri window hata di."
                    : "Sheet khali hai, hatane ke liye kuch nahi.";
        }
        if ("clear_windows".equals(tool)) {
            return c.clearAllWindows() + " window hata di. Sheet ab khali hai.";
        }
        if ("show_estimate".equals(tool)) {
            AgentAppController.AppCalculationState st = c.getLiveState();
            StringBuilder sb = new StringBuilder("ESTIMATE\n");
            if (st.summaries != null) {
                for (Map.Entry<String, Engine.TypeSummary> e : st.summaries.entrySet()) {
                    Engine.TypeSummary ts = e.getValue();
                    sb.append("• ").append(e.getKey()).append(": ").append(ts.pcs).append(" pcs, ")
                      .append(fmt(ts.totalLen)).append("\" len, ").append(ts.stockNeeded())
                      .append(" pipe, waste ").append(fmt(ts.waste())).append("\"\n");
                }
            }
            if (st.grand != null) {
                sb.append("TOTAL: ").append(st.grand.pcs).append(" pcs, ")
                  .append(fmt(st.grand.totalLen)).append("\" , ").append(st.grand.stockPipes)
                  .append(" pipe, ").append(fmt(st.grand.glassSqft)).append(" sqft glass");
            }
            if (st.cost != null) sb.append(", Rs ").append(fmt(st.cost.total));
            return sb.toString();
        }
        if ("show_cutting_plan".equals(tool)) {
            AgentAppController.AppCalculationState st = c.getLiveState();
            StringBuilder sb = new StringBuilder("CUTTING PLAN\n");
            if (st.summaries != null) {
                for (Map.Entry<String, Engine.TypeSummary> e : st.summaries.entrySet()) {
                    Engine.TypeSummary ts = e.getValue();
                    sb.append("• ").append(e.getKey()).append(" — ").append(ts.stockNeeded()).append(" pipe\n");
                    if (ts.bins != null) {
                        int i = 0;
                        for (Engine.Bin b : ts.bins) {
                            i++;
                            sb.append("   Pipe ").append(i).append(" (").append(fmt(b.length(0))).append("\"): ");
                            if (b.items != null) {
                                for (int k = 0; k < b.items.size(); k++) {
                                    if (k > 0) sb.append(" + ");
                                    sb.append(fmt(b.items.get(k).len));
                                }
                            }
                            sb.append("  left ").append(fmt(b.free())).append("\"\n");
                        }
                    }
                }
            }
            return sb.toString().trim();
        }
        if ("show_costing".equals(tool)) {
            AgentAppController.AppCalculationState st = c.getLiveState();
            if (st.cost == null) return "Cost nikalne ke liye pehle window add karein.";
            Costing.Total t = st.cost;
            return "KHARCHA\n• Pipe/Aluminium: Rs " + fmt(t.pipeAmt)
                 + "\n• Glass: Rs " + fmt(t.glassAmt)
                 + "\n• Extra: Rs " + fmt(t.extraAmt)
                 + "\n• TOTAL: Rs " + fmt(t.total)
                 + "  (weight " + fmt(t.weight) + " kg)";
        }
        if ("show_bom".equals(tool)) {
            AgentAppController.AppCalculationState st = c.getLiveState();
            if (st.summaries == null || st.summaries.isEmpty())
                return "BOM ke liye pehle window add karein.";
            com.digitalalu.alu.calc.BomBuilder.Bom bom =
                    com.digitalalu.alu.calc.BomBuilder.build(st.results, st.summaries, st.settings);
            bom.bizName = st.settings != null ? st.settings.bizName : "";
            return new com.digitalalu.alu.calc.BomBuilder().toText(bom, true);
        }
        if ("update_rate".equals(tool)) {
            String kind = a.optString("kind", "alu").toLowerCase(Locale.US);
            double rate = a.optDouble("rate", 0);
            if (rate <= 0) return "Rate 0 se bada hona chahiye.";
            if (kind.contains("glass")) {
                c.updateGlassRate(rate);
                return "Glass rate Rs " + fmt(rate) + "/sqft set ho gaya.";
            }
            c.updateAluRate(rate);
            return "Aluminium rate Rs " + fmt(rate) + "/kg set ho gaya.";
        }
        if ("set_stock_pipe".equals(tool)) {
            Settings s = c.getSettings();
            String key = resolveSection(s, a.optString("section", "frame"));
            double len = a.optDouble("length", 0);
            if (key == null) return "Ye section nahi mila. Frame / Sutter / Muliya / RT / RP me se bolein.";
            if (len <= 0) return "Pipe ki length batao (inch me).";
            s.setPrimaryStock(key, len);
            s.save(c.getContext());
            return key + " ka stock pipe " + fmt(len) + "\" ho gaya.";
        }
        if ("add_extra_pipe".equals(tool)) {
            Settings s = c.getSettings();
            String key = resolveSection(s, a.optString("section", "frame"));
            double len = a.optDouble("length", 0);
            int qty = (int) Math.round(a.optDouble("qty", 1));
            if (key == null) return "Ye section nahi mila. Frame / Sutter / Muliya / RT / RP me se bolein.";
            if (len <= 0) return "Pipe ki length batao (inch me).";
            if (qty <= 0) qty = 1;
            s.addExtraBar(key, len, qty);
            s.save(c.getContext());
            return key + " me " + fmt(len) + "\" x" + qty + " leftover pipe add ho gaya — cutting plan isse use karega.";
        }
        if ("reset_stock_pipes".equals(tool)) {
            Settings s = c.getSettings();
            s.resetStockBars();
            s.save(c.getContext());
            return "Saare stock pipe wapas default par aa gaye.";
        }
        if ("list_customers".equals(tool)) {
            List<Customer> list = c.getCustomers();
            if (list == null || list.isEmpty()) return "Abhi koi customer save nahi hai.";
            StringBuilder sb = new StringBuilder("Customers (" + list.size() + "):\n");
            int n = 0;
            for (Customer cu : list) {
                n++;
                if (n > 15) break;
                sb.append(n).append(". ").append(cu.name);
                if (cu.village != null && cu.village.length() > 0) sb.append(" (").append(cu.village).append(')');
                if (cu.mobile != null && cu.mobile.length() > 0) sb.append(" - ").append(cu.mobile);
                sb.append('\n');
            }
            return sb.toString().trim();
        }
        if ("save_customer".equals(tool)) {
            String name = a.optString("name", "");
            if (name.trim().length() == 0) return "Customer ka naam batao.";
            Customer cu = c.saveCurrentSheetToCustomer(name, a.optString("mobile", ""),
                    a.optString("village", ""), a.optString("note", ""));
            return (cu == null) ? "Save nahi ho paya — pehle sheet me window daalein."
                    : name + " ke naam se sheet save ho gayi ("
                      + (cu.windows == null ? 0 : cu.windows.size()) + " window).";
        }
        if ("load_customer".equals(tool)) {
            String want = a.optString("name", "").trim().toLowerCase(Locale.US);
            if (want.length() == 0) return "Kis customer ka sheet kholna hai, naam batao.";
            List<Customer> list = c.getCustomers();
            if (list != null) {
                for (Customer cu : list) {
                    if (cu.name != null && cu.name.toLowerCase(Locale.US).contains(want)) {
                        boolean ok = c.loadCustomerToSheet(cu.id);
                        return ok ? cu.name + " ka sheet khol diya." : "Sheet load nahi ho payi.";
                    }
                }
            }
            return "Is naam ka customer nahi mila.";
        }
        if ("list_models".equals(tool)) {
            StringBuilder sb = new StringBuilder("On-device AI models:\n");
            for (ModelCatalog.ModelSpec m : ModelCatalog.list()) {
                sb.append("• ").append(m.title).append(" — ").append(m.sizeText())
                  .append("  (min RAM ").append(m.minRamBytes / 1073741824L).append("GB)\n");
            }
            sb.append("AI Setup screen me model download + TEST kar sakte hai.");
            return sb.toString();
        }
        return "Tool " + tool + " abhi implement nahi hua.";
    }

    /** "sutter" -> "ZED SUTTER". */
    public static String resolveSection(Settings s, String want) {
        String w = want == null ? "" : want.trim().toUpperCase(Locale.US);
        if (w.length() == 0) w = "FRAME";
        if (w.endsWith("GRILL")) w = w.replace("GRILL", "RP").trim();

        List<String> sections = new ArrayList<String>();
        sections.add("ZED FRAME");
        sections.add("ZED SUTTER");
        sections.add("ZED MULIYA");
        sections.add("ZED RP");
        sections.add("DOMAL FRAME");
        sections.add("DOMAL SUTTER");
        sections.add("DOMAL RT");
        sections.add("DOMAL RP");

        for (String key : sections) if (key.equals(w)) return key;
        for (String key : sections) if (key.endsWith(w)) return key;
        for (String key : sections) if (key.contains(w)) return key;
        return null;
    }
}
