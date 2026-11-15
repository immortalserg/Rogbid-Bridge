# Widget Android per Home Assistant

[English](../android-template-widgets.md) · [Русский](android-template-widgets.ru.md) · [Français](android-template-widgets.fr.md) · [Lëtzebuergesch](android-template-widgets.lb.md) · [Deutsch](android-template-widgets.de.md) · [Italiano](android-template-widgets.it.md)

Questi esempi sono per il **widget Modello dell’app Home Assistant Companion su Android**, non per le schede Lovelace. Usano Jinja2 e la formattazione HTML limitata di Android.

## Configurazione

1. Installa e configura Home Assistant Companion su Android.
2. Aggiungi un widget **Home Assistant → Modello** alla schermata iniziale.
3. Incolla uno dei modelli seguenti e salva.
4. Sostituisci il prefisso dei sensori con quello del **tuo orologio**. Trovi gli ID in Home Assistant → Strumenti per sviluppatori → Stati.

> **Importante:** Il prefisso di esempio appartiene a uno specifico orologio. Anche i suffissi dei sensori cumulativi (ad esempio `_2`) possono variare. Verifica tutti gli ID.

Sono supportati tag semplici come `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>` e `<br>`. Tabelle HTML, SVG, JavaScript e gran parte del CSS non sono supportati. Android potrebbe ignorare `font-stretch` e `letter-spacing`.

## Anteprima dei widget Android

![Android Home Assistant widgets](../../img/vidget.jpeg)

La schermata mostra tutti e quattro i widget Android.

## 1. Passi

Passi attuali e totali degli ultimi 7, 30 e 365 giorni.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set t = states(p ~ 'steps') -%}
{% set w = states(p ~ 'steps_last_7_days_2') -%}
{% set m = states(p ~ 'steps_last_30_days_2') -%}
{% set y = states(p ~ 'steps_last_365_days_2') -%}
{% macro value(v) -%}
{%- if is_number(v) -%}
{{ "{:,}".format(v | int).replace(",", " ") }}
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#1976D2"><b><big>👣 Passi</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 2. Calorie

Calorie in kcal e totali per gli stessi periodi.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set t = states(p ~ 'calories') -%}
{% set w = states(p ~ 'calories_last_7_days_2') -%}
{% set m = states(p ~ 'calories_last_30_days_2') -%}
{% set y = states(p ~ 'calories_last_365_days_2') -%}
{% macro value(v) -%}
{%- if is_number(v) -%}
{{ "{:,}".format(v | int).replace(",", " ") }} kcal
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#EF6C00"><b><big>🔥 Calorie</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 3. Distanza

Distanza in chilometri e totali per gli stessi periodi.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set t = states(p ~ 'distance') -%}
{% set w = states(p ~ 'distance_last_7_days_2') -%}
{% set m = states(p ~ 'distance_last_30_days_2') -%}
{% set y = states(p ~ 'distance_last_365_days_2') -%}
{% macro value(v) -%}
{%- if is_number(v) -%}
{{ ("%.1f" | format(v | float)).replace(".", ",") }} km
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#2E7D32"><b><big>📍 Distanza</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 giorni</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## Scheda attività Home Assistant

![Home Assistant activity dashboard](../../img/0014.png)

Questa è una scheda della dashboard Home Assistant, non un widget Android.

## 4. Ultimo sonno

