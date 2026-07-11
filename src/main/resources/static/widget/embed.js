(function() {
  'use strict';

  var TENANT = document.currentScript.getAttribute('data-tenant') || '';
  var API_BASE = document.currentScript.getAttribute('data-api') || '/api';
  var PRIMARY = '#2563eb';
  var BOT_NAME = document.currentScript.getAttribute('data-name') || 'SmartRAG AI';

  if (!TENANT) { console.error('[SmartRAG Widget] data-tenant is required'); return; }

  // --- Create DOM ---
  var container = document.createElement('div');
  container.id = '_sr_widget';
  container.innerHTML =
    '<style>' +
    '#_sr_widget{position:fixed;bottom:20px;right:20px;z-index:999999;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}' +
    '#_sr_btn{width:56px;height:56px;border-radius:50%;background:linear-gradient(135deg,' + PRIMARY + ',#1d4ed8);color:#fff;border:none;cursor:pointer;font-size:24px;box-shadow:0 4px 16px rgba(37,99,235,.3);transition:transform .15s;display:flex;align-items:center;justify-content:center;margin-left:auto}' +
    '#_sr_btn:hover{transform:scale(1.05)}' +
    '#_sr_btn svg{width:26px;height:26px}' +
    '#_sr_panel{position:absolute;bottom:68px;right:0;width:360px;height:520px;background:#fff;border-radius:16px;box-shadow:0 8px 32px rgba(0,0,0,.15);display:none;flex-direction:column;overflow:hidden;max-width:calc(100vw - 40px);max-height:calc(100vh - 100px)}' +
    '#_sr_panel.open{display:flex}' +
    '#_sr_header{background:linear-gradient(135deg,' + PRIMARY + ',#1d4ed8);color:#fff;padding:14px 18px}' +
    '#_sr_header h3{font-size:14px;font-weight:600;margin:0}' +
    '#_sr_header p{font-size:11px;opacity:.85;margin:3px 0 0}' +
    '#_sr_msgs{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:8px;background:#fafbfc}' +
    '#_sr_msgs ._m{max-width:82%;padding:9px 13px;border-radius:12px;font-size:14px;line-height:1.5;word-break:break-word;white-space:pre-wrap}' +
    '#_sr_msgs ._m._bot{background:#f1f4f9;align-self:flex-start;border-bottom-left-radius:4px;color:#1e293b}' +
    '#_sr_msgs ._m._user{background:' + PRIMARY + ';color:#fff;align-self:flex-end;border-bottom-right-radius:4px}' +
    '#_sr_msgs ._m._typing{color:#94a3b8;font-style:italic;background:none;padding:9px 0}' +
    '#_sr_input{padding:10px 14px;border-top:1px solid #e2e8f0;display:flex;gap:8px;align-items:center;background:#fff}' +
    '#_sr_input input{flex:1;padding:8px 12px;border:1px solid #e2e8f0;border-radius:8px;font-size:13px;outline:none}' +
    '#_sr_input input:focus{border-color:' + PRIMARY + '}' +
    '#_sr_input button{padding:8px 16px;background:' + PRIMARY + ';color:#fff;border:none;border-radius:8px;font-size:13px;cursor:pointer;white-space:nowrap}' +
    '#_sr_input button:disabled{opacity:.5;cursor:default}' +
    '</style>' +
    '<button id="_sr_btn" onclick="document.getElementById(\'_sr_panel\').classList.toggle(\'open\');this.style.display=\'none\'">' +
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>' +
    '</button>' +
    '<div id="_sr_panel">' +
    '<div id="_sr_header"><h3>' + BOT_NAME + '</h3><p>AI-powered assistant</p></div>' +
    '<div id="_sr_msgs"><div class="_m _bot">Hi! How can I help you? 👋</div></div>' +
    '<div id="_sr_input"><input id="_sr_inp" placeholder="Type your question..." onkeydown="if(event.key===\'Enter\')_sr_send()"><button id="_sr_send" onclick="_sr_send()">Send</button></div>' +
    '</div>';

  document.body.appendChild(container);

  // --- State ---
  var messages = [];
  var convId = null;

  // --- Expose send function globally ---
  window._sr_send = function() {
    var inp = document.getElementById('_sr_inp');
    var text = inp.value.trim();
    if (!text) return;
    inp.value = '';
    appendMsg(text, '_user');
    appendMsg('Thinking...', '_typing');
    document.getElementById('_sr_send').disabled = true;
    askAI(text);
  };

  function appendMsg(text, cls) {
    var el = document.getElementById('_sr_msgs');
    // Remove typing indicator if present
    var typing = el.querySelector('._typing');
    if (typing) typing.remove();
    var div = document.createElement('div');
    div.className = '_m ' + cls;
    div.textContent = text;
    el.appendChild(div);
    el.scrollTop = el.scrollHeight;
  }

  function askAI(question) {
    var url = API_BASE + '/chat/completions';
    var body = JSON.stringify({ message: question });

    fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Tenant-ID': TENANT },
      body: body
    })
    .then(function(r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    })
    .then(function(data) {
      appendMsg(data.answer || 'No answer available.', '_bot');
    })
    .catch(function(err) {
      console.error('[SmartRAG]', err);
      appendMsg('Sorry, I had trouble answering that. Please try again later.', '_bot');
    })
    .finally(function() {
      document.getElementById('_sr_send').disabled = false;
    });
  }
})();
