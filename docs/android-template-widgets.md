# Android Home Assistant Template Widgets / Виджеты Android

These are **Home Assistant Companion App → Android → Template Widget** examples, **not Lovelace cards**. Templates use Home Assistant Jinja2 and limited Android HTML formatting.

Это примеры для **виджета «Шаблон» приложения Home Assistant на Android**, а не карточки Lovelace.

## Setup / Установка

1. Install and configure the Home Assistant Companion App on Android.
2. Add a **Home Assistant → Template** widget to the Android home screen.
3. Paste one template below into its template field and save.
4. Replace the example entity prefix with the prefix of **your own watch**. Find entity IDs under Home Assistant → Developer Tools → States.

> **Important:** The entity prefix below is an example from one watch, **not a universal device ID**. The `_2` suffixes on rolling-total sensors are also installation-specific. Verify the actual IDs before use.
>
> **Важно:** ID устройства в примерах необходимо заменить на свой. Суффикс `_2` может отсутствовать или отличаться.

The widget supports basic tags such as `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>`, and `<br>`. Do not expect HTML tables, SVG, JavaScript, or CSS to work. In particular, `font-stretch` and `letter-spacing` are **best-effort** and may be ignored by Android's HTML renderer.

## Android launcher preview / Виджеты на рабочем столе Android

![Android Home Assistant Template Widgets: steps, calories, distance and last sleep](../img/vidget.jpeg)

The screenshot shows all four Android widgets together. / На скриншоте показаны все четыре виджета Android.

## 1. Steps / Шаги

Current steps and rolling 7/30/365-day totals.

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
<font color="#1976D2"><b><big>👣 Шаги</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 дней</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 дней</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 дней</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 2. Calories / Калории

Current calories (kcal) and rolling totals.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set t = states(p ~ 'calories') -%}
{% set w = states(p ~ 'calories_last_7_days_2') -%}
{% set m = states(p ~ 'calories_last_30_days_2') -%}
{% set y = states(p ~ 'calories_last_365_days_2') -%}
{% macro value(v) -%}
{%- if is_number(v) -%}
{{ "{:,}".format(v | int).replace(",", " ") }} ккал
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#EF6C00"><b><big>🔥 Калории</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 дней</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 дней</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 дней</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 3. Distance / Расстояние

Current distance (km) and rolling totals.

```jinja2
{% set p = 'sensor.rogbid_z10_86c93e46f09c4159a06ffb592cb78eaf_' -%}
{% set t = states(p ~ 'distance') -%}
{% set w = states(p ~ 'distance_last_7_days_2') -%}
{% set m = states(p ~ 'distance_last_30_days_2') -%}
{% set y = states(p ~ 'distance_last_365_days_2') -%}
{% macro value(v) -%}
{%- if is_number(v) -%}
{{ ("%.1f" | format(v | float)).replace(".", ",") }} км
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#2E7D32"><b><big>📍 Расстояние</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 дней</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 дней</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 дней</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## Home Assistant activity card / Карточка активности Home Assistant

![Home Assistant activity card with steps totals and history charts](../img/0014.png)

This is the **Home Assistant dashboard card**, not an Android launcher widget. / Это карточка интерфейса Home Assistant, а не виджет рабочего стола Android.

## 4. Last sleep / Последний сон

Duration, start/end times, deep/light/awake metrics and a compact 32-block horizontal stage timeline. The `sleep_json` attribute must be a mapping containing `sleep_date`, `start_time`, `end_time`, and `stages` with `time` and `type`. Stage types from the watch are **1 = deep, 2 = light, 3 = awake/end**. The existing `rem_sleep` entity is used here as the **awake** metric for compatibility with the current integration; it is **not REM sleep**.

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
{{ (v | int) // 60 }} ч {{ (v | int) % 60 }} мин
{%- else -%}—{%- endif -%}
{%- endmacro %}
<center>
<font color="#673AB7"><b><big>🌙 Последний сон</big></b></font><br>
<small><font color="#607D8B">{{ sleep.get('sleep_date', '—') }}</font></small><br>
<big><big><font color="#512DA8"><b>{{ duration(total) }}</b></font></big></big><br>
<small><font color="#607D8B">Начало — окончание</font></small><br>
<big><font color="#263238"><b>{{ sleep.get('start_time') or '—' }} – {{ sleep.get('end_time') or '—' }}</b></font></big><br>
<small><font color="#607D8B">Глубокий сон</font></small><br>
<big><font color="#1565C0"><b>{{ duration(deep) }}</b></font></big><br>
<small><font color="#607D8B">Лёгкий сон</font></small><br>
<big><font color="#00897B"><b>{{ duration(light) }}</b></font></big><br>
<small><font color="#607D8B">Пробуждения</font></small><br>
<big><font color="#E65100"><b>{{ (awake | int ~ ' мин') if is_number(awake) else '—' }}</b></font></big><br>
<small><font color="#607D8B">Фазы сна</font></small><br>
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
  <small><font color="#607D8B">Нет данных о фазах сна</font></small>
{% endif -%}
</center>
```

## Home Assistant sleep card / Карточка сна Home Assistant

![Home Assistant sleep card with sleep stages, durations and history](../img/0013.png)

This screenshot shows the **Home Assistant dashboard sleep card**; its detailed graph is separate from the compact Android widget timeline. / Здесь показана карточка сна Home Assistant с подробным графиком, отличная от компактной шкалы Android-виджета.

### Compact timeline adjustments / Настройка графика

- **32** blocks are rendered without spaces using the wide `█` character.
- If the graph overflows, change **both** `range(32)` and `i / 32` to `range(24)` and `i / 24`.
- Colors: blue = deep, teal = light, orange = awake.
- The stage timeline is sampled across the recorded sleep interval. It is an **approximate visualization**, not an exact minute-by-minute chart.
- The summary duration sensors and stage timestamps can disagree; the widget displays each as provided by the integration.
- Android may ignore the CSS compression styles. The reliable width control is the number of blocks.

## Troubleshooting / Устранение проблем

- **`unknown`, `unavailable` or dashes:** Check that the entity IDs exist, the watch has synchronized, and the rolling-total suffixes match your system.
- **No sleep graph:** Check the `sleep_json` attribute of the `last_sleep_details` sensor. At least two timestamped stages are required.
- **Unexpected line wrapping:** Reduce the number of blocks, widget font size or increase the Android widget width.
- **Formatting differs from Home Assistant dashboard:** Android Template Widget has a different, limited renderer; Lovelace CSS/cards do not apply.
- **Privacy:** Do not publish real sleep history, watch IDs or MQTT credentials in issue reports without redacting them.