Durata, orari di inizio e fine, sonno profondo e leggero, risvegli e grafico compatto di 32 blocchi. `sleep_json` deve contenere `sleep_date`, `start_time`, `end_time` e `stages` con `time` e `type`. Codici: **1 = profondo, 2 = leggero, 3 = sveglio/fine**. Nell’integrazione attuale `rem_sleep` indica **il tempo di veglia, non il sonno REM**.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set sleep = state_attr(p ~ 'last_sleep_details', 'sleep_json') -%}
{% set sleep = sleep if sleep is mapping else {} -%}
{% set stages = sleep.get('stages', []) -%}
{% set total = states(p ~ 'sleep_duration') -%}
{% set deep = states(p ~ 'deep_sleep') -%}
{% set light = states(p ~ 'light_sleep') -%}
{% set awake = states(p ~ 'rem_sleep') -%}
{% set ns = namespace(points=[], last=-1, offset=0) -%}
{% for stage in stages -%}
  {% set parts = stage.time.split(':') -%}
  {% if parts | length == 2 -%}
    {% set minute = (parts[0] | int) * 60 + (parts[1] | int) -%}
    {% if ns.last >= 0 and minute < ns.last -%}
      {% set ns.offset = ns.offset + 1440 -%}
    {% endif -%}
    {% set ns.last = minute -%}
    {% set ns.points = ns.points + [(minute + ns.offset, stage.type | int)] -%}
  {% endif -%}
{% endfor -%}
{% macro duration(v) -%}
{%- if is_number(v) -%}
{{ (v | int) // 60 }} h {{ (v | int) % 60 }} min
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#673AB7"><b><big>🌙 Ultimo sonno</big></b></font><br>
<small><font color="#607D8B">{{ sleep.get('sleep_date', '—') }}</font></small><br>
<big><big><font color="#512DA8"><b>{{ duration(total) }}</b></font></big></big><br>
<small><font color="#607D8B">Inizio — fine</font></small><br>
<big><font color="#263238"><b>{{ sleep.get('start_time') or '—' }} – {{ sleep.get('end_time') or '—' }}</b></font></big><br>
<small><font color="#607D8B">Sonno profondo</font></small><br>
<big><font color="#1565C0"><b>{{ duration(deep) }}</b></font></big><br>
<small><font color="#607D8B">Sonno leggero</font></small><br>
<big><font color="#00897B"><b>{{ duration(light) }}</b></font></big><br>
<small><font color="#607D8B">Risvegli</font></small><br>
<big><font color="#E65100"><b>{{ (awake | int ~ ' min') if is_number(awake) else '—' }}</b></font></big><br>
<small><font color="#607D8B">Fasi del sonno</font></small><br>
{% if ns.points | length > 1 -%}
  {% set first = ns.points[0][0] -%}
  {% set last = ns.points[-1][0] -%}
  <small><font style="font-stretch: condensed; letter-spacing: -2px">
  {%- for i in range(32) -%}
    {%- set current = first + (last - first) * i / 32 -%}
    {%- set cell = namespace(type=2) -%}
    {%- for point in ns.points -%}
      {%- if point[0] <= current -%}
        {%- set cell.type = point[1] -%}
      {%- endif -%}
    {%- endfor -%}
    {%- if cell.type == 1 -%}
      <font color="#1565C0">█</font>
    {%- elif cell.type == 2 -%}
      <font color="#00897B">█</font>
    {%- else -%}
      <font color="#EF6C00">█</font>
    {%- endif -%}
  {%- endfor -%}
  </font></small>
{% else -%}
  <small><font color="#607D8B">Nessun dato sulle fasi del sonno</font></small>
{% endif -%}
</center>
```

## Scheda sonno Home Assistant

![Home Assistant sleep dashboard](../../img/0013.png)

Il grafico dettagliato della dashboard è diverso dalla barra compatta del widget Android.

### Regolare il grafico compatto

- Il grafico usa **32** blocchi larghi `█` senza spazi.
- Per ridurne la larghezza, sostituisci `range(32)` e `i / 32` con `range(24)` e `i / 24`.
- Blu = sonno profondo; turchese = leggero; arancione = veglia.
- Il grafico è approssimativo, non minuto per minuto.
- Le durate dei sensori e gli orari delle fasi possono differire.
- Android potrebbe ignorare la compressione CSS; ridurre i blocchi è più affidabile.

## Risoluzione dei problemi

- **Valori sconosciuti o trattini:** controlla ID, sincronizzazione e suffissi.
- **Grafico assente:** controlla `sleep_json` in `last_sleep_details`; servono almeno due fasi.
- **Grafico su più righe:** riduci i blocchi o allarga il widget.
- **Formattazione diversa:** il renderer HTML Android è limitato rispetto a Lovelace.
- **Privacy:** oscura ID dell’orologio, dati del sonno e credenziali MQTT prima di pubblicare segnalazioni.
