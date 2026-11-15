/* Rogbid Bridge history card, no external dependencies. */
const KINDS = [["heart","❤️ Pulse"],["oxygen","🩸 SpO₂"],["steps","👣 Steps"],["kcal","🔥 kcal"],["km","📍 km"],["sleep","🌙 Sleep"]];
const EXTRA_KINDS = ["kcal","km"];
const historyKind = kind => EXTRA_KINDS.includes(kind) ? "steps" : kind;
const viewForKind = (config,kind) => config?.view_modes?.[kind] || config?.view_mode || "table";
const TOTAL_KINDS = ["steps","kcal","km"];
const showTotals = (config,kind) => config?.show_totals?.[kind] === true;
const showToday = (config,kind) => config?.show_today?.[kind] !== false;
const SLEEP_COLORS={deep:"#2d5fd2",light:"#32beba",awake:"#ef9c51"};
const sleepColor=(config,kind)=>/^#[0-9a-fA-F]{6}$/.test(config?.sleep_colors?.[kind]||"")?config.sleep_colors[kind]:SLEEP_COLORS[kind];
const monthlyOptions = (config,kind) => config?.monthly_charts?.[kind] || {};
const showMonthly = (config,kind) => TOTAL_KINDS.includes(kind) && monthlyOptions(config,kind).enabled === true;
const dailyDate = raw => {
  const match = String(raw || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return null;
  const date = new Date(Number(match[1]),Number(match[2])-1,Number(match[3]));
  return date.getFullYear()===Number(match[1]) && date.getMonth()===Number(match[2])-1 &&
    date.getDate()===Number(match[3]) ? date : null;
};
const totalValue = (record,kind) => {
  const key = kind === "kcal" ? "kcal" : kind === "km" ? "diatance" : "steps";
  const raw = record[key];
  const value = raw == null || raw === "" ? NaN : Number(raw);
  return Number.isFinite(value) && value >= 0 ? value / (kind === "steps" ? 1 : 1000) : null;
};
const orderKinds = v => {
  const requested = Array.isArray(v) ? v : [];
  return [...new Set([...requested.filter(k=>KINDS.some(([id])=>id===k)),...KINDS.map(([id])=>id)])];
};


const ROGBID_TRANSLATIONS = {"en":{"heart":"Pulse","oxygen":"Oxygen","steps":"Steps","sleep":"Sleep","date":"Date","wakeDate":"Wake date","duration":"Duration","deep":"Deep","light":"Light","score":"Score","delete":"Delete","loading":"Loading…","empty":"No records yet","noWatch":"No watches configured","showTitle":"Show title","sections":"Visible sections","order":"Button order","limit":"History records (1–500)","height":"History height (px)","display":"History display","table":"Table","graph":"Graph","showDelete":"Show delete buttons","language":"Language","haLanguage":"Home Assistant language","autoWatch":"Automatic watch","cardTitle":"Card title","noNumeric":"No numeric data","noStages":"No sleep stages available","invalidStages":"No valid stage timeline","other":"Other / end","refresh":"Refresh history","confirmDelete":"Delete record from Home Assistant? It will remain on the watch.","deleteError":"Could not delete record","watch":"Watch"},"ru":{"heart":"Пульс","oxygen":"Кислород","steps":"Шаги","sleep":"Сон","date":"Дата","wakeDate":"Дата пробуждения","duration":"Продолжительность","deep":"Глубокий","light":"Лёгкий","score":"Оценка","delete":"Удалить","loading":"Загрузка…","empty":"Записей пока нет","noWatch":"Часы не настроены","showTitle":"Показывать заголовок","sections":"Отображаемые разделы","order":"Порядок кнопок","limit":"Количество записей (1–500)","height":"Высота истории (пикс.)","display":"Отображение истории","table":"Таблица","graph":"График","showDelete":"Показывать кнопки удаления","language":"Язык","haLanguage":"Язык Home Assistant","autoWatch":"Автоматический выбор часов","cardTitle":"Заголовок карточки","noNumeric":"Нет числовых данных","noStages":"Нет данных о фазах сна","invalidStages":"Некорректная шкала фаз сна","other":"Другое / конец","refresh":"Обновить историю","confirmDelete":"Удалить запись из Home Assistant? На часах она останется.","deleteError":"Не удалось удалить запись","watch":"Часы"},"fr":{"heart":"Pouls","oxygen":"Oxygène","steps":"Pas","sleep":"Sommeil","date":"Date","wakeDate":"Date de réveil","duration":"Durée","deep":"Profond","light":"Léger","score":"Score","delete":"Supprimer","loading":"Chargement…","empty":"Aucune donnée","noWatch":"Aucune montre configurée","showTitle":"Afficher le titre","sections":"Sections visibles","order":"Ordre des boutons","limit":"Enregistrements (1–500)","height":"Hauteur (px)","display":"Affichage de l'historique","table":"Tableau","graph":"Graphique","showDelete":"Afficher les boutons de suppression","language":"Langue","haLanguage":"Langue de Home Assistant","autoWatch":"Montre automatique","cardTitle":"Titre de la carte","noNumeric":"Aucune donnée numérique","noStages":"Aucune phase de sommeil","invalidStages":"Chronologie invalide","other":"Autre / fin","refresh":"Actualiser","confirmDelete":"Supprimer de Home Assistant ? La montre conserve cette donnée.","deleteError":"Suppression impossible","watch":"Montre"},"de":{"heart":"Puls","oxygen":"Sauerstoff","steps":"Schritte","sleep":"Schlaf","date":"Datum","wakeDate":"Aufwachdatum","duration":"Dauer","deep":"Tief","light":"Leicht","score":"Bewertung","delete":"Löschen","loading":"Laden…","empty":"Keine Einträge","noWatch":"Keine Uhr eingerichtet","showTitle":"Titel anzeigen","sections":"Sichtbare Bereiche","order":"Schaltflächenreihenfolge","limit":"Einträge (1–500)","height":"Höhe (px)","display":"Verlauf anzeigen","table":"Tabelle","graph":"Diagramm","showDelete":"Löschtasten anzeigen","language":"Sprache","haLanguage":"Home Assistant-Sprache","autoWatch":"Uhr automatisch","cardTitle":"Kartentitel","noNumeric":"Keine Zahlenwerte","noStages":"Keine Schlafphasen","invalidStages":"Ungültiger Verlauf","other":"Andere / Ende","refresh":"Aktualisieren","confirmDelete":"Eintrag aus Home Assistant löschen? Auf der Uhr bleibt er erhalten.","deleteError":"Löschen fehlgeschlagen","watch":"Uhr"},"lb":{"heart":"Puls","oxygen":"Sauerstoff","steps":"Schrëtt","sleep":"Schlof","date":"Datum","wakeDate":"Erwächen","duration":"Dauer","deep":"Déif","light":"Liicht","score":"Bewäertung","delete":"Läschen","loading":"Lueden…","empty":"Keng Donnéeën","noWatch":"Keng Auer ageriicht","showTitle":"Titel weisen","sections":"Siichtbar Beräicher","order":"Reiefolleg","limit":"Entréeën (1–500)","height":"Héicht (px)","display":"Geschicht uweisen","table":"Tabell","graph":"Grafik","showDelete":"Läschknäppercher weisen","language":"Sprooch","haLanguage":"Home Assistant-Sprooch","autoWatch":"Automatesch Auer","cardTitle":"Kaartentitel","noNumeric":"Keng numeresch Donnéeën","noStages":"Keng Schlofphasen","invalidStages":"Ongëlteg Zäitlinn","other":"Aner / Enn","refresh":"Aktualiséieren","confirmDelete":"Entrée aus Home Assistant läschen? Op der Auer bleift se.","deleteError":"Läschen net méiglech","watch":"Auer"},"it":{"heart":"Battito","oxygen":"Ossigeno","steps":"Passi","sleep":"Sonno","date":"Data","wakeDate":"Risveglio","duration":"Durata","deep":"Profondo","light":"Leggero","score":"Punteggio","delete":"Elimina","loading":"Caricamento…","empty":"Nessun dato","noWatch":"Nessun orologio configurato","showTitle":"Mostra titolo","sections":"Sezioni visibili","order":"Ordine pulsanti","limit":"Record (1–500)","height":"Altezza (px)","display":"Visualizzazione","table":"Tabella","graph":"Grafico","showDelete":"Mostra pulsanti elimina","language":"Lingua","haLanguage":"Lingua Home Assistant","autoWatch":"Orologio automatico","cardTitle":"Titolo scheda","noNumeric":"Nessun dato numerico","noStages":"Fasi del sonno assenti","invalidStages":"Sequenza non valida","other":"Altro / fine","refresh":"Aggiorna","confirmDelete":"Eliminare da Home Assistant? Resterà sull'orologio.","deleteError":"Eliminazione non riuscita","watch":"Orologio"},"pt":{"heart":"Pulso","oxygen":"Oxigénio","steps":"Passos","sleep":"Sono","date":"Data","wakeDate":"Despertar","duration":"Duração","deep":"Profundo","light":"Leve","score":"Pontuação","delete":"Eliminar","loading":"A carregar…","empty":"Sem registos","noWatch":"Sem relógio configurado","showTitle":"Mostrar título","sections":"Secções visíveis","order":"Ordem dos botões","limit":"Registos (1–500)","height":"Altura (px)","display":"Visualização","table":"Tabela","graph":"Gráfico","showDelete":"Mostrar botões de eliminação","language":"Idioma","haLanguage":"Idioma do Home Assistant","autoWatch":"Relógio automático","cardTitle":"Título do cartão","noNumeric":"Sem dados numéricos","noStages":"Sem fases de sono","invalidStages":"Cronologia inválida","other":"Outro / fim","refresh":"Atualizar","confirmDelete":"Eliminar do Home Assistant? Permanecerá no relógio.","deleteError":"Erro ao eliminar","watch":"Relógio"},"es":{"heart":"Pulso","oxygen":"Oxígeno","steps":"Pasos","sleep":"Sueño","date":"Fecha","wakeDate":"Despertar","duration":"Duración","deep":"Profundo","light":"Ligero","score":"Puntuación","delete":"Eliminar","loading":"Cargando…","empty":"Sin registros","noWatch":"Sin relojes configurados","showTitle":"Mostrar título","sections":"Secciones visibles","order":"Orden de botones","limit":"Registros (1–500)","height":"Altura (px)","display":"Visualización","table":"Tabla","graph":"Gráfico","showDelete":"Mostrar botones de eliminación","language":"Idioma","haLanguage":"Idioma de Home Assistant","autoWatch":"Reloj automático","cardTitle":"Título de tarjeta","noNumeric":"Sin datos numéricos","noStages":"Sin fases de sueño","invalidStages":"Cronología no válida","other":"Otro / fin","refresh":"Actualizar","confirmDelete":"¿Eliminar de Home Assistant? Permanecerá en el reloj.","deleteError":"Error al eliminar","watch":"Reloj"}};
for(const [lang,translations] of Object.entries(ROGBID_TRANSLATIONS)) {
  Object.assign(translations, {buttonLabels:({ru:"Подписи кнопок",fr:"Libellés des boutons",de:"Tastenbeschriftungen"}[lang]||"Button labels"),todayTotals:({ru:"Показывать сегодня",fr:"Afficher aujourd’hui",de:"Heute anzeigen"}[lang]||"Show today"),details:({ru:"Подробности",fr:"Détails",de:"Details"}[lang]||"Details"),awake:({ru:"Пробуждение",fr:"Éveil",de:"Wach"}[lang]||"Awake"),sleepStages:({ru:"Фазы сна",fr:"Phases de sommeil",de:"Schlafphasen"}[lang]||"Sleep stages"),sleepColors:({ru:"Цвета фаз сна",fr:"Couleurs des phases de sommeil",de:"Schlafphasenfarben"}[lang]||"Sleep stage colors"),sleepSquare:({ru:"Цветной квадратик",fr:"Carré coloré",de:"Farbiges Quadrat"}[lang]||"Color square"),sleepColoredText:({ru:"Цветное значение",fr:"Valeur colorée",de:"Farbiger Wert"}[lang]||"Colored value"),sleepSummaryStyle:({ru:"Обозначение фаз сна",fr:"Indicateurs des phases",de:"Schlafphasenanzeige"}[lang]||"Sleep phase indicators")});
}
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
    const changed = previousKind !== this._kind || previousDevice !== this._deviceId;
    const needsTotals = TOTAL_KINDS.includes(this._kind) &&
      (showTotals(config,this._kind) || showToday(config,this._kind) || showMonthly(config,this._kind));
    if (changed) {
      this._requestId = (this._requestId || 0) + 1;
      this._rows = [];
      this._selectedSleepId = null;
      this._totalRows = [];
      this._loading = false;
      this._error = "";
    }
    this._render();
    // Changing chart settings must fetch the monthly data immediately.
    if (this._hass && this.isConnected && this._deviceId &&
        (changed || (needsTotals && !this._totalRows?.length && !this._loading))) {
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
      if (request === this._requestId) {
        this._rows = result.records || [];
        if (kind === "sleep" && !this._rows.some(r=>String(r._record_id||r.id||r.sleep_date)===this._selectedSleepId)) this._selectedSleepId=null;
      }
      if (TOTAL_KINDS.includes(kind) && (showTotals(this._config,kind) || showToday(this._config,kind) || showMonthly(this._config,kind))) {
        // Fetch independently of the chart/table row limit so a year can be summed.
        const totalsResult = await this._hass.callWS({
          type:"rogbid_bridge/history", device_id:this._deviceId, kind:"steps", limit:500
        });
        if (request === this._requestId) this._totalRows = totalsResult.records || [];
      } else if (request === this._requestId) this._totalRows = [];
    } catch (error) {
      if (request === this._requestId) {
        this._rows = [];
        this._totalRows = [];
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
          .totals { display:grid;grid-template-columns:repeat(auto-fit,minmax(70px,1fr));gap:8px;margin:0 0 12px; }
          .total { padding:8px 5px;border:1px solid var(--divider-color);border-radius:8px;text-align:center;min-width:0; }
          .total strong { display:block;font-size:17px;overflow-wrap:anywhere; }
          .total small { color:var(--secondary-text-color);font-size:11px; }
          .sleep-row { cursor:pointer; }
          .sleep-row:focus { outline:2px solid var(--primary-color); }
          .sleep-detail { padding:12px 4px; border-bottom:1px solid var(--divider-color); margin-bottom:12px; }
          .sleep-summary { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:12px 0; }
          .sleep-summary > div { padding:8px 4px;text-align:center;border:1px solid var(--divider-color);border-radius:8px; }
          .sleep-summary strong { display:block; }
          .sleep-summary small { display:block;margin-bottom:5px; }
          .sleep-color-square { display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:5px;vertical-align:middle; }
          .eye-button { padding:5px 9px; }
          .stage-track { align-items:stretch; }
          .stage-track { display:flex; height:32px; border-radius:6px; overflow:hidden; margin:12px 0 6px; background:var(--divider-color); }
          .stage-segment { min-width:1px; }
          .stage-labels { display:flex; justify-content:space-between; font-size:12px; color:var(--secondary-text-color); }
          .legend { font-size:12px; margin:8px 0; }
        </style>
        <ha-card><header><h2></h2></header>
          <nav></nav><div class="scroll"></div></ha-card>`;

    }
    const t = key => rogbidT(this._config,this._hass,key);
    const heading = this._root.querySelector("h2");
    heading.textContent = this._config.title || "Rogbid Bridge";
    heading.hidden = this._config.show_title === false;

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
      header.append(select);
    }
    const nav = this._root.querySelector("nav");
    nav.replaceChildren();
    for (const key of visibleKinds(this._config)) {
      const label = KINDS.find(([id])=>id===key)[1].split(" ")[0] + " " + (EXTRA_KINDS.includes(key) ? key : t(key));
      const button = document.createElement("button");
      button.textContent = this._config.show_button_labels === false ? label.split(" ")[0] : label;
      button.title = label;
      button.setAttribute("aria-label", label);
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
    const refresh=document.createElement("button");
    refresh.textContent="↻";refresh.title=t("refresh");refresh.setAttribute("aria-label",t("refresh"));
    refresh.addEventListener("click",()=>this._load());nav.append(refresh);
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
    if (this._kind === "sleep") { this._renderSleepHistory(area); return; }
    if (viewForKind(this._config,this._kind) === "graph") {
      if (TOTAL_KINDS.includes(this._kind) && (showTotals(this._config,this._kind) || showToday(this._config,this._kind))) this._renderTotals(area);
      if (showMonthly(this._config,this._kind) && monthlyOptions(this._config,this._kind).position !== "after") this._renderMonthly(area);
      this._renderGraph(area);
      if (showMonthly(this._config,this._kind) && monthlyOptions(this._config,this._kind).position === "after") this._renderMonthly(area);
      return;
    }
    if (TOTAL_KINDS.includes(this._kind) && (showTotals(this._config,this._kind) || showToday(this._config,this._kind))) this._renderTotals(area);
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


  _renderTotals(area) {
    const lang = rogbidLang(this._config,this._hass);
    const labels = lang === "ru" ? ["Сегодня","7 дней","30 дней","365 дней"] :
      lang === "fr" ? ["Aujourd'hui","7 jours","30 jours","365 jours"] :
      lang === "de" ? ["Heute","7 Tage","30 Tage","365 Tage"] :
      ["Today","7 days","30 days","365 days"];
    const today = new Date();
    today.setHours(0,0,0,0);
    const days = [1,7,30,365];
    const sums = [0,0,0,0], counts = [0,0,0,0];
    const seen = new Set();
    for (const record of this._totalRows || this._rows || []) {
      const date = dailyDate(record.date);
      if (!date || seen.has(record.date)) continue;
      seen.add(record.date);
      const age = Math.round((Date.UTC(today.getFullYear(),today.getMonth(),today.getDate()) -
        Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())) / 86400000);
      if (age < 0) continue;
      const value = totalValue(record,this._kind);
      if (value === null) continue;
      days.forEach((period,i)=>{if(age < period){sums[i]+=value;counts[i]++;}});
    }
    const container=document.createElement("div");container.className="totals";
    const formatter=new Intl.NumberFormat(lang,{maximumFractionDigits:this._kind==="km"?1:0});
    days.forEach((period,i)=>{
      if (i === 0 ? !showToday(this._config,this._kind) : !showTotals(this._config,this._kind)) return;
      const item=document.createElement("div");item.className="total";
      const value=document.createElement("strong");
      value.textContent=counts[i] ? formatter.format(this._kind==="km"?sums[i]:Math.round(sums[i])) : "—";
      const label=document.createElement("small");
      label.textContent=labels[i];
      item.append(value,label);container.append(item);
    });
    area.append(container);
  }

  _renderMonthly(area) {
    const options = monthlyOptions(this._config,this._kind);
    const count = Number(options.months) === 12 ? 12 : 6;
    const lang = rogbidLang(this._config,this._hass);
    const now = new Date();
    const months = Array.from({length:count},(_,i)=>{
      const offset=count-1-i;
      const year=now.getFullYear(),month=now.getMonth()-offset;
      const start=new Date(year,month,1);
      return {key:start.getFullYear()+"-"+String(start.getMonth()+1).padStart(2,"0"),
        label:new Intl.DateTimeFormat(lang,{month:"short",year:"2-digit"}).format(start),
        sum:0,days:0};
    });
    const byMonth = new Map(months.map(m=>[m.key,m]));
    const seen=new Set();
    for (const record of this._totalRows || []) {
      const date=dailyDate(record.date);
      if (!date || date > now || seen.has(record.date)) continue;
      seen.add(record.date);
      const key=record.date.slice(0,7), month=byMonth.get(key);
      if (!month) continue;
      const value=totalValue(record,this._kind);
      if (value===null) continue;
      month.sum+=value;month.days++;
    }
    const wrapper=document.createElement("section");
    wrapper.style.cssText="margin:12px 0 18px";
    const title=document.createElement("strong");
    title.textContent=(lang==="ru"?"По месяцам":lang==="fr"?"Par mois":lang==="de"?"Pro Monat":"Monthly totals")+
      " · "+(this._kind==="steps"?rogbidT(this._config,this._hass,"steps"):this._kind);
    wrapper.append(title);
    const ns="http://www.w3.org/2000/svg",svg=document.createElementNS(ns,"svg");
    svg.setAttribute("viewBox","0 0 600 260");svg.classList.add("history-chart");
    svg.setAttribute("role","img");svg.setAttribute("aria-label",title.textContent);
    const make=(tag,attrs,parent=svg)=>{
      const el=document.createElementNS(ns,tag);
      Object.entries(attrs).forEach(([k,v])=>el.setAttribute(k,String(v)));
      parent.append(el);return el;
    };
    const max=Math.max(1,...months.map(m=>m.sum));
    make("line",{x1:48,y1:218,x2:590,y2:218,stroke:"currentColor"});
    const formatter=new Intl.NumberFormat(lang,{maximumFractionDigits:this._kind==="km"?1:0});
    months.forEach((m,i)=>{
      const slot=540/count,x=50+i*slot+slot*0.15,width=slot*0.7;
      const height=178*m.sum/max;
      const bar=make("rect",{x,y:218-height,width,height,rx:2,fill:"var(--primary-color)"});
      const tooltip=make("title",{},bar);
      tooltip.textContent=m.label+": "+(m.days?formatter.format(m.sum):"—")+" ("+m.days+" days)";
      if (options.show_values === true && m.days) {
        const valueLabel=make("text",{x:x+width/2,y:Math.max(18,213-height),
          "text-anchor":"middle",fill:"currentColor","font-size":count===12?10:12});
        valueLabel.textContent=formatter.format(m.sum);
      }
      const label=make("text",{x:x+width/2,y:239,"text-anchor":"middle",fill:"currentColor","font-size":count===12?10:12});
      label.textContent=m.label;
    });
    wrapper.append(svg);
    area.append(wrapper);
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

  _renderSleepHistory(area) {
    const t=key=>rogbidT(this._config,this._hass,key);
    const rows=this._rows;
    if (!rows.length) return;
    const selected=rows.find(r=>String(r._record_id||r.id||r.sleep_date)===this._selectedSleepId) || rows[0];
    this._selectedSleepId=String(selected._record_id||selected.id||selected.sleep_date);
    this._showSleep(selected,area);
    const table=document.createElement("table");
    const head=document.createElement("tr");
    for (const title of [t("wakeDate"),t("duration"),""]) {
      const th=document.createElement("th");th.textContent=title;head.append(th);
    }
    const thead=document.createElement("thead");thead.append(head);table.append(thead);
    const body=document.createElement("tbody");
    for(const record of rows) {
      const row=document.createElement("tr");
      for(const value of [record.sleep_date||"—",formatRogbidMinutes(record.duration_minutes,rogbidLang(this._config,this._hass))]) {
        const td=document.createElement("td");td.textContent=value;row.append(td);
      }
      const cell=document.createElement("td");
      const eye=document.createElement("button");eye.className="eye-button";eye.textContent="👁";
      eye.title=t("details") === "details" ? "Details" : t("details");
      eye.setAttribute("aria-label",eye.title);
      eye.addEventListener("click",()=>{
        this._selectedSleepId=String(record._record_id||record.id||record.sleep_date);
        this._render();
      });
      cell.append(eye);row.append(cell);body.append(row);
    }
    table.append(body);area.append(table);
  }

  _showSleep(record, area) {
    const detail = document.createElement("div");
    detail.className = "sleep-detail";
    const title = document.createElement("strong");
    title.textContent = (record.sleep_date || "") + "  " +
      (record.start_time || "") + "–" + (record.end_time || "");
    detail.append(title);
    const summary=document.createElement("div");summary.className="sleep-summary";
    for(const [kind,name,value] of [["deep",rogbidT(this._config,this._hass,"deep"),record.deep_minutes],["light",rogbidT(this._config,this._hass,"light"),record.light_minutes],["awake",rogbidT(this._config,this._hass,"awake"),record.awake_minutes]]) {
      const cell=document.createElement("div");const label=document.createElement("small");label.textContent=name;
      const amount=document.createElement("strong");amount.textContent=formatRogbidMinutes(value,rogbidLang(this._config,this._hass));
      const color=sleepColor(this._config,kind);
      if(this._config.sleep_summary_style==="text") amount.style.color=color;
      else { const square=document.createElement("span");square.className="sleep-color-square";square.style.backgroundColor=color;label.prepend(square); }
      cell.append(label,amount);summary.append(cell);
    }
    detail.append(summary);
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
    const start=timeline[0]?.time,end=timeline[timeline.length-1]?.time;
    if(timeline.length<2||end<=start){
      const hint=document.createElement("div");hint.className="hint";
      hint.textContent=rogbidT(this._config,this._hass,"invalidStages");detail.append(hint);
      const old=area.querySelector(".sleep-detail");if(old)old.remove();area.prepend(detail);return;
    }
    const track=document.createElement("div");track.className="stage-track";
    const color=type=>sleepColor(this._config,type===1?"deep":type===2?"light":"awake");
    for(let i=0;i<timeline.length-1;i++){
      const stage=timeline[i],next=timeline[i+1];
      if(next.time<=stage.time)continue;
      const segment=document.createElement("div");segment.className="stage-segment";
      segment.style.flex=String(next.time-stage.time);
      segment.style.backgroundColor=color(stage.type);
      segment.title=stage.label+"–"+next.label;
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
    const buttonLabels=document.createElement("input");buttonLabels.type="checkbox";buttonLabels.checked=this._config.show_button_labels!==false;
    buttonLabels.addEventListener("change",()=>this._update("show_button_labels",buttonLabels.checked));
    const buttonLabelsRow=document.createElement("label");buttonLabelsRow.style.cssText="display:flex;gap:8px;align-items:center;margin-top:12px";
    buttonLabelsRow.append(buttonLabels,document.createTextNode(t("buttonLabels")));this.append(buttonLabelsRow);
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
    const totalsTitle=document.createElement("div");
    totalsTitle.textContent=t("display")+" — ∑";
    totalsTitle.style.marginTop="12px";this.append(totalsTitle);
    for (const kind of TOTAL_KINDS) {
      const toggle=document.createElement("input");toggle.type="checkbox";
      toggle.checked=showTotals(this._config,kind);
      toggle.addEventListener("change",()=>{
        this._update("show_totals",{...(this._config.show_totals||{}),[kind]:toggle.checked});
      });
      const label=document.createElement("label");
      label.style.cssText="display:flex;gap:8px;align-items:center;margin:6px 0";
      label.append(toggle,document.createTextNode("∑ "+(kind==="steps"?t("steps"):kind)+" (7 / 30 / 365)"));
      this.append(label);
    }
    const todayTitle=document.createElement("div");todayTitle.textContent=t("todayTotals");todayTitle.style.marginTop="12px";this.append(todayTitle);
    for(const kind of TOTAL_KINDS) {
      const toggle=document.createElement("input");toggle.type="checkbox";toggle.checked=showToday(this._config,kind);
      toggle.addEventListener("change",()=>this._update("show_today",{...(this._config.show_today||{}),[kind]:toggle.checked}));
      const row=document.createElement("label");row.style.cssText="display:flex;gap:8px;align-items:center;margin:6px 0";
      row.append(toggle,document.createTextNode(kind==="steps"?t("steps"):kind));this.append(row);
    }
    const sleepSettingsTitle=document.createElement("div");sleepSettingsTitle.textContent=t("sleepColors");sleepSettingsTitle.style.marginTop="12px";this.append(sleepSettingsTitle);
    for(const kind of ["deep","light","awake"]){
      const row=document.createElement("label");row.style.cssText="display:flex;align-items:center;gap:10px;margin:7px 0";
      const color=document.createElement("input");color.type="color";color.value=sleepColor(this._config,kind);
      color.addEventListener("change",()=>this._update("sleep_colors",{...(this._config.sleep_colors||{}),[kind]:color.value}));
      row.append(color,document.createTextNode(t(kind)));this.append(row);
    }
    const summaryStyle=document.createElement("select");
    for(const [value,label] of [["square",t("sleepSquare")],["text",t("sleepColoredText")]]){
      const option=document.createElement("option");option.value=value;option.textContent=label;summaryStyle.append(option);
    }
    summaryStyle.value=this._config.sleep_summary_style==="text"?"text":"square";
    summaryStyle.addEventListener("change",()=>this._update("sleep_summary_style",summaryStyle.value));
    add(t("sleepSummaryStyle"),summaryStyle);
    const monthlyTitle=document.createElement("div");
    monthlyTitle.textContent=(rogbidLang(this._config,this._hass)==="ru"?"Месячные графики":"Monthly charts");
    monthlyTitle.style.marginTop="12px";this.append(monthlyTitle);
    for(const kind of TOTAL_KINDS) {
      const options=monthlyOptions(this._config,kind);
      const row=document.createElement("div");
      row.style.cssText="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:8px 0";
      const toggle=document.createElement("input");toggle.type="checkbox";toggle.checked=options.enabled===true;
      const name=document.createElement("span");name.textContent=kind==="steps"?t("steps"):kind;
      const months=document.createElement("select");
      for(const value of [6,12]) {const option=document.createElement("option");option.value=String(value);option.textContent=String(value)+" "+(rogbidLang(this._config,this._hass)==="ru"?"месяцев":"months");months.append(option);}
      months.value=String(options.months===12?12:6);
      const position=document.createElement("select");
      for(const [value,label] of [["before",rogbidLang(this._config,this._hass)==="ru"?"Перед графиком":"Before chart"],["after",rogbidLang(this._config,this._hass)==="ru"?"После графика":"After chart"]]) {
        const option=document.createElement("option");option.value=value;option.textContent=label;position.append(option);
      }
      position.value=options.position==="after"?"after":"before";
      const values=document.createElement("input");values.type="checkbox";
      values.checked=options.show_values===true;
      const valuesLabel=document.createElement("label");
      valuesLabel.style.cssText="display:flex;gap:4px;align-items:center";
      valuesLabel.append(values,document.createTextNode(
        rogbidLang(this._config,this._hass)==="ru"?"Показывать значения":"Show values"));
      const save=()=>this._update("monthly_charts",{...(this._config.monthly_charts||{}),
        [kind]:{enabled:toggle.checked,months:Number(months.value),position:position.value,show_values:values.checked}});
      toggle.addEventListener("change",save);months.addEventListener("change",save);position.addEventListener("change",save);values.addEventListener("change",save);
      row.append(toggle,name,months,position,valuesLabel);this.append(row);
    }
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
