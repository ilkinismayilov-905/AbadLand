// Live auction panel: polls /api/v1/auctions/{id}/state every 2 s, countdown from the server clock, bids via fetch + CSRF.
(function () {
  "use strict";
  const root = document.getElementById("auction-live");
  if (!root) return;
  const id = root.dataset.id;
  const $ = (sel) => root.querySelector(sel);
  const fmt = (n) => n == null ? "—" : Math.round(n).toLocaleString("az-AZ").replace(/ |,/g, " ") + " AZN";
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
  let state = null, offset = 0, lastPrice = null, lastEnds = null, busy = false;

  function show(msg, ok) {
    const m = $("[data-msg]");
    m.textContent = msg || "";
    m.className = "text-sm mt-2 " + (ok ? "text-green-700" : "text-red-700");
  }

  function render(s) {
    state = s;
    offset = s.serverNow - Date.now();
    $("[data-status]").textContent = s.statusLabel;
    const price = $("[data-price]");
    price.textContent = fmt(s.currentPrice ?? s.startPrice);
    if (lastPrice !== null && s.currentPrice !== lastPrice) {
      price.classList.add("bg-yellow-200");
      setTimeout(() => price.classList.remove("bg-yellow-200"), 900);
      if (!(s.me && s.me.isHighest)) show("", true); // someone else bid: old message is stale
    }
    const perHa = $("[data-per-ha]");
    if (perHa) perHa.textContent = "≈ " + fmt((s.currentPrice ?? s.startPrice) / Number(root.dataset.area)) + " " + window.t("js.auction.perHaYear", "/ ha / il");
    lastPrice = s.currentPrice;
    $("[data-price-label]").textContent = s.currentPrice == null ? window.t("js.auction.startPrice", "Başlanğıc qiymət") : window.t("js.auction.currentPrice", "Cari qiymət");
    $("[data-min]").textContent = fmt(s.minNextBid);
    $("[data-participants]").textContent = s.participants;
    if (lastEnds !== null && s.endsAt > lastEnds + 1000) show(window.t("js.auction.extended", "Vaxt 2 dəqiqə uzadıldı"), true);
    lastEnds = s.endsAt;
    const list = $("[data-bids]");
    list.replaceChildren(...s.bids.map((b, i) => {
      const li = document.createElement("li");
      li.className = "flex justify-between py-1 border-t" + (i === 0 ? " font-semibold" : "");
      const who = document.createElement("span");
      who.textContent = window.t("js.auction.participant", "İştirakçı #") + b.participantNo + (s.me && s.me.participantNo === b.participantNo ? " " + window.t("js.auction.you", "(siz)") : "")
        + (b.extended ? " · " + window.t("js.auction.extendedMark", "⏱ +2 dəq") : "");
      const amt = document.createElement("span");
      amt.textContent = fmt(b.amount) + " · " + new Date(b.at).toLocaleTimeString("az-AZ", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
      li.append(who, amt);
      return li;
    }));
    if (!s.bids.length) { const li = document.createElement("li"); li.className = "py-1 text-gray-500"; li.textContent = window.t("js.auction.noBids", "Hələ təklif yoxdur."); list.replaceChildren(li); }
    const live = s.status === "LIVE";
    const canBid = live && s.me && s.me.depositConfirmed;
    $("[data-controls]").hidden = !canBid;
    $("[data-mine]").textContent = s.me && s.me.isHighest ? window.t("js.auction.highest", "Ən yüksək təklif sizindir ✓") : "";
    if (!live && root.dataset.wasLive === "1") location.reload(); // ended / failed → server renders the result
    root.dataset.wasLive = live ? "1" : "0";
    tick();
  }

  function tick() {
    if (!state) return;
    const el = $("[data-countdown]");
    const target = state.status === "SCHEDULED" ? state.startsAt : state.endsAt;
    let ms = target - (Date.now() + offset);
    if (!["LIVE", "SCHEDULED"].includes(state.status)) { el.textContent = "—"; return; }
    if (ms < 0) ms = 0;
    const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    el.textContent = (state.status === "SCHEDULED" ? window.t("js.auction.startsIn", "Başlamağa:") + " " : "") + (h ? h + ":" : "") + String(m).padStart(2, "0") + ":" + String(sec).padStart(2, "0");
    el.classList.toggle("text-red-600", state.status === "LIVE" && ms < 60000);
  }

  async function poll() {
    try {
      const r = await fetch("/api/v1/auctions/" + id + "/state", { headers: { Accept: "application/json" } });
      if (r.ok) render(await r.json());
    } catch (e) { /* network hiccup: next poll retries */ }
  }

  async function bid(amount) {
    if (busy) return;
    busy = true;
    root.querySelectorAll("[data-controls] button").forEach((b) => (b.disabled = true));
    try {
      const body = new URLSearchParams({ amount: String(amount) });
      const r = await fetch("/herraclar/" + id + "/teklif", {
        method: "POST", body, headers: { [csrfHeader]: csrfToken, Accept: "application/json" }
      });
      const data = r.headers.get("content-type")?.includes("json") ? await r.json() : { ok: false, message: window.t("js.auction.sessionExpired", "Sessiya bitib — yenidən daxil olun.") };
      show(data.message, data.ok);
      await poll();
    } catch (e) {
      show(window.t("js.auction.networkError", "Bağlantı xətası — yenidən cəhd edin."), false);
    } finally {
      busy = false;
      root.querySelectorAll("[data-controls] button").forEach((b) => (b.disabled = false));
    }
  }

  root.querySelectorAll("[data-steps]").forEach((b) => b.addEventListener("click", () => {
    if (!state) return;
    const k = Number(b.dataset.steps);
    bid(state.currentPrice == null ? state.startPrice + (k - 1) * state.step : state.currentPrice + k * state.step);
  }));
  $("[data-custom]").addEventListener("submit", (e) => {
    e.preventDefault();
    const v = Number(e.target.amount.value);
    if (v > 0) bid(v); else show(window.t("js.auction.enterAmount", "Məbləği yazın."), false);
  });

  poll();
  setInterval(poll, 2000);
  setInterval(tick, 250);
})();
