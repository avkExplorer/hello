/* Cookie consent. Not legal advice; part of the AVK legal pack (draft).
   Modes (data-mode on the script tag):
     "notice"  - the app sets only essential cookies: show a one-time notice with an OK button.
     "consent" - the app has optional tools: nothing optional runs before Accept; Accept and Reject look the same.
   The choice is kept in localStorage ("avk_consent"), never in a cookie. Optional scripts are written as
     <script type="text/plain" data-consent="analytics" src="..."></script>
   and are only switched on after Accept. The footer link "Cookie settings" reopens the choice. */
(function () {
  'use strict';
  var KEY = 'avk_consent';
  var script = document.currentScript;
  var mode = (script && script.getAttribute('data-mode')) || 'notice';

  function read() { try { return window.localStorage.getItem(KEY); } catch (e) { return null; } }
  function write(v) { try { window.localStorage.setItem(KEY, v); } catch (e) { /* storage blocked: choice lasts for this page only */ } }

  function activate() {
    var pending = document.querySelectorAll('script[type="text/plain"][data-consent]');
    for (var i = 0; i < pending.length; i++) {
      var old = pending[i], s = document.createElement('script');
      for (var j = 0; j < old.attributes.length; j++) {
        var a = old.attributes[j];
        if (a.name !== 'type') { s.setAttribute(a.name, a.value); }
      }
      s.text = old.text;
      old.parentNode.replaceChild(s, old);
    }
  }

  function close(box) { if (box && box.parentNode) { box.parentNode.removeChild(box); } }

  function show() {
    close(document.getElementById('avk-consent'));
    var box = document.createElement('div');
    box.id = 'avk-consent';
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-label', 'Cookie choices');
    var text = document.createElement('p');
    var link = '<a href="/legal/cookies.html">cookie notice</a>';
    text.innerHTML = mode === 'consent'
      ? 'We would like to use optional cookies and scripts. They stay off unless you accept. See the ' + link + '.'
      : 'This site uses only the cookies it needs to work. See the ' + link + '.';
    box.appendChild(text);
    var row = document.createElement('div');
    function button(label, value) {
      var b = document.createElement('button');
      b.type = 'button';
      b.textContent = label;
      b.addEventListener('click', function () {
        write(value);
        if (value === 'accepted') { activate(); }
        close(box);
      });
      row.appendChild(b);
    }
    if (mode === 'consent') { button('Accept', 'accepted'); button('Reject', 'rejected'); } else { button('OK', 'noticed'); }
    box.appendChild(row);
    document.body.appendChild(box);
    row.firstChild.focus();
  }

  var openers = document.querySelectorAll('[data-consent-open]');
  for (var i = 0; i < openers.length; i++) {
    openers[i].addEventListener('click', function (ev) { ev.preventDefault(); show(); });
  }

  var saved = read();
  if (saved === 'accepted' && mode === 'consent') { activate(); }
  if (!saved) { show(); }
})();
