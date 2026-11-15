# Android-Widgets für Home Assistant

[English](../android-template-widgets.md) · [Русский](android-template-widgets.ru.md) · [Français](android-template-widgets.fr.md) · [Lëtzebuergesch](android-template-widgets.lb.md) · [Deutsch](android-template-widgets.de.md) · [Italiano](android-template-widgets.it.md)

Diese Beispiele gelten für das **Template-Widget der Home Assistant Companion App unter Android**, nicht für Lovelace-Karten. Sie verwenden Jinja2 und die eingeschränkte HTML-Darstellung von Android.

## Einrichtung

1. Installieren und konfigurieren Sie die Home Assistant Companion App unter Android.
2. Fügen Sie ein **Home Assistant → Template**-Widget zum Startbildschirm hinzu.
3. Fügen Sie eine der folgenden Vorlagen ein und speichern Sie.
4. Ersetzen Sie das Sensorpräfix durch das Präfix **Ihrer Uhr**. Die Entitäts-IDs finden Sie unter Home Assistant → Entwicklerwerkzeuge → Zustände.

> **Wichtig:** Das Sensorpräfix gehört zu einer bestimmten Uhr. Auch Suffixe der Summensensoren (z. B. `_2`) können abweichen. Prüfen Sie alle Entitäts-IDs.

Einfache Tags wie `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>` und `<br>` werden unterstützt. HTML-Tabellen, SVG, JavaScript und die meisten CSS-Eigenschaften nicht. Android kann `font-stretch` und `letter-spacing` ignorieren.

## Android-Widgets auf dem Startbildschirm

![Android Home Assistant widgets](../../img/vidget.jpeg)

Der Screenshot zeigt alle vier Android-Widgets zusammen.

## 1. Schritte

Aktuelle Schritte und Summen der letzten 7, 30 und 365 Tage.

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
<font color="#1976D2"><b><big>👣 Schritte</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 2. Kalorien

Kalorien in kcal und Summen für dieselben Zeiträume.

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
<font color="#EF6C00"><b><big>🔥 Kalorien</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 3. Distanz

Distanz in Kilometern und Summen für dieselben Zeiträume.

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
<font color="#2E7D32"><b><big>📍 Distanz</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 Tage</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## Home Assistant Aktivitätskarte

![Home Assistant activity dashboard](../../img/0014.png)

Dies ist eine Dashboard-Karte von Home Assistant und kein Android-Widget.

## 4. Letzter Schlaf

Dauer, Start- und Endzeit, Tief- und Leichtschlaf, Wachphasen und eine kompakte Zeitleiste mit 32 Blöcken. `sleep_json` muss `sleep_date`, `start_time`, `end_time` und `stages` mit `time` und `type` enthalten. Codes: **1 = tief, 2 = leicht, 3 = wach/Ende**. Der derzeitige Sensor `rem_sleep` steht für **Wachzeit, nicht für REM-Schlaf**.

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
{{ (v | int) // 60 }} Std. {{ (v | int) % 60 }} Min.
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#673AB7"><b><big>🌙 Letzter Schlaf</big></b></font><br>
<small><font color="#607D8B">{{ sleep.get('sleep_date', '—') }}</font></small><br>
<big><big><font color="#512DA8"><b>{{ duration(total) }}</b></font></big></big><br>
<small><font color="#607D8B">Beginn — Ende</font></small><br>
<big><font color="#263238"><b>{{ sleep.get('start_time') or '—' }} – {{ sleep.get('end_time') or '—' }}</b></font></big><br>
<small><font color="#607D8B">Tiefschlaf</font></small><br>
<big><font color="#1565C0"><b>{{ duration(deep) }}</b></font></big><br>
<small><font color="#607D8B">Leichtschlaf</font></small><br>
<big><font color="#00897B"><b>{{ duration(light) }}</b></font></big><br>
<small><font color="#607D8B">Wachphasen</font></small><br>
<big><font color="#E65100"><b>{{ (awake | int ~ ' Min.') if is_number(awake) else '—' }}</b></font></big><br>
<small><font color="#607D8B">Schlafphasen</font></small><br>
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
  <small><font color="#607D8B">Keine Schlafphasendaten</font></small>
{% endif -%}
</center>
```

## Home Assistant Schlafkarte

![Home Assistant sleep dashboard](../../img/0013.png)

Das detaillierte Schlafdiagramm im Dashboard ist von der kompakten Android-Zeitleiste zu unterscheiden.

### Kompakte Zeitleiste anpassen

- Die Zeitleiste verwendet **32** breite `█`-Blöcke ohne Leerzeichen.
- Für eine kürzere Anzeige ändern Sie `range(32)` und `i / 32` in `range(24)` und `i / 24`.
- Blau = Tiefschlaf; Türkis = Leichtschlaf; Orange = wach.
- Die Zeitleiste ist eine Näherung, keine minutengenaue Darstellung.
- Sensordauern und Zeitstempel der Phasen können voneinander abweichen.
- Android kann die CSS-Komprimierung ignorieren; weniger Blöcke sind zuverlässiger.

## Fehlerbehebung

- **Unbekannte Werte oder Striche:** Entitäts-IDs, Synchronisierung und Suffixe prüfen.
- **Kein Schlafdiagramm:** `sleep_json` bei `last_sleep_details` prüfen; mindestens zwei Phasen sind nötig.
- **Zeilenumbruch:** Weniger Blöcke verwenden oder das Widget verbreitern.
- **Andere Formatierung:** Android unterstützt nur eingeschränktes HTML, nicht Lovelace-Styling.
- **Datenschutz:** Uhren-IDs, Schlafdaten und MQTT-Zugangsdaten vor Fehlerberichten entfernen.
