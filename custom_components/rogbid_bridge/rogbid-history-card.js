/* Rogbid Bridge history card, no external dependencies. */
const KINDS = [["heart","❤️ Pulse"],["oxygen","🩸 SpO₂"],["steps","👣 Steps"],["kcal","🔥 kcal"],["km","📍 km"],["sleep","🌙 Sleep"]];
const EXTRA_KINDS = ["kcal","km"];
const historyKind = kind => EXTRA_KINDS.includes(kind) ? "steps" : kind;
const viewForKind = (config,kind) => config?.view_modes?.[kind] || config?.view_mode || "table";
const orderKinds = v => {
  const requested = Array.isArray(v) ? v : [];
  return [...new Set([...requested.filter(k=>KINDS.some(([id])=>id===k)),...KINDS.map(([id])=>id)])];
};


const ROGBID_TRANSLATIONS = {"en":{"heart":"Pulse","oxygen":"Oxygen","steps":"Steps","sleep":"Sleep","date":"Date","wakeDate":"Wake date","duration":"Duration","deep":"Deep","light":"Light","score":"Score","delete":"Delete","loading":"Loading…","empty":"No records yet","noWatch":"No watches configured","showTitle":"Show title","sections":"Visible sections","order":"Button order","limit":"History records (1–500)","height":"History height (px)","display":"History display","table":"Table","graph":"Graph","showDelete":"Show delete buttons","language":"Language","haLanguage":"Home Assistant language","autoWatch":"Automatic watch","cardTitle":"Card title","noNumeric":"No numeric data","noStages":"No sleep stages available","invalidStages":"No valid stage timeline","other":"Other / end","refresh":"Refresh history","confirmDelete":"Delete record from Home Assistant? It will remain on the watch.","deleteError":"Could not delete record","watch":"Watch"},"ru":{"heart":"Пульс","oxygen":"Кислород","steps":"Шаги","sleep":"Сон","date":"Дата","wakeDate":"Дата пробуждения","duration":"Продолжительность","deep":"Глубокий","light":"Лёгкий","score":"Оценка","delete":"Удалить","loading":"Загрузка…","empty":"Записей пока нет","noWatch":"Часы не настроены","showTitle":"Показывать заголовок","sections":"Отображаемые разделы","order":"Порядок кнопок","limit":"Количество записей (1–500)","height":"Высота истории (пикс.)","display":"Отображение истории","table":"Таблица","graph":"График","showDelete":"Показывать кнопки удаления","language":"Язык","haLanguage":"Язык Home Assistant","autoWatch":"Автоматический выбор часов","cardTitle":"Заголовок карточки","noNumeric":"Нет числовых данных","noStages":"Нет данных о фазах сна","invalidStages":"Некорректная шкала фаз сна","other":"Другое / конец","refresh":"Обновить историю","confirmDelete":"Удалить запись из Home Assistant? На часах она останется.","deleteError":"Не удалось удалить запись","watch":"Часы"},"fr":{"heart":"Pouls","oxygen":"Oxygène","steps":"Pas","sleep":"Sommeil","date":"Date","wakeDate":"Date de réveil","duration":"Durée","deep":"Profond","light":"Léger","score":"Score","delete":"Supprimer","loading":"Chargement…","empty":"Aucune donnée","noWatch":"Aucune montre configurée","showTitle":"Afficher le titre","sections":"Sections visibles","order":"Ordre des boutons","limit":"Enregistrements (1–500)","height":"Hauteur (px)","display":"Affichage de l'historique","table":"Tableau","graph":"Graphique","showDelete":"Afficher les boutons de suppression","language":"Langue","haLanguage":"Langue de Home Assistant","autoWatch":"Montre automatique","cardTitle":"Titre de la carte","noNumeric":"Aucune donnée numérique","noStages":"Aucune phase de sommeil","invalidStages":"Chronologie invalide","other":"Autre / fin","refresh":"Actualiser","confirmDelete":"Supprimer de Home Assistant ? La montre conserve cette donnée.","deleteError":"Suppression impossible","watch":"Montre"},"de":{"heart":"Puls","oxygen":"Sauerstoff","steps":"Schritte","sleep":"Schlaf","date":"Datum","wakeDate":"Aufwachdatum","duration":"Dauer","deep":"Tief","light":"Leicht","score":"Bewertung","delete":"Löschen","loading":"Laden…","empty":"Keine Einträge","noWatch":"Keine Uhr eingerichtet","showTitle":"Titel anzeigen","sections":"Sichtbare Bereiche","order":"Schaltflächenreihenfolge","limit":"Einträge (1–500)","height":"Höhe (px)","display":"Verlauf anzeigen","table":"Tabelle","graph":"Diagramm","showDelete":"Löschtasten anzeigen","language":"Sprache","haLanguage":"Home Assistant-Sprache","autoWatch":"Uhr automatisch","cardTitle":"Kartentitel","noNumeric":"Keine Zahlenwerte","noStages":"Keine Schlafphasen","invalidStages":"Ungültiger Verlauf","other":"Andere / Ende","refresh":"Aktualisieren","confirmDelete":"Eintrag aus Home Assistant löschen? Auf der Uhr bleibt er erhalten.","deleteError":"Löschen fehlgeschlagen","watch":"Uhr"},"lb":{"heart":"Puls","oxygen":"Sauerstoff","steps":"Schrëtt","sleep":"Schlof","date":"Datum","wakeDate":"Erwächen","duration":"Dauer","deep":"Déif","light":"Liicht","score":"Bewäertung","delete":"Läschen","loading":"Lueden…","empty":"Keng Donnéeën","noWatch":"Keng Auer ageriicht","showTitle":"Titel weisen","sections":"Siichtbar Beräicher","order":"Reiefolleg","limit":"Entréeën (1–500)","height":"Héicht (px)","display":"Geschicht uweisen","table":"Tabell","graph":"Grafik","showDelete":"Läschknäppercher weisen","language":"Sprooch","haLanguage":"Home Assistant-Sprooch","autoWatch":"Automatesch Auer","cardTitle":"Kaartentitel","noNumeric":"Keng numeresch Donnéeën","noStages":"Keng Schlofphasen","invalidStages":"Ongëlteg Zäitlinn","other":"Aner / Enn","refresh":"Aktualiséieren","confirmDelete":"Entrée aus Home Assistant läschen? Op der Auer bleift se.","deleteError":"Läschen net méiglech","watch":"Auer"},"it":{"heart":"Battito","oxygen":"Ossigeno","steps":"Passi","sleep":"Sonno","date":"Data","wakeDate":"Risveglio","duration":"Durata","deep":"Profondo","light":"Leggero","score":"Punteggio","delete":"Elimina","loading":"Caricamento…","empty":"Nessun dato","noWatch":"Nessun orologio configurato","showTitle":"Mostra titolo","sections":"Sezioni visibili","order":"Ordine pulsanti","limit":"Record (1–500)","height":"Altezza (px)","display":"Visualizzazione","table":"Tabella","graph":"Grafico","showDelete":"Mostra pulsanti elimina","language":"Lingua","haLanguage":"Lingua Home Assistant","autoWatch":"Orologio automatico","cardTitle":"Titolo scheda","noNumeric":"Nessun dato numerico","noStages":"Fasi del sonno assenti","invalidStages":"Sequenza non valida","other":"Altro / fine","refresh":"Aggiorna","confirmDelete":"Eliminare da Home Assistant? Resterà sull'orologio.","deleteError":"Eliminazione non riuscita","watch":"Orologio"},"pt":{"heart":"Pulso","oxygen":"Oxigénio","steps":"Passos","sleep":"Sono","date":"Data","wakeDate":"Despertar","duration":"Duração","deep":"Profundo","light":"Leve","score":"Pontuação","delete":"Eliminar","loading":"A carregar…","empty":"Sem registos","noWatch":"Sem relógio configurado","showTitle":"Mostrar título","sections":"Secções visíveis","order":"Ordem dos botões","limit":"Registos (1–500)","height":"Altura (px)","display":"Visualização","table":"Tabela","graph":"Gráfico","showDelete":"Mostrar botões de eliminação","language":"Idioma","haLanguage":"Idioma do Home Assistant","autoWatch":"Relógio automático","cardTitle":"Título do cartão","noNumeric":"Sem dados numéricos","noStages":"Sem fases de sono","invalidStages":"Cronologia inválida","other":"Outro / fim","refresh":"Atualizar","confirmDelete":"Eliminar do Home Assistant? Permanecerá no relógio.","deleteError":"Erro ao eliminar","watch":"Relógio"},"es":{"heart":"Pulso","oxygen":"Oxígeno","steps":"Pasos","sleep":"Sueño","date":"Fecha","wakeDate":"Despertar","duration":"Duración","deep":"Profundo","light":"Ligero","score":"Puntuación","delete":"Eliminar","loading":"Cargando…","empty":"Sin registros","noWatch":"Sin relojes configurados","showTitle":"Mostrar título","sections":"Secciones visibles","order":"Orden de botones","limit":"Registros (1–500)","height":"Altura (px)","display":"Visualización","table":"Tabla","graph":"Gráfico","showDelete":"Mostrar botones de eliminación","language":"Idioma","haLanguage":"Idioma de Home Assistant","autoWatch":"Reloj automático","cardTitle":"Título de tarjeta","noNumeric":"Sin datos numéricos","noStages":"Sin fases de sueño","invalidStages":"Cronología no válida","other":"Otro / fin","refresh":"Actualizar","confirmDelete":"¿Eliminar de Home Assistant? Permanecerá en el reloj.","deleteError":"Error al eliminar","watch":"Reloj"}};
const rogbidLang = (config,hass) => {
  const raw = config?.language && config.language !== "auto" ? config.language :
    (hass?.locale?.language || hass?.language || "en");
  const key = String(raw).toLowerCase().split(/[-_]/)[0];
  return ROGBID_TRANSLATIONS[key] ? key : "en";
};
const rogbidT = (config,hass,key) =>
  ROGBID_TRANSLATIONS[rogbidLang(config,hass)][key] || ROGBID_TRANSLATIONS.en[key] || key;
