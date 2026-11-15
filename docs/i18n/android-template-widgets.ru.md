# Android-виджеты Home Assistant

[English](../android-template-widgets.md) · [Русский](android-template-widgets.ru.md) · [Français](android-template-widgets.fr.md) · [Lëtzebuergesch](android-template-widgets.lb.md) · [Deutsch](android-template-widgets.de.md) · [Italiano](android-template-widgets.it.md)

Примеры предназначены для **Home Assistant Companion → Android → виджет «Шаблон»**, а не для карточек Lovelace. Используются шаблоны Jinja2 и ограниченное HTML-форматирование Android.

## Установка

1. Установите и настройте приложение Home Assistant Companion на Android.
2. Добавьте на рабочий стол Android виджет **Home Assistant → Шаблон**.
3. Вставьте один из приведённых ниже шаблонов в поле виджета и сохраните.
4. Замените префикс датчиков на префикс **своих часов**. Идентификаторы доступны в Home Assistant → Инструменты разработчика → Состояния.

> **Важно:** Префикс датчиков относится к конкретным часам. Суффиксы датчиков сумм (например, `_2`) тоже могут отличаться. Проверьте все идентификаторы.

Поддерживаются основные теги `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>`, `<br>`. HTML-таблицы, SVG, JavaScript и большинство CSS-свойств не работают. Android может игнорировать `font-stretch` и `letter-spacing`.

## Виджеты на рабочем столе Android

![Android Home Assistant widgets](../../img/vidget.jpeg)

На снимке показаны все четыре Android-виджета.

## 1. Шаги

Шаги и суммы за последние 7, 30 и 365 дней.

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

## 2. Калории

Калории (ккал) и суммы за выбранные периоды.

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

## 3. Расстояние

Расстояние (км) и суммы за выбранные периоды.

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

## Карточка активности Home Assistant

![Home Assistant activity dashboard](../../img/0014.png)

Это карточка панели Home Assistant, а не Android-виджет.

## 4. Последний сон

Продолжительность, время начала и окончания, глубокий и лёгкий сон, пробуждения и компактный график из 32 блоков. Атрибут `sleep_json` должен содержать `sleep_date`, `start_time`, `end_time`, `stages` с `time` и `type`. Коды фаз: **1 — глубокий, 2 — лёгкий, 3 — пробуждение/окончание**. В текущей интеграции `rem_sleep` используется для **пробуждений, а не REM-сна**.

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

## Карточка сна Home Assistant

![Home Assistant sleep dashboard](../../img/0013.png)

Подробный график сна на панели Home Assistant отличается от компактной шкалы Android.

### Настройка компактного графика

- График состоит из **32** широких символов `█` без пробелов.
- Чтобы уменьшить ширину, замените `range(32)` и `i / 32` на `range(24)` и `i / 24`.
- Синий — глубокий сон, бирюзовый — лёгкий, оранжевый — пробуждение.
- График приблизительный, а не поминутный.
- Длительности в датчиках и временные отметки фаз могут различаться.
- Android может игнорировать CSS-сжатие; надёжнее уменьшать количество блоков.

## Решение проблем

- **Прочерки или неизвестные значения:** проверьте ID датчиков, синхронизацию и суффиксы.
- **Нет графика сна:** проверьте `sleep_json` датчика `last_sleep_details`; нужно минимум две отметки.
- **График переносится:** уменьшите число блоков или увеличьте ширину виджета.
- **Форматирование отличается:** Android-виджет не поддерживает оформление Lovelace.
- **Конфиденциальность:** скрывайте ID часов, историю сна и пароли MQTT при публикации отчётов.
