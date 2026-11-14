/* Rogbid Bridge history card, no external dependencies. */
class RogbidHistoryCard extends HTMLElement {
  static getConfigElement() { return document.createElement("rogbid-history-card-editor"); }
  static getStubConfig() { return { device_id: "king", title: "Rogbid Bridge" }; }

  setConfig(config) {
    if (!config.device_id || !/^[A-Za-z0-9_-]+$/.test(config.device_id)) {
      throw new Error("Rogbid Bridge card requires a valid device_id");
    }
    this._config = config;
    this._kind = this._kind || "heart";
    this._rows = [];
    this._loading = false;
    this._error = "";
    this._render();
  }

  set hass(hass) {
    this._hass = hass;
    if (this.isConnected && !this._loaded) {
      this._loaded = true;
      this._load();
    }
  }

  connectedCallback() {
    if (this._hass && !this._loaded) {
      this._loaded = true;
      this._load();
    }
  }

  disconnectedCallback() { this._loaded = false; }

  async _load() {
    if (!this._hass) return;
    const kind = this._kind;
    const request = (this._requestId || 0) + 1;
    this._requestId = request;
    this._loading = true;
    this._error = "";
    this._render();
    try {
      const result = await this._hass.callWS({
        type: "rogbid_bridge/history",
        device_id: this._config.device_id,
        kind,
        limit: 200
      });
      if (request === this._requestId) this._rows = result.records || [];
    } catch (error) {
      if (request === this._requestId) {
        this._rows = [];
        this._error = String(error.message || error);
      }
    } finally {
      if (request === this._requestId) {
        this._loading = false;
        this._render();
      }
    }
  }

  _render() {
    if (!this._config) return;
    if (!this._root) {
      this._root = this.attachShadow({ mode: "open" });
      this._root.innerHTML = `
        <style>
          ha-card { padding: 16px; }
          header { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; }
          h2 { margin:0; font-size:20px; }
          nav { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:12px; }
          button { border:1px solid var(--divider-color); background:transparent; color:var(--primary-text-color); padding:8px 10px; border-radius:8px; cursor:pointer; }
          button[aria-pressed="true"] { background:var(--primary-color); color:var(--text-primary-color, white); }
          table { border-collapse:collapse; width:100%; font-size:13px; }
          td,th { padding:9px 6px; border-bottom:1px solid var(--divider-color); text-align:left; }
          .scroll { max-height:420px; overflow:auto; }
          .hint { color:var(--secondary-text-color); padding:12px 0; }
        </style>
        <ha-card><header><h2></h2><button id="refresh" aria-label="Refresh history">↻</button></header>
          <nav></nav><div class="scroll"></div></ha-card>`;
      this._root.getElementById("refresh").addEventListener("click", () => this._load());
    }
    this._root.querySelector("h2").textContent = this._config.title || "Rogbid Bridge";
    const nav = this._root.querySelector("nav");
    nav.replaceChildren();
    for (const [key, label] of [["heart","❤️ Pulse"],["oxygen","🩸 SpO₂"],["steps","👣 Steps"],["sleep","🌙 Sleep"]]) {
      const button = document.createElement("button");
      button.textContent = label;
      button.setAttribute("aria-pressed", String(this._kind === key));
      button.addEventListener("click", () => {
        if (this._kind !== key) {
          this._kind = key;
          this._rows = [];
          this._load();
        }
      });
      nav.append(button);
    }
    const area = this._root.querySelector(".scroll");
    area.replaceChildren();
    if (this._loading || this._error || !this._rows.length) {
      const note = document.createElement("div");
      note.className = "hint";
      note.textContent = this._loading ? "Loading…" : this._error || "No records yet";
      area.append(note);
      return;
    }
    const table = document.createElement("table");
    const headings = this._kind === "heart" ? ["Date", "BPM"] :
      this._kind === "oxygen" ? ["Date", "SpO₂"] :
      this._kind === "steps" ? ["Date", "Steps", "kcal", "km"] :
      ["Wake date", "Duration", "Deep", "Light", "REM", "Score"];
    const tr = document.createElement("tr");
    for (const heading of headings) {
      const th = document.createElement("th");
      th.textContent = heading;
      tr.append(th);
    }
    const thead = document.createElement("thead");
    thead.append(tr);
    table.append(thead);
    const tbody = document.createElement("tbody");
    for (const record of this._rows) {
      const date = record.date || record.sleep_date || record.measured_at || "";
      const values = this._kind === "heart" ? [date, record.bpm] :
        this._kind === "oxygen" ? [date, record.spo2] :
        this._kind === "steps" ? [date, record.steps, record.kcal == null ? "" : Math.round(Number(record.kcal) / 1000), record.diatance == null ? "" : (Number(record.diatance) / 1000).toFixed(1)] :
        [date, record.duration_minutes, record.deep_minutes, record.light_minutes, record.fast_minutes, record.score];
      const row = document.createElement("tr");
      for (const value of values) {
        const td = document.createElement("td");
        td.textContent = value == null ? "—" : String(value);
        row.append(td);
      }
      tbody.append(row);
    }
    table.append(tbody);
    area.append(table);
  }

  getCardSize() { return 5; }
}

class RogbidHistoryCardEditor extends HTMLElement {
  setConfig(config) { this._config = config; this._render(); }
  _render() {
    this.replaceChildren();
    for (const [key, label] of [["device_id","MQTT device ID"],["title","Card title"]]) {
      const input = document.createElement("input");
      input.placeholder = label;
      input.value = this._config[key] || "";
      input.style.cssText = "display:block;margin:8px 0;padding:8px;width:90%";
      input.addEventListener("change", () => {
        this._config = { ...this._config, [key]: input.value };
        this.dispatchEvent(new CustomEvent("config-changed", { detail: { config: this._config }, bubbles: true, composed: true }));
      });
      this.append(input);
    }
  }
}
customElements.define("rogbid-history-card", RogbidHistoryCard);
customElements.define("rogbid-history-card-editor", RogbidHistoryCardEditor);
window.customCards = window.customCards || [];
window.customCards.push({ type: "rogbid-history-card", name: "Rogbid Bridge History", description: "Local journals from Rogbid Bridge" });
