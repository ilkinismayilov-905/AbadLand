// Plot filter: auto-apply on desktop, bottom sheet on mobile, Rayon options follow Region, empty params left out of the URL.
(function () {
  "use strict";
  document.querySelectorAll("form[data-plot-filter]").forEach((form) => {
    const desktop = window.matchMedia("(min-width: 768px)");
    const panel = form.querySelector(".pf-panel");
    const backdrop = form.querySelector(".pf-backdrop");
    const openBtn = form.querySelector("[data-pf-open]");
    const region = form.querySelector("[data-pf-region]");
    const district = form.querySelector("[data-pf-district]");

    const submit = () => form.requestSubmit ? form.requestSubmit() : form.submit();

    // Shareable URLs: only filled params
    form.addEventListener("submit", () => {
      form.querySelectorAll("input, select").forEach((el) => { if (el.name && el.value === "" && el.type !== "radio") el.disabled = true; });
      form.querySelectorAll('input[type=radio][value=""]:checked').forEach((el) => (el.disabled = true));
    });

    function filterDistricts() {
      if (!region || !district) return;
      [...district.options].forEach((o) => { o.hidden = !!o.value && !!region.value && o.dataset.region !== region.value; });
      if (district.selectedOptions[0]?.hidden) district.value = "";
    }
    // Back button (bfcache) restores the page with the fields we disabled on submit
    window.addEventListener("pageshow", () => form.querySelectorAll(":disabled").forEach((el) => (el.disabled = false)));
    region?.addEventListener("change", filterDistricts);
    filterDistricts();

    // Desktop: apply right away. Mobile: wait for "Tətbiq et".
    form.addEventListener("change", (e) => {
      if (e.target.name === "q" || !desktop.matches) return;
      submit();
    });
    let timer;
    form.q?.addEventListener("input", () => {
      if (!desktop.matches) return;
      clearTimeout(timer);
      timer = setTimeout(submit, 600);
    });

    function sheet(open) {
      panel.classList.toggle("open", open);
      backdrop.classList.toggle("open", open);
      openBtn?.setAttribute("aria-expanded", open);
      document.body.style.overflow = open ? "hidden" : "";
      if (open) panel.querySelector("select, input")?.focus();
    }
    openBtn?.addEventListener("click", () => sheet(true));
    form.querySelectorAll("[data-pf-close]").forEach((b) => b.addEventListener("click", () => sheet(false)));
    document.addEventListener("keydown", (e) => { if (e.key === "Escape" && panel.classList.contains("open")) sheet(false); });
    desktop.addEventListener("change", () => sheet(false));
  });
})();