const visibleKinds = config => orderKinds(config?.button_order).filter(k =>
  !Array.isArray(config?.hidden_sections) || !config.hidden_sections.includes(k));

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
const formatRogbidMinutes = (value, language = "en") => {
  if (value == null || value === "") return "—";
  const minutes = Number(value);
  if (!Number.isFinite(minutes) || minutes < 0) return "—";
  const rounded = Math.round(minutes);
  return language === "ru" ? Math.floor(rounded/60)+" ч "+String(rounded%60).padStart(2,"0")+" мин" : Math.floor(rounded/60)+" h "+String(rounded%60).padStart(2,"0")+" min";
};
class RogbidHistoryCard extends HTMLElement {
  static getConfigElement() { return document.createElement("rogbid-history-card-editor"); }
  static getStubConfig() { return { title: "Rogbid Bridge" }; }

  setConfig(config) {
    if (config.device_id && !/^[A-Za-z0-9_-]+$/.test(config.device_id)) {
      throw new Error("Rogbid Bridge card requires a valid device_id");
    }
    const previousDevice = this._deviceId;
    const previousKind = this._kind;
    this._config = config;
    this._deviceId = config.device_id || previousDevice || null;
    this._devices = this._devices || [];
    this._kind = visibleKinds(config).includes(this._kind) ? this._kind : visibleKinds(config)[0] || null;
    this._rows = [];
    this._loading = false;
    this._error = "";
    this._render();
    if (this._hass && this.isConnected && this._deviceId &&
        (previousKind !== this._kind || (config.device_id && previousDevice !== config.device_id))) {
      this._load();
    }
  }

