// Matching quiz: reveal conditional inputs, progress counter, thousands formatting, client-side checks (server re-validates).
(function () {
  "use strict";
  const form = document.getElementById("quiz");
  if (!form) return;
  const questions = ["activity", "budget", "size", "district", "experience"];
  const checked = (name) => form.querySelector('input[name="' + name + '"]:checked');

  function sync() {
    form.querySelectorAll(".reveal[data-when]").forEach((r) => {
      const [name, value] = r.dataset.when.split("=");
      const open = checked(name)?.value === value;
      r.classList.toggle("open", open);
      r.querySelectorAll("input").forEach((i) => { i.required = open; i.tabIndex = open ? 0 : -1; });
    });
    form.querySelector("[data-progress]").textContent = questions.filter(checked).length;
  }

  // "150000" → "150 000" while typing; keeps the caret near the end
  form.querySelectorAll("[data-thousands]").forEach((i) => i.addEventListener("input", () => {
    const digits = i.value.replace(/\D/g, "").slice(0, 12);
    i.value = digits.replace(/\B(?=(\d{3})+(?!\d))/g, " ");
  }));

  form.addEventListener("change", sync);
  form.addEventListener("submit", (e) => {
    const missing = questions.find((q) => !checked(q));
    let msg = missing ? "Zəhmət olmasa bütün 5 suala cavab verin." : null;
    const budget = form.budgetCustom;
    if (!msg && checked("budget").value === "0") {
      const v = Number(budget.value.replace(/\D/g, ""));
      if (!v) msg = "Büdcə məbləğini yazın.";
      else if (v < 10000) msg = "Büdcə ən azı 10 000 AZN olmalıdır.";
      else if (v % 1000) msg = "Büdcə 1 000 AZN-ə bölünən məbləğ olmalıdır.";
    }
    if (!msg && checked("activity").value === "OTHER" && !form.otherText.value.trim()) msg = "Fəaliyyət növünü yazın.";
    if (!msg && checked("size").value === "custom") {
      const lo = parseFloat(form.minHa.value), hi = parseFloat(form.maxHa.value);
      if (isNaN(lo) || isNaN(hi)) msg = "Sahə üçün minimum və maksimum hektarı yazın.";
      else if (lo < 0.5) msg = "Minimum sahə ən azı 0,5 ha olmalıdır.";
      else if (hi < lo) msg = "Maksimum sahə minimumdan kiçik ola bilməz.";
    }
    if (msg) {
      e.preventDefault();
      let box = document.getElementById("quiz-error");
      if (!box) {
        box = document.createElement("div");
        box.id = "quiz-error";
        box.setAttribute("role", "alert");
        box.className = "p-3 rounded-xl bg-red-100 text-red-800 border border-red-200";
        form.prepend(box);
      }
      box.textContent = msg;
      box.scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }
    // Shareable URL: leave out empty conditional inputs
    form.querySelectorAll(".reveal input").forEach((i) => { if (!i.value) i.disabled = true; });
  });
  window.addEventListener("pageshow", () => form.querySelectorAll(":disabled").forEach((i) => (i.disabled = false)));
  sync();
})();
