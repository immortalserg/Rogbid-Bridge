/* Rogbid Bridge history card, no external dependencies. */
const KINDS = [["heart","❤️ Pulse"],["oxygen","🩸 SpO₂"],["steps","👣 Steps"],["sleep","🌙 Sleep"]];
const orderKinds = v => {
  const requested = Array.isArray(v) ? v : [];
  return [...new Set([...requested.filter(k=>KINDS.some(([id])=>id===k)),...KINDS.map(([id])=>id)])];
};

const formatRogbidDateTime = value => {
  if (value == null || value === "") return "—";
  const text = String(value).trim();
  // Watch timestamps may be ISO-like, with or without a timezone.
  const match = text.match(/^(\d{4})[-.\/](\d{1,2})[-.\/](\d{1,2})[T\s]+(\d{1,2}):(\d{2})/);
  if (match) {
    const [,year,month,day,hour,minute] = match;
    return year+"."+month.padStart(2,"0")+"."+day.padStart(2,"0")+" "+hour.padStart(2,"0")+":"+minute;
  }
  const parsed = new Date(text);
  if (!Number.isNaN(parsed.getTime())) {
    const pad = n => String(n).padStart(2,"0");
    return parsed.getFullYear()+"."+pad(parsed.getMonth()+1)+"."+pad(parsed.getDate())+
      " "+pad(parsed.getHours())+":"+pad(parsed.getMinutes());
  }
  return text;
};
const formatRogbidMinutes = value => {
  if (value == null || value === "") return "—";
  const minutes = Number(value);
  if (!Number.isFinite(minutes) || minutes < 0) return "—";
  const rounded = Math.round(minutes);
  return Math.floor(rounded/60)+" ч "+String(rounded%60).padStart(2,"0")+" мин";
};
class RogbidHistoryCard extends HTMLElement {
  static getConfigElement() { return document.createElement("rogbid-history-card-editor"); }
  static getStubConfig() { return { title: "Rogbid Bridge" }; }

  setConfig(config) {
    if (config.device_id && !/^[A-Za-z0-9_-]+$/.test(config.device_id)) {
      throw new Error("Rogbid Bridge card requires a valid device_id");
    }
    this._config = config;
    this._deviceId = config.device_id || null;
    this._devices = [];
    this._kind = this._kind || orderKinds(config.button_order)[0];
    this._rows = [];
    this._loading = false;
    this._error = "";
    this._render();
  }

  set hass(hass) {
    this._hass = hass;
    if (this.isConnected && !this._loaded) {
      this._loaded = true;
      this._initialize();
    }
  }

  connectedCallback() {
    if (this._hass && !this._loaded) {
      this._loaded = true;
      this._initialize();
    }
  }

  disconnectedCallback() { this._loaded = false; }

  async _initialize() {
    try {
      const result = await this._hass.callWS({ type: "rogbid_bridge/devices" });
      this._devices = result.devices || [];
      if (!this._deviceId && this._devices.length) this._deviceId = this._devices[0].device_id;
      this._render();
      if (this._deviceId) await this._load();
    } catch (error) {
      this._error = String(error.message || error);
      this._render();
    }
  }