  set hass(hass) {
    const previousLanguage = rogbidLang(this._config,this._hass);
    this._hass = hass;
    if (this._config && previousLanguage !== rogbidLang(this._config,hass)) this._render();
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
    if (!this._hass || !this._deviceId || !this._kind) return;
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
        kind: historyKind(kind),
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
    const t = key => rogbidT(this._config,this._hass,key);
    const heading = this._root.querySelector("h2");
    heading.textContent = this._config.title || "Rogbid Bridge";
    heading.hidden = this._config.show_title === false;
    this._root.getElementById("refresh").title = t("refresh");
    this._root.getElementById("refresh").setAttribute("aria-label",t("refresh"));
    const header = this._root.querySelector("header");
    const oldSelect = header.querySelector("select");
    if (oldSelect) oldSelect.remove();
    if (this._devices.length > 1) {
      const select = document.createElement("select");
      select.setAttribute("aria-label", t("watch"));
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
    for (const key of visibleKinds(this._config)) {
      const label = KINDS.find(([id])=>id===key)[1].split(" ")[0] + " " + (EXTRA_KINDS.includes(key) ? key : t(key));
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
    if (!this._kind) { const note = document.createElement("div"); note.className="hint"; note.textContent=t("sections"); area.append(note); return; }
    if (this._loading || this._error || !this._rows.length) {
      const note = document.createElement("div");
      note.className = "hint";
      note.textContent = this._loading ? t("loading") : this._error || (!this._deviceId ? t("noWatch") : t("empty"));
      area.append(note);
      return;
    }
    if (viewForKind(this._config,this._kind) === "graph") { this._renderGraph(area); return; }
    const table = document.createElement("table");
    const headings = this._kind === "heart" ? [t("date"), "BPM"] :
      this._kind === "oxygen" ? [t("date"), "SpO₂"] :
      this._kind === "steps" ? [t("date"), t("steps"), "kcal", "km"] :
      this._kind === "kcal" ? [t("date"), "kcal"] :
      this._kind === "km" ? [t("date"), "km"] :
      [t("wakeDate"), t("duration"), t("deep"), t("light"), "REM", t("score")];
    if (this._config.show_delete_buttons === true && this._hass?.user?.is_admin) headings.push(t("delete"));
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
        this._kind === "kcal" ? [date, record.kcal == null ? "" : Math.round(Number(record.kcal) / 1000)] :
        this._kind === "km" ? [date, record.diatance == null ? "" : (Number(record.diatance) / 1000).toFixed(1)] :
        [date, formatRogbidMinutes(record.duration_minutes,rogbidLang(this._config,this._hass)), formatRogbidMinutes(record.deep_minutes,rogbidLang(this._config,this._hass)), formatRogbidMinutes(record.light_minutes,rogbidLang(this._config,this._hass)), formatRogbidMinutes(record.fast_minutes,rogbidLang(this._config,this._hass)), record.score];
      const row = document.createElement("tr");
      for (const value of values) {
        const td = document.createElement("td");
        td.textContent = value == null ? "—" : String(value);
        row.append(td);
      }
      if (this._config.show_delete_buttons === true && this._hass?.user?.is_admin) {
        const cell = document.createElement("td");
        const button = document.createElement("button");
        button.textContent = "🗑";
        button.title = t("delete");
        button.setAttribute("aria-label", t("delete"));
        button.addEventListener("click", async event => {
          event.stopPropagation();
          if (!record._record_id) return;
          if (!confirm(t("confirmDelete"))) return;
          button.disabled = true;
          try {
            await this._hass.callWS({
              type: "rogbid_bridge/delete_record",
              device_id: this._deviceId, kind: historyKind(this._kind),
              record_id: record._record_id
            });
            await this._load();
          } catch (error) {
            button.disabled = false;
            alert(t("deleteError") + ": " + String(error.message || error));
          }
        });
        cell.append(button);
        row.append(cell);
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
    const metric = {heart:"bpm",oxygen:"spo2",steps:"steps",kcal:"kcal",km:"diatance",sleep:"duration_minutes"}[this._kind];
    const scale = this._kind === "kcal" || this._kind === "km" ? 1000 : 1;
    const points = this._rows.map((r,i)=>({
      x:Date.parse(r.measured_at||r.date||r.sleep_date||""),
      y:Number(r[metric]) / scale, label:String(r.measured_at||r.date||r.sleep_date||i),
      valid:r[metric]!==null && r[metric]!==undefined && r[metric]!=="" && Number.isFinite(Number(r[metric]))
    })).filter(p=>p.valid && Number.isFinite(p.y));
    if (!points.length) { area.textContent=rogbidT(this._config,this._hass,"noNumeric"); return; }
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
      hint.textContent = rogbidT(this._config,this._hass,"noStages");
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
      detail.append(document.createTextNode(rogbidT(this._config,this._hass,"invalidStages")));
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
        (stage.type === 1 ? rogbidT(this._config,this._hass,"deep") : stage.type === 2 ? rogbidT(this._config,this._hass,"light") : rogbidT(this._config,this._hass,"other"));
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
    legend.textContent = "■ " + rogbidT(this._config,this._hass,"deep") + " (blue)   ■ " + rogbidT(this._config,this._hass,"light") + " (teal)   ■ " + rogbidT(this._config,this._hass,"other") + " (gray)";
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
    if (value !== null && value !== undefined) config[key] = value;
    else delete config[key];
    this._config = config;
    this._render();
    this.dispatchEvent(new CustomEvent("config-changed", {
      detail: { config }, bubbles: true, composed: true
    }));
  }
  _render() {
    this.replaceChildren();
    const t = key => rogbidT(this._config,this._hass,key);
    const label = document.createElement("label");
    label.textContent = t("watch");
    label.style.display = "block";
    this.append(label);
    const select = document.createElement("select");
    select.style.cssText = "display:block;margin:8px 0;padding:8px;width:95%";
    const auto = document.createElement("option");
    auto.value = "";
    auto.textContent = t("autoWatch");
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
    input.placeholder = t("cardTitle");
    input.value = this._config.title || "";
    input.style.cssText = "display:block;margin:8px 0;padding:8px;width:90%";
    input.addEventListener("change", () => this._update("title", input.value));
    this.append(input);
    const add=(labelText,el)=>{const label=document.createElement("label");label.textContent=labelText;
      label.style.cssText="display:block;margin-top:12px";el.style.cssText="display:block;margin:6px 0;padding:8px;width:95%";
      this.append(label,el);};
    const language=document.createElement("select");
    for (const [code,name] of [["auto",t("haLanguage")],["ru","Русский"],["en","English"],["fr","Français"],["de","Deutsch"],["lb","Lëtzebuergesch"],["it","Italiano"],["pt","Português"],["es","Español"]]) {
      const option=document.createElement("option");option.value=code;option.textContent=name;language.append(option);
    }
    language.value=this._config.language||"auto";
    language.addEventListener("change",()=>this._update("language",language.value==="auto"?null:language.value));
    add(t("language"),language);
    const titleToggle=document.createElement("input");titleToggle.type="checkbox";
    titleToggle.checked=this._config.show_title!==false;
    titleToggle.addEventListener("change",()=>this._update("show_title",titleToggle.checked));
    const titleLabel=document.createElement("label");titleLabel.style.cssText="display:flex;gap:8px;align-items:center;margin-top:12px";
    titleLabel.append(titleToggle,document.createTextNode(t("showTitle")));this.append(titleLabel);
    const sections=document.createElement("div");sections.textContent=t("sections");sections.style.marginTop="12px";this.append(sections);
    for(const [key] of KINDS) {
      const toggle=document.createElement("input");toggle.type="checkbox";
      toggle.checked=!this._config.hidden_sections?.includes(key);
      toggle.addEventListener("change",()=>{
        const hidden=new Set(this._config.hidden_sections||[]);
        if(toggle.checked) hidden.delete(key);else hidden.add(key);
        this._update("hidden_sections",[...hidden]);
      });
      const row=document.createElement("label");row.style.cssText="display:flex;gap:8px;align-items:center;margin:5px 0";
      row.append(toggle,document.createTextNode(KINDS.find(([id])=>id===key)[1].split(" ")[0]+" "+(EXTRA_KINDS.includes(key)?key:t(key))));
      this.append(row);
    }
    const heading=document.createElement("div");heading.textContent=t("order");heading.style.marginTop="12px";this.append(heading);
    const order=orderKinds(this._config.button_order);
    order.forEach((key,i)=>{const row=document.createElement("div");row.style.cssText="display:flex;gap:8px;align-items:center;margin:5px 0";
      const label=document.createElement("span");label.textContent=KINDS.find(([id])=>id===key)[1].split(" ")[0]+" "+t(key);label.style.flex="1";row.append(label);
      for(const [symbol,delta] of [["↑",-1],["↓",1]]){const button=document.createElement("button");button.textContent=symbol;
        button.disabled=i+delta<0||i+delta>=order.length;
        button.addEventListener("click",()=>{const next=[...order];[next[i],next[i+delta]]=[next[i+delta],next[i]];this._update("button_order",next);});
        row.append(button);}this.append(row);});
    const limit=document.createElement("input");limit.type="number";limit.min="1";limit.max="500";
    limit.value=String(this._config.history_limit||200);
    limit.addEventListener("change",()=>this._update("history_limit",Math.max(1,Math.min(500,Number(limit.value)||200))));
    add(t("limit"),limit);
    const height=document.createElement("input");height.type="number";height.min="120";height.max="1200";
    height.value=String(this._config.history_height||420);
    height.addEventListener("change",()=>this._update("history_height",Math.max(120,Math.min(1200,Number(height.value)||420))));
    add(t("height"),height);
    const deletion = document.createElement("input");
    deletion.type = "checkbox";
    deletion.checked = this._config.show_delete_buttons === true;
    deletion.style.cssText = "margin-right:8px";
    deletion.addEventListener("change", () => this._update("show_delete_buttons", deletion.checked));
    const deletionLabel = document.createElement("label");
    deletionLabel.style.cssText = "display:flex;align-items:center;margin-top:12px";
    deletionLabel.append(deletion, document.createTextNode(t("showDelete")));
    this.append(deletionLabel);
    const modeTitle=document.createElement("div");
    modeTitle.textContent=t("display");modeTitle.style.marginTop="12px";this.append(modeTitle);
    for (const [kind,iconLabel] of KINDS) {
      const row=document.createElement("div");
      row.style.cssText="display:flex;gap:8px;align-items:center;margin:6px 0";
      const caption=document.createElement("span");
      caption.textContent=iconLabel.split(" ")[0]+" "+(kind==="kcal"?"kcal":kind==="km"?"km":t(kind));
      caption.style.flex="1";
      const mode=document.createElement("select");
      for(const [value,name] of [["table",t("table")],["graph",t("graph")]]) {
        const option=document.createElement("option");option.value=value;option.textContent=name;mode.append(option);
      }
      mode.value=viewForKind(this._config,kind);
      mode.addEventListener("change",()=>{
        this._update("view_modes",{...(this._config.view_modes||{}),[kind]:mode.value});
      });
      row.append(caption,mode);this.append(row);
    }
  }
}
customElements.define("rogbid-history-card", RogbidHistoryCard);
customElements.define("rogbid-history-card-editor", RogbidHistoryCardEditor);
window.customCards = window.customCards || [];
window.customCards.push({ type: "rogbid-history-card", name: "Rogbid Bridge History", description: "Local journals from Rogbid Bridge" });
