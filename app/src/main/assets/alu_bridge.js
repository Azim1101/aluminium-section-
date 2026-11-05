/* =====================================================================
   ALU WINDOW — PERCHANCE BRIDGE v4  (chatbot + app control)
   ---------------------------------------------------------------------
   Ye script generator page ke ANDAR chalti hai (app khud inject karti hai,
   ya aap ise Perchance HTML me paste kar sakte ho — dono me same kaam).

   Do kaam:
     1) BRIDGE  : app ke native chat se "ask" aaye to ai-text-plugin se jawab.
     2) CHAT UI : page par hi poora chatbot — user yahin likhta hai, AI yahin
                  jawab deta hai, aur AI ko APP CONTROL ke tools milte hain:
                    ALU.tools()  ALU.state()  ALU.run(tool,args)  ALU.confirm()
                    ALU.openScreen("windows|estimate|cutting|price|customers")
   Agar page khud UI banata hai to window.ALU_CHAT = 1 set kar do,
   overlay nahi banega par ALU.* aur aluAgent.* sab available rahenge.
   ===================================================================== */
(function () {
  try {
    if (window.__aluBridgeV2) return;
    window.__aluBridgeV2 = true;
    window.__aluBridgeVersion = 4;

    var Q = [], BUSY = false, SEEN = [], MODEL = null;
    var hasApp = function () { return !!(window.ALU && typeof window.ALU.run === 'function'); };

    /* ---------------- app <-> page events (native chat path) ---------------- */
    function send(kind, id, text, extra) {
      var m = { __alu: 1, kind: kind, id: id, text: text || '' };
      if (extra) for (var k in extra) m[k] = extra[k];
      try { if (window.Android && window.Android.onPerchanceEvent) window.Android.onPerchanceEvent(JSON.stringify(m)); } catch (e) {}
      try { if (window.parent && window.parent !== window) window.parent.postMessage(m, '*'); } catch (e) {}
    }

    /* ---------------- prompts ---------------- */
    function sys() {
      if (typeof window.aluSystemPrompt === 'function') { try { return String(window.aluSystemPrompt()); } catch (e) {} }
      if (hasApp()) { try { var s = window.ALU.systemPrompt(); if (s) return String(s); } catch (e) {} }
      return 'Tum ALU Window ke agent ho. Kaam: user ki Hinglish baat samajhna aur SAHI ek tool chunna.\n' +
        'Sirf JSON do. Koi markdown nahi, koi extra line nahi.\nJawab ka format:\n' +
        '  {"say":"chhota Hinglish jawab", "tool":"tool_name", "args":{...}}\n' +
        'Ya agar koi tool nahi chahiye to sirf: {"say":"jawab"}\n' +
        'Numbers sirf STATE me diye gaye sach se lo, apne se mat banao.';
    }
    function orderInstr(text, state, tools) {
      return sys() + '\n\nAVAILABLE TOOLS:\n' + (tools || '') + '\n\nCURRENT STATE (asli data):\n' + (state || '') +
        '\n\nUSER NE KAHA: ' + (text || '') + '\n\nAb sirf ek JSON object do.';
    }
    function sayInstr(text, tool, result) {
      return 'Tum ALU Window ke agent ho. Tool chal chuka hai.\nUSER NE POOCHA: ' + (text || '') + '\nTOOL: ' + (tool || '') +
        '\nTOOL KA RESULT (yahi sach hai, isse bahar kuch mat bolo):\n' + (result || '') +
        '\nAb user ko 1-2 line Hinglish me jawab do. Number sirf upar wale result se lo.\nSirf JSON do: {"say":"..."}';
    }
    function instr(d) {
      if (d.mode === 'order') return orderInstr(d.text, d.state, d.tools);
      if (d.mode === 'say') return sayInstr(d.text, d.tool, d.result);
      return d.text || '';
    }

    /* ---------------- find the ai-text-plugin ---------------- */
    function find() {
      var names = ['ai', 'generateText', 'aiText', 'textAi', 'aiTextPlugin', 'textGen', 'llm'];
      for (var i = 0; i < names.length; i++) {
        try { var f = window[names[i]]; if (typeof f === 'function') return { fn: f, name: names[i] }; } catch (e) {}
      }
      try {
        var r = window.root;
        if (r) for (var k in r) {
          try {
            var g = r[k];
            if (typeof g === 'function' && /ai|text|llm/i.test(k) && !/image|img|picture|photo|speech|audio/i.test(k) && typeof g.then !== 'function')
              return { fn: g, name: 'root.' + k };
          } catch (e) {}
        }
      } catch (e) {}
      return null;
    }
    function run(opts) {
      var m = MODEL || find();
      if (!m) return Promise.reject('ai-text-plugin nahi mila — Lists me "ai = {import:ai-text-plugin}" chahiye');
      MODEL = m;
      var out;
      try { out = m.fn(opts); } catch (e) { return Promise.reject(String(e)); }
      return Promise.resolve(out).then(function (res) {
        if (res == null) return '';
        if (typeof res === 'string') return res;
        if (res.text != null) return String(res.text);
        if (res.generatedText != null) return String(res.generatedText);
        return String(res);
      });
    }
    window.aluRun = run;

    /* ---------------- tolerant JSON parse ---------------- */
    function parseCall(raw) {
      var t = String(raw || '').trim();
      t = t.replace(/^```(json)?/i, '').replace(/```$/, '').trim();
      var i = t.indexOf('{'), j = t.lastIndexOf('}');
      if (i >= 0 && j > i) {
        var s = t.substring(i, j + 1);
        try { var o = JSON.parse(s); return { say: o.say || o.reply || o.text || '', tool: o.tool || null, args: o.args || o.arguments || {} }; } catch (e) {}
        // last resort: cut at first balanced object
        var depth = 0; for (var k = i; k < t.length; k++) { if (t[k] === '{') depth++; else if (t[k] === '}') { depth--; if (depth === 0) { try { var o2 = JSON.parse(t.substring(i, k + 1)); return { say: o2.say || '', tool: o2.tool || null, args: o2.args || {} }; } catch (e) {} break; } } }
      }
      return { say: t, tool: null, args: {} };
    }

    /* ---------------- queue for app->page asks ---------------- */
    function pump() {
      if (BUSY || Q.length === 0) return;
      var d = Q.shift(); BUSY = true; var id = d.id;
      var opts = { instruction: instr(d), endButtons: 'none', onChunk: function (c) { try { send('chunk', id, (c && c.fullTextSoFar) || ''); } catch (e) {} } };
      if (d.startWith) opts.startWith = d.startWith;
      if (d.stopSequences && d.stopSequences.length) opts.stopSequences = d.stopSequences;
      send('start', id, '');
      var guard = setTimeout(function () { if (BUSY) { BUSY = false; send('error', id, 'plugin ne 150s me jawab nahi diya'); pump(); } }, 150000);
      run(opts).then(function (full) { clearTimeout(guard); if (!BUSY) return; BUSY = false; send('done', id, full); pump(); },
        function (err) { clearTimeout(guard); if (!BUSY) return; BUSY = false; send('error', id, String(err)); pump(); });
    }
    window.addEventListener('message', function (e) {
      var d = e.data;
      if (!d || d.__alu !== 1 || d.kind !== 'ask') return;
      if (SEEN.indexOf(d.id) >= 0) return;
      SEEN.push(d.id); if (SEEN.length > 60) SEEN.shift();
      if (Q.length > 4) { send('error', d.id, 'queue full — thoda ruko'); return; }
      Q.push(d); pump();
    });
    window.__aluAsk = function (d) { window.postMessage(d, '*'); };

    /* =====================================================================
       AGENT LOOP (page-side) — user -> model -> ALU tool -> model -> user
       ===================================================================== */
    var agent = {
      pendingConfirm: null,
      busy: false,
      appJson: function (s) { try { return typeof s === 'string' ? JSON.parse(s) : s; } catch (e) { return { ok: false, text: String(s) }; } },
      isYes: function (t) { return /^(haan|ha|han|yes|y|ok|okay|theek|thik|kar do|kardo|confirm|हाँ|हां)\b/i.test(t.trim()); },
      isNo: function (t) { return /^(nahi|nahin|no|n|cancel|mat|rehne do|नहीं)\b/i.test(t.trim()); },

      /* main entry: returns Promise<{say, resultText, tool, actions}> */
      turn: function (text, onChunk) {
        var self = this;
        text = String(text || '').trim();
        if (!text) return Promise.resolve({ say: '' });
        if (!hasApp()) return Promise.resolve({ say: '⚠️ App bridge (ALU) nahi mila — ye page ALU Window app ke andar kholo.' });

        if (self.pendingConfirm) {
          var pc = self.pendingConfirm; self.pendingConfirm = null;
          if (self.isYes(text)) { var cr = self.appJson(window.ALU.confirm(pc.tool, JSON.stringify(pc.args || {}))); return Promise.resolve({ say: cr.text || 'Ho gaya.', tool: pc.tool, done: true }); }
          if (self.isNo(text)) return Promise.resolve({ say: 'Theek hai, cancel kar diya.' });
        }

        var state = '', tools = '';
        try { state = window.ALU.state(); tools = window.ALU.toolsPrompt(); } catch (e) {}
        self.busy = true;
        return run({ instruction: orderInstr(text, state, tools), startWith: '{', endButtons: 'none', onChunk: function (c) { if (onChunk) onChunk((c && c.fullTextSoFar) || ''); } })
          .then(function (raw) {
            var call = parseCall('{' + String(raw).replace(/^\{/, ''));
            if (!call.tool) return { say: call.say || raw };
            var er = self.appJson(window.ALU.run(call.tool, JSON.stringify(call.args || {})));
            if (er.needsConfirm) {
              self.pendingConfirm = { tool: er.confirmTool || call.tool, args: er.confirmArgs || call.args };
              return { say: (call.say ? call.say + '\n\n' : '') + '⚠️ ' + (er.describe || ('"' + call.tool + '" chalana hai')) + '\nConfirm karein?', confirm: true, tool: call.tool };
            }
            var resultText = er.text || '';
            // follow-up: let the model phrase the real numbers
            return run({ instruction: sayInstr(text, call.tool, resultText), startWith: '{', endButtons: 'none' })
              .then(function (raw2) { var c2 = parseCall('{' + String(raw2).replace(/^\{/, '')); return { say: c2.say || call.say || '', resultText: resultText, tool: call.tool }; },
                    function () { return { say: call.say || '', resultText: resultText, tool: call.tool }; });
          })
          .then(function (r) { self.busy = false; return r; }, function (err) { self.busy = false; return { say: '⚠️ ' + String(err) + '\n\nUpar 🌐 dabao — agar page par "Verifying" ya ad dikhe to ek baar pass karo, phir dobara bhejo.', error: true }; });
      }
    };
    window.aluAgent = agent;

    /* =====================================================================
       CHAT UI OVERLAY (only when the app asks for it and page has none)
       ===================================================================== */
    function wantUi() {
      if (window.ALU_CHAT) return false;
      if (window.__ALU_CHAT_UI) return true;
      try { return hasApp() && window.ALU.wantChatUi && window.ALU.wantChatUi(); } catch (e) { return false; }
    }
    function esc(s) { return String(s).replace(/[&<>]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]; }); }
    function md(s) { return esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/\n/g, '<br>'); }

    function buildUi() {
      if (document.getElementById('aluChat')) return;
      var dark = false; try { dark = hasApp() && window.ALU.isDark && window.ALU.isDark(); } catch (e) {}
      var C = dark ? { bg: '#0E0F12', card: '#1B1D22', card2: '#23262C', line: '#2C2F36', txt: '#F2F3F5', mut: '#9BA1AB', acc: '#4C9BFF', accl: '#16233A' }
                   : { bg: '#F4F6FB', card: '#FFFFFF', card2: '#F7F8FA', line: '#E5EAF3', txt: '#152236', mut: '#66758C', acc: '#2563EB', accl: '#E8F0FF' };
      var css = '#aluChat{position:fixed;inset:0;z-index:2147483000;background:' + C.bg + ';color:' + C.txt + ';font-family:system-ui,Segoe UI,Roboto,sans-serif;display:flex;flex-direction:column;font-size:15px}' +
        '#aluChat *{box-sizing:border-box}' +
        '#aluHead{padding:10px 14px 6px;display:flex;align-items:center;gap:10px}' +
        '#aluHead .av{width:38px;height:38px;border-radius:50%;background:' + C.acc + ';display:flex;align-items:center;justify-content:center;color:#fff;font-size:18px}' +
        '#aluHead .t{flex:1;min-width:0}#aluHead .t b{display:block;font-size:17px}#aluHead .t span{font-size:11.5px;color:' + C.mut + ';display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
        '#aluHead button{width:38px;height:38px;border-radius:50%;border:1px solid ' + C.line + ';background:' + C.card + ';color:' + C.txt + ';font-size:16px}' +
        '#aluMsgs{flex:1;overflow-y:auto;padding:6px 10px 10px;-webkit-overflow-scrolling:touch}' +
        '.aluB{max-width:86%;padding:10px 13px;margin:5px 0;border-radius:20px;line-height:1.38;word-wrap:break-word;white-space:pre-wrap}' +
        '.aluU{margin-left:auto;background:' + C.acc + ';color:#fff;border-bottom-right-radius:6px}' +
        '.aluA{margin-right:auto;background:' + C.card + ';border:1px solid ' + C.line + ';border-top-left-radius:6px}' +
        '.aluA .res{display:block;margin-top:8px;padding:8px 10px;border-radius:12px;background:' + C.card2 + ';font-size:12.5px;color:' + C.mut + ';white-space:pre-wrap}' +
        '.aluA .acts{display:flex;gap:6px;flex-wrap:wrap;margin-top:8px}' +
        '.aluA .acts button,#aluChips button{border:1px solid ' + C.line + ';background:' + C.card + ';color:' + C.acc + ';font-weight:700;border-radius:20px;padding:7px 12px;font-size:13px}' +
        '#aluChips{display:flex;gap:8px;overflow-x:auto;padding:6px 10px;scrollbar-width:none}#aluChips button{white-space:nowrap;flex:none}' +
        '#aluIn{display:flex;gap:8px;padding:6px 10px 12px;align-items:flex-end}' +
        '#aluIn textarea{flex:1;border:1px solid ' + C.line + ';background:' + C.card + ';color:' + C.txt + ';border-radius:24px;padding:11px 14px;font-size:15px;resize:none;max-height:110px;outline:none}' +
        '#aluIn button{width:48px;height:48px;border-radius:50%;border:none;background:' + C.acc + ';color:#fff;font-size:20px;flex:none}' +
        '.aluTyping{opacity:.75;font-style:italic}';
      var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
      var root = document.createElement('div'); root.id = 'aluChat';
      root.innerHTML =
        '<div id="aluHead"><div class="av">🤖</div><div class="t"><b>ALU AI Agent</b><span id="aluSt">Perchance Web Chat • plugin ka intezaar…</span></div>' +
        '<button id="aluPage" title="Perchance page dekho">🌐</button><button id="aluNav" title="App screens">▦</button></div>' +
        '<div id="aluMsgs"></div>' +
        '<div id="aluChips"></div>' +
        '<div id="aluIn"><textarea id="aluTxt" rows="1" placeholder="Message… (jaise: 48x60 ki 2 window add karo)"></textarea><button id="aluSend">➤</button></div>';
      document.body.appendChild(root);

      var msgs = document.getElementById('aluMsgs'), txt = document.getElementById('aluTxt'), stEl = document.getElementById('aluSt');
      function scroll() { msgs.scrollTop = msgs.scrollHeight; }
      function bubble(cls, html) { var d = document.createElement('div'); d.className = 'aluB ' + cls; d.innerHTML = html; msgs.appendChild(d); scroll(); return d; }
      function actions(d, list) {
        var box = document.createElement('div'); box.className = 'acts';
        list.forEach(function (a) { var b = document.createElement('button'); b.textContent = a.label; b.onclick = function () { box.remove(); a.run(); }; box.appendChild(b); });
        d.appendChild(box); scroll();
      }
      window.aluChatSay = function (t) { bubble('aluA', md(t)); };

      var chips = [
        ['➕ 48×60 Window', '48x60 ki 2 window add karo'], ['📊 Estimate', 'Kitne pipe lagenge? estimate batao'],
        ['✂ Cutting Plan', 'Cutting plan dikhao'], ['💰 Kharcha', 'Total kharcha kitna hua?'],
        ['📋 Windows', 'Windows list dikhao'], ['👤 Customers', 'Customer list dikhao'], ['🧹 Clear', 'Sab windows clear kardo']
      ];
      var chipBox = document.getElementById('aluChips');
      chips.forEach(function (c) { var b = document.createElement('button'); b.textContent = c[0]; b.onclick = function () { go(c[1]); }; chipBox.appendChild(b); });

      // Show the real Perchance page (ads / "verify" / login) and come back.
      var back = document.createElement('button'); back.id = 'aluBack'; back.textContent = '💬 Chat par wapas';
      back.style.cssText = 'position:fixed;left:50%;bottom:18px;transform:translateX(-50%);z-index:2147483001;display:none;border:none;border-radius:24px;padding:12px 18px;background:' + C.acc + ';color:#fff;font-weight:700;font-size:14px;box-shadow:0 8px 24px rgba(0,0,0,.25)';
      document.body.appendChild(back);
      document.getElementById('aluPage').onclick = function () { root.style.display = 'none'; back.style.display = 'block'; };
      back.onclick = function () { root.style.display = 'flex'; back.style.display = 'none'; };
      window.aluShowPage = function () { root.style.display = 'none'; back.style.display = 'block'; };

      document.getElementById('aluNav').onclick = function () {
        var d = bubble('aluA', 'Kaunsi screen kholu?');
        actions(d, ['windows', 'estimate', 'cutting', 'price', 'customers'].map(function (s) { return { label: s, run: function () { try { window.ALU.openScreen(s); } catch (e) {} } }; }));
      };

      function go(text) {
        text = String(text || txt.value || '').trim(); if (!text || agent.busy) return;
        txt.value = ''; txt.style.height = 'auto';
        bubble('aluU', esc(text));
        var typing = bubble('aluA aluTyping', 'soch raha hoon…');
        stEl.textContent = 'jawab ban raha hai…';
        agent.turn(text, function (partial) { if (partial) typing.innerHTML = esc(partial.slice(0, 300)); }).then(function (r) {
          typing.remove();
          stEl.textContent = 'READY • ' + (MODEL ? MODEL.name : 'plugin');
          var html = md(r.say || (r.resultText ? '' : 'Samajh nahi aaya, dobara bolo.'));
          if (r.resultText) html += '<span class="res">' + esc(r.resultText) + '</span>';
          var d = bubble('aluA', html);
          if (r.confirm) actions(d, [{ label: '✅ Haan', run: function () { go('haan'); } }, { label: '❌ Nahi', run: function () { go('nahi'); } }]);
          if (r.tool && /add_window|clear|delete|update_rate|set_stock|add_extra|reset_stock|load_customer/.test(r.tool)) {
            try { window.ALU.notifyChanged(); } catch (e) {}
          }
        });
      }
      document.getElementById('aluSend').onclick = function () { go(); };
      txt.addEventListener('keydown', function (e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); go(); } });
      txt.addEventListener('input', function () { txt.style.height = 'auto'; txt.style.height = Math.min(110, txt.scrollHeight) + 'px'; });

      var greet = 'Namaste! 🙏 Main ALU Window ka agent hoon — yahin Perchance page par chal raha hoon aur app ko control kar sakta hoon.\n\nBolo: "48x60 ki 2 window add karo", "kitne pipe lagenge?", "rate 450 per kg kardo"…';
      bubble('aluA', md(greet));
      window.__aluSetStatus = function (s) { stEl.textContent = s; };
    }

    /* ---------------- boot: wait for plugin, announce ready ---------------- */
    function boot() {
      if (wantUi()) { if (document.body) buildUi(); else document.addEventListener('DOMContentLoaded', buildUi); }
      var tries = 0;
      (function wait() {
        var m = find();
        if (m) {
          MODEL = m;
          send('ready', 0, 'bridge v4 • ' + m.name, { v: 4, plugin: m.name });
          if (window.__aluSetStatus) window.__aluSetStatus('READY • ' + m.name + ' • app control ON');
        } else if (++tries < 120) {
          if (tries === 1 || tries % 10 === 0) send('info', 0, 'page khuli — ai-text-plugin ka intezaar (' + tries + ')');
          setTimeout(wait, 500);
        } else {
          send('info', 0, 'ai-text-plugin nahi mila — generator ke Lists me ai = {import:ai-text-plugin} daalo');
          if (window.__aluSetStatus) window.__aluSetStatus('❌ ai-text-plugin nahi mila');
        }
      })();
      setInterval(function () { send('ping', 0, String(Date.now())); }, 5000);
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
  } catch (e) {
    try { Android.onPerchanceEvent(JSON.stringify({ __alu: 1, kind: 'info', text: 'bridge error: ' + e })); } catch (x) {}
  }
})();