  async _load() {
    if (!this._hass || !this._deviceId) return;
    const kind = this._kind;
    const request = (this._requestId || 0) + 1;
    this._requestId = request;
    this._loading = true;
    this._error = "";
    this._render();
    try {
      const result = await this._hass.callWS({
        type: "rogbid_bridge/history",
        device_id: this._deviceId,
        kind,
        limit: Math.max(1,Math.min(500,Number(this._config.history_limit)||200))
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
          select { max-width:100%; padding:6px; background:var(--card-background-color); color:var(--primary-text-color); border:1px solid var(--divider-color); border-radius:6px; }
          nav { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:12px; }
          button { border:1px solid var(--divider-color); background:transparent; color:var(--primary-text-color); padding:8px 10px; border-radius:8px; cursor:pointer; }
          button[aria-pressed="true"] { background:var(--primary-color); color:var(--text-primary-color, white); }
          table { border-collapse:collapse; width:100%; font-size:13px; }
          td,th { padding:9px 6px; border-bottom:1px solid var(--divider-color); text-align:left; }
          .scroll { overflow:auto; }
          svg.history-chart { width:100%; height:auto; color:var(--primary-text-color); }
          .hint { color:var(--secondary-text-color); padding:12px 0; }
          .sleep-row { cursor:pointer; }
          .sleep-row:focus { outline:2px solid var(--primary-color); }
          .sleep-detail { padding:12px 4px; }
          .stage-track { display:flex; height:32px; border-radius:6px; overflow:hidden; margin:12px 0 6px; background:var(--divider-color); }
          .stage-segment { min-width:1px; }
          .stage-labels { display:flex; justify-content:space-between; font-size:12px; color:var(--secondary-text-color); }
          .legend { font-size:12px; margin:8px 0; }
        </style>
        <ha-card><header><h2></h2><button id="refresh" aria-label="Refresh history">↻</button></header>
          <nav></nav><div class="scroll"></div></ha-card>`;
      this._root.getElementById("refresh").addEventListener("click", () => this._load());
    }
    this._root.querySelector("h2").textContent = this._config.title || "Rogbid Bridge";
    const header = this._root.querySelector("header");
    const oldSelect = header.querySelector("select");
    if (oldSelect) oldSelect.remove();
    if (this._devices.length > 1) {
      const select = document.createElement("select");
      select.setAttribute("aria-label", "Rogbid watch");
      for (const device of this._devices) {
        const option = document.createElement("option");
        option.value = device.device_id;
        option.textContent = device.name || ("Rogbid " + device.device_id.slice(-8));
        select.append(option);
      }
      select.value = this._deviceId;
      select.addEventListener("change", () => { this._deviceId = select.value; this._load(); });
      header.insertBefore(select, this._root.getElementById("refresh"));
    }
    const nav = this._root.querySelector("nav");
    nav.replaceChildren();
    for (const key of orderKinds(this._config.button_order)) {
      const label = KINDS.find(([id])=>id===key)[1];
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
    area.style.maxHeight = Math.max(120,Math.min(1200,Number(this._config.history_height)||420))+"px";
    if (this._loading || this._error || !this._rows.length) {
      const note = document.createElement("div");
      note.className = "hint";
      note.textContent = this._loading ? "Loading…" : this._error || (!this._deviceId ? "No Rogbid watches configured" : "No records yet");
      area.append(note);
      return;
    }
    if (this._config.view_mode === "graph") { this._renderGraph(area); return; }
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
      const rawDate = record.date || record.sleep_date || record.measured_at || "";
      const date = this._kind === "heart" || this._kind === "oxygen" ? formatRogbidDateTime(rawDate) : rawDate;
      const values = this._kind === "heart" ? [date, record.bpm] :
        this._kind === "oxygen" ? [date, record.spo2] :
        this._kind === "steps" ? [date, record.steps, record.kcal == null ? "" : Math.round(Number(record.kcal) / 1000), record.diatance == null ? "" : (Number(record.diatance) / 1000).toFixed(1)] :
        [date, formatRogbidMinutes(record.duration_minutes), formatRogbidMinutes(record.deep_minutes), formatRogbidMinutes(record.light_minutes), formatRogbidMinutes(record.fast_minutes), record.score];
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
    if (this._kind === "sleep") {
      for (const [index, tr] of Array.from(tbody.children).entries()) {
        tr.className = "sleep-row";
        tr.tabIndex = 0;
        tr.setAttribute("role", "button");
        tr.setAttribute("aria-label", "View sleep stages");
        const select = () => this._showSleep(this._rows[index], area);
        tr.addEventListener("click", select);
        tr.addEventListener("keydown", event => {
          if (event.key === "Enter" || event.key === " ") { event.preventDefault(); select(); }
        });
      }
    }
  }


  _renderGraph(area) {
    const metric = {heart:"bpm",oxygen:"spo2",steps:"steps",sleep:"duration_minutes"}[this._kind];
    const points = this._rows.map((r,i)=>({
      x:Date.parse(r.measured_at||r.date||r.sleep_date||""),
      y:Number(r[metric]), label:String(r.measured_at||r.date||r.sleep_date||i),
      valid:r[metric]!==null && r[metric]!==undefined && r[metric]!==""
    })).filter(p=>p.valid && Number.isFinite(p.y));
    if (!points.length) { area.textContent="No numeric data"; return; }
    if (points.some(p=>!Number.isFinite(p.x))) points.reverse().forEach((p,i)=>p.x=i);
    else points.sort((a,b)=>a.x-b.x);
    const x0=Math.min(...points.map(p=>p.x)), x1=Math.max(...points.map(p=>p.x));
    const y0=Math.min(...points.map(p=>p.y)), y1=Math.max(...points.map(p=>p.y));
    const X=x=>50+530*(x-x0)/(x1-x0||1), Y=y=>220-190*(y-y0)/(y1-y0||1);
    const ns="http://www.w3.org/2000/svg", svg=document.createElementNS(ns,"svg");
    svg.setAttribute("viewBox","0 0 600 260"); svg.classList.add("history-chart");
    svg.setAttribute("role","img"); svg.setAttribute("aria-label",this._kind+" history");
    const make=(tag,attrs)=>{const el=document.createElementNS(ns,tag);Object.entries(attrs).forEach(([k,v])=>el.setAttribute(k,String(v)));svg.append(el);return el;};
    make("line",{x1:50,y1:220,x2:580,y2:220,stroke:"currentColor"});
    make("line",{x1:50,y1:30,x2:50,y2:220,stroke:"currentColor"});
    for(let i=0;i<=4;i++){const val=y0+(y1-y0)*i/4;
      const t=make("text",{x:45,y:Y(val)+4,"text-anchor":"end",fill:"currentColor","font-size":12});
      t.textContent=val.toFixed(1);
    }
    make("polyline",{points:points.map(p=>X(p.x)+","+Y(p.y)).join(" "),fill:"none",stroke:"var(--primary-color)", "stroke-width":2.5});
    points.forEach(p=>{const dot=make("circle",{cx:X(p.x),cy:Y(p.y),r:3,fill:"var(--primary-color)"});
      const title=document.createElementNS(ns,"title");title.textContent=p.label+": "+p.y;dot.append(title);});
    for(const [i,anchor] of [[0,"start"],[points.length-1,"end"]]){
      const p=points[i], t=make("text",{x:X(p.x),y:245,"text-anchor":anchor,fill:"currentColor","font-size":11});
      t.textContent=p.label.slice(0,16);
    }
    area.append(svg);
  }

  _showSleep(record, area) {
    const detail = document.createElement("div");
    detail.className = "sleep-detail";
    const title = document.createElement("strong");
    title.textContent = (record.sleep_date || "") + "  " +
      (record.start_time || "") + "–" + (record.end_time || "");
    detail.append(title);
    let stages;
    try {
      stages = typeof record.stages_json === "string" ?
        JSON.parse(record.stages_json) : record.stages_json;
    } catch (_) { stages = []; }
    if (!Array.isArray(stages) || stages.length < 2) {
      const hint = document.createElement("div");
      hint.className = "hint";
      hint.textContent = "No stage timeline available";
      detail.append(hint);
      area.prepend(detail);
      return;
    }
    const minute = value => {
      if (typeof value !== "string" || !/^\\d{1,2}:\\d{2}$/.test(value)) return null;
      const [hour, min] = value.split(":").map(Number);
      return hour < 24 && min < 60 ? hour * 60 + min : null;
    };
    let offset = 0, previous = -1;
    const timeline = [];
    for (const stage of stages) {
      const time = minute(stage.time);
      if (time === null) continue;
      if (previous >= 0 && time < previous) offset += 1440;
      previous = time;
      timeline.push({ time: time + offset, type: Number(stage.type), label: stage.time });
    }
    const track = document.createElement("div");
    track.className = "stage-track";
    const start = timeline[0]?.time;
    const end = timeline[timeline.length - 1]?.time;
    if (timeline.length < 2 || end <= start) {
      detail.append(document.createTextNode("No valid stage timeline"));
      area.prepend(detail);
      return;
    }
    for (let i = 0; i < timeline.length - 1; i++) {
      const stage = timeline[i], next = timeline[i + 1];
      if (next.time <= stage.time) continue;
      const segment = document.createElement("div");
      segment.className = "stage-segment";
      segment.style.flex = String(next.time - stage.time);
      segment.style.background = stage.type === 1 ? "#2d5fd2" :
        stage.type === 2 ? "#32beba" : "#888";
      segment.title = stage.label + " — " + next.label + ": " +
        (stage.type === 1 ? "Deep" : stage.type === 2 ? "Light" : "Other/end");
      track.append(segment);
    }
    detail.append(track);
    const labels = document.createElement("div");
    labels.className = "stage-labels";
    for (const label of [timeline[0].label, timeline[timeline.length - 1].label]) {
      const span = document.createElement("span");
      span.textContent = label;
      labels.append(span);
    }
    detail.append(labels);
    const legend = document.createElement("div");
    legend.className = "legend";
    legend.textContent = "■ Deep (blue)   ■ Light (teal)   ■ Other / end (gray)";
    detail.append(legend);
    const old = area.querySelector(".sleep-detail");
    if (old) old.remove();
    area.prepend(detail);
  }

  getCardSize() { return 5; }
}

class RogbidHistoryCardEditor extends HTMLElement {
  setConfig(config) { this._config = config; this._render(); }
  set hass(hass) {
    if (this._hass === hass || this._fetching || this._devices) return;
    this._hass = hass;
    this._fetching = true;
    hass.callWS({ type: "rogbid_bridge/devices" })
      .then(result => { this._devices = result.devices || []; this._render(); })
      .catch(() => { this._devices = []; this._render(); })
      .finally(() => { this._fetching = false; });
  }
  _update(key, value) {
    const config = { ...this._config };
    if (value) config[key] = value;
    else delete config[key];
    this._config = config;
    this.dispatchEvent(new CustomEvent("config-changed", {
      detail: { config }, bubbles: true, composed: true
    }));
  }
  _render() {
    this.replaceChildren();
    const label = document.createElement("label");
    label.textContent = "Rogbid watch";
    label.style.display = "block";
    this.append(label);
    const select = document.createElement("select");
    select.style.cssText = "display:block;margin:8px 0;padding:8px;width:95%";
    const auto = document.createElement("option");
    auto.value = "";
    auto.textContent = "Automatic (first configured watch)";
    select.append(auto);
    const devices = this._devices || [];
    for (const device of devices) {
      const option = document.createElement("option");
      option.value = device.device_id;
      option.textContent = device.name || ("Rogbid " + device.device_id.slice(-8));
      select.append(option);
    }
    if (this._config.device_id && !devices.some(d => d.device_id === this._config.device_id)) {
      const option = document.createElement("option");
      option.value = this._config.device_id;
      option.textContent = this._config.device_id;
      select.append(option);
    }
    select.value = this._config.device_id || "";
    select.addEventListener("change", () => this._update("device_id", select.value));
    this.append(select);
    const input = document.createElement("input");
    input.placeholder = "Card title";
    input.value = this._config.title || "";
    input.style.cssText = "display:block;margin:8px 0;padding:8px;width:90%";
    input.addEventListener("change", () => this._update("title", input.value));
    this.append(input);
    const add=(labelText,el)=>{const label=document.createElement("label");label.textContent=labelText;
      label.style.cssText="display:block;margin-top:12px";el.style.cssText="display:block;margin:6px 0;padding:8px;width:95%";
      this.append(label,el);};
    const heading=document.createElement("div");heading.textContent="Button order";heading.style.marginTop="12px";this.append(heading);
    const order=orderKinds(this._config.button_order);
    order.forEach((key,i)=>{const row=document.createElement("div");row.style.cssText="display:flex;gap:8px;align-items:center;margin:5px 0";
      const label=document.createElement("span");label.textContent=KINDS.find(([id])=>id===key)[1];label.style.flex="1";row.append(label);
      for(const [symbol,delta] of [["↑",-1],["↓",1]]){const button=document.createElement("button");button.textContent=symbol;
        button.disabled=i+delta<0||i+delta>=order.length;
        button.addEventListener("click",()=>{const next=[...order];[next[i],next[i+delta]]=[next[i+delta],next[i]];this._update("button_order",next);});
        row.append(button);}this.append(row);});
    const limit=document.createElement("input");limit.type="number";limit.min="1";limit.max="500";
    limit.value=String(this._config.history_limit||200);
    limit.addEventListener("change",()=>this._update("history_limit",Math.max(1,Math.min(500,Number(limit.value)||200))));
    add("History records (1–500)",limit);
    const height=document.createElement("input");height.type="number";height.min="120";height.max="1200";
    height.value=String(this._config.history_height||420);
    height.addEventListener("change",()=>this._update("history_height",Math.max(120,Math.min(1200,Number(height.value)||420))));
    add("History height (px)",height);
    const mode=document.createElement("select");
    for(const [value,label] of [["table","Table"],["graph","Graph"]]){const option=document.createElement("option");
      option.value=value;option.textContent=label;mode.append(option);}
    mode.value=this._config.view_mode||"table";
    mode.addEventListener("change",()=>this._update("view_mode",mode.value));add("History display",mode);
  }
}
customElements.define("rogbid-history-card", RogbidHistoryCard);
customElements.define("rogbid-history-card-editor", RogbidHistoryCardEditor);
window.customCards = window.customCards || [];
window.customCards.push({ type: "rogbid-history-card", name: "Rogbid Bridge History", description: "Local journals from Rogbid Bridge" });
