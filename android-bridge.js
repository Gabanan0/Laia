(() => {
  function two(n){ return String(n).padStart(2,"0"); }

  window.tryAndroidCommand = function(raw){
    if (!window.AndroidBridge) return null;
    const t = String(raw || "").trim().toLowerCase().replace(/,/g, ".");

    const alarm =
      t.match(/(?:wecker|alarm).*?(?:auf|um)?\s*(\d{1,2})(?:(?:[:.]|\s*uhr\s*)(\d{1,2}))?/i) ||
      t.match(/(?:stell|set).*?(\d{1,2})(?:(?:[:.]|\s*uhr\s*)(\d{1,2}))?.*?(?:wecker|alarm)/i);

    if (alarm) {
      const hour = Number(alarm[1]);
      const minute = Number(alarm[2] || 0);
      if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
        window.AndroidBridge.setAlarm(hour, minute, "LAIA");
        return {handled:true, reply:"Okay. Ich stelle den Wecker auf " + two(hour) + ":" + two(minute) + " Uhr."};
      }
    }

    const timer =
      t.match(/timer.*?(\d+)\s*(sek(?:unde[n]?)?|min(?:ute[n]?)?|st(?:unde[n]?)?|h\b)/i) ||
      t.match(/(?:stell|set).*?(\d+)\s*(sek(?:unde[n]?)?|min(?:ute[n]?)?|st(?:unde[n]?)?|h\b).*?timer/i);

    if (timer) {
      const amount = Number(timer[1]);
      const unit = timer[2];
      const seconds = Math.round(amount * (unit.startsWith("sek") ? 1 : unit.startsWith("min") ? 60 : 3600));
      if (seconds > 0 && seconds <= 86400) {
        window.AndroidBridge.setTimer(seconds, "LAIA");
        return {handled:true, reply:"Okay. Timer läuft."};
      }
    }

    const wantsOpen = /(?:öffne|oeffne|open|mach).*?(?:auf|bitte)?/i.test(t);
    if (wantsOpen) {
      const apps = [
        ["whatsapp", /\bwhatsapp\b/i, "WhatsApp"],
        ["spotify", /\bspotify\b/i, "Spotify"],
        ["maps", /\b(?:google\s*)?maps\b|\bkarte[n]?\b/i, "Maps"],
        ["camera", /\b(?:kamera|camera)\b/i, "die Kamera"],
        ["settings", /\b(?:einstellungen|settings)\b/i, "die Einstellungen"],
        ["clock", /\b(?:uhr|clock|wecker-app)\b/i, "die Uhr"]
      ];
      for (const [id, re, label] of apps) {
        if (re.test(t)) {
          window.AndroidBridge.openApp(id);
          return {handled:true, reply:"Okay, ich öffne " + label + "."};
        }
      }
    }

    return null;
  };
})();