# Android Home Assistant Template Widgets

[English](android-template-widgets.md) · [Russian](i18n/android-template-widgets.ru.md) · [Français](i18n/android-template-widgets.fr.md) · [Lëtzebuergesch](i18n/android-template-widgets.lb.md) · [Deutsch](i18n/android-template-widgets.de.md) · [Italiano](i18n/android-template-widgets.it.md)

These examples are for the **Home Assistant Companion App → Android → Template Widget**, not Lovelace cards. They use Home Assistant Jinja2 and the limited HTML formatting supported by Android.

## Setup

1. Install and configure the Home Assistant Companion App on Android.
2. Add a **Home Assistant → Template** widget to the Android home screen.
3. Paste one of the templates below into the widget's template field and save.
4. Replace the example sensor prefix with the prefix for **your watch**. Find your entity IDs in Home Assistant → Developer Tools → States.

> **Important:** The example sensor prefix is specific to one watch, not a universal device ID. Rolling-total sensors may also have different suffixes (such as `_2`). Verify every entity ID before use.

The widget supports basic tags such as `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>`, and `<br>`. HTML tables, SVG, JavaScript, and most CSS are unsupported. In particular, `font-stretch` and `letter-spacing` may be ignored by Android.

## Android home screen preview

![Android Home Assistant widgets](../img/vidget.jpeg)

The screenshot shows all four Android home screen widgets together.

## 1. Steps

Current steps and rolling totals for the last 7, 30 and 365 days.

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
<font color="#1976D2"><b><big>👣 Steps</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 days</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 days</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 days</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 2. Calories

Current energy expenditure in kcal and rolling totals.

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
<font color="#EF6C00"><b><big>🔥 Calories</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 days</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 days</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 days</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 3. Distance

Current distance in kilometres and rolling totals.

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
<font color="#2E7D32"><b><big>📍 Distance</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 days</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 days</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 days</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## Home Assistant activity dashboard card

![Home Assistant activity dashboard](../img/0014.png)

This is a Home Assistant dashboard card, not an Android home screen widget.

## 4. Last sleep

Duration, start and end times, deep and light sleep, awake time, and a compact 32-block stage timeline. The `sleep_json` attribute must contain `sleep_date`, `start_time`, `end_time`, and `stages` with `time` and `type`. Stage codes: **1 = deep, 2 = light, 3 = awake/end**. For compatibility, the current `rem_sleep` entity represents **awake time, not REM sleep**.

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
<font color="#673AB7"><b><big>🌙 Last sleep</big></b></font><br>
<small><font color="#607D8B">{{ sleep.get('sleep_date', '—') }}</font></small><br>
<big><big><font color="#512DA8"><b>{{ duration(total) }}</b></font></big></big><br>
<small><font color="#607D8B">Start — end</font></small><br>
<big><font color="#263238"><b>{{ sleep.get('start_time') or '—' }} – {{ sleep.get('end_time') or '—' }}</b></font></big><br>
<small><font color="#607D8B">Deep sleep</font></small><br>
<big><font color="#1565C0"><b>{{ duration(deep) }}</b></font></big><br>
<small><font color="#607D8B">Light sleep</font></small><br>
<big><font color="#00897B"><b>{{ duration(light) }}</b></font></big><br>
<small><font color="#607D8B">Awake</font></small><br>
<big><font color="#E65100"><b>{{ (awake | int ~ ' min') if is_number(awake) else '—' }}</b></font></big><br>
<small><font color="#607D8B">Sleep stages</font></small><br>
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
  <small><font color="#607D8B">No sleep stage data</font></small>
{% endif -%}
</center>
```

## Home Assistant sleep dashboard card

![Home Assistant sleep dashboard](../img/0013.png)

The detailed dashboard sleep chart is separate from the compact Android widget timeline.

### Adjusting the compact timeline

- The chart uses **32** adjacent wide `█` characters without inserted spaces.
- To make it narrower, change both `range(32)` and `i / 32` to `range(24)` and `i / 24`.
- Blue = deep sleep; teal = light sleep; orange = awake.
- The timeline is sampled and approximate; it is not a minute-by-minute chart.
- Summary sensors and stage timestamps may disagree; the widget shows the data provided by each source.
- Android may ignore CSS text compression. Reducing the number of blocks is more reliable.

## Troubleshooting

- **Unknown values or dashes:** Verify sensor IDs, synchronization and rolling-total suffixes.
- **No sleep graph:** Verify the `sleep_json` attribute on `last_sleep_details`; at least two stages are needed.
- **Graph wraps:** Reduce the number of blocks or increase the widget width.
- **Different formatting from Lovelace:** Android widgets use a limited HTML renderer, not Lovelace cards.
- **Privacy:** Redact watch IDs, sleep history and MQTT credentials before posting issue reports.
