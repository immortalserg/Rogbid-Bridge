# Widgets Android Home Assistant

[English](../android-template-widgets.md) · [Русский](android-template-widgets.ru.md) · [Français](android-template-widgets.fr.md) · [Lëtzebuergesch](android-template-widgets.lb.md) · [Deutsch](android-template-widgets.de.md) · [Italiano](android-template-widgets.it.md)

Ces exemples concernent le **widget Modèle de Home Assistant Companion sur Android**, et non les cartes Lovelace. Ils utilisent Jinja2 et le rendu HTML limité d’Android.

## Installation

1. Installez et configurez Home Assistant Companion sur Android.
2. Ajoutez le widget **Home Assistant → Modèle** à l’écran d’accueil.
3. Collez l’un des modèles ci-dessous et enregistrez.
4. Remplacez le préfixe des entités par celui de **votre montre**. Consultez Home Assistant → Outils de développement → États.

> **Important:** Le préfixe correspond à une montre particulière. Les suffixes des capteurs de cumul (comme `_2`) peuvent varier. Vérifiez tous les identifiants.

Les balises `<center>`, `<font color="...">`, `<big>`, `<small>`, `<b>` et `<br>` sont prises en charge. Les tableaux HTML, SVG, JavaScript et la plupart des propriétés CSS ne le sont pas. Android peut ignorer `font-stretch` et `letter-spacing`.

## Aperçu des widgets Android

![Android Home Assistant widgets](../../img/vidget.jpeg)

La capture présente les quatre widgets sur l’écran d’accueil Android.

## 1. Pas

Nombre de pas actuel et cumuls sur 7, 30 et 365 jours.

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
<font color="#1976D2"><b><big>👣 Pas</big></b></font><br>
<big><big><font color="#263238"><b>{{ value(t) }}</b></font></big></big><br>
<small><font color="#607D8B">7 jours</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 jours</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 jours</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 2. Calories

Calories en kcal et cumuls sur les mêmes périodes.

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
<small><font color="#607D8B">7 jours</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 jours</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 jours</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## 3. Distance

Distance en kilomètres et cumuls sur les mêmes périodes.

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
<small><font color="#607D8B">7 jours</font></small><br>
<big><font color="#263238"><b>{{ value(w) }}</b></font></big><br>
<small><font color="#607D8B">30 jours</font></small><br>
<big><font color="#263238"><b>{{ value(m) }}</b></font></big><br>
<small><font color="#607D8B">365 jours</font></small><br>
<big><font color="#263238"><b>{{ value(y) }}</b></font></big>
</center>
```

## Carte d’activité Home Assistant

![Home Assistant activity dashboard](../../img/0014.png)

Il s’agit d’une carte du tableau de bord Home Assistant, pas d’un widget Android.

## 4. Dernier sommeil

Durée, heures de début et de fin, sommeil profond et léger, éveils et frise compacte de 32 blocs. `sleep_json` doit contenir `sleep_date`, `start_time`, `end_time` et `stages` avec `time` et `type`. Codes : **1 = profond, 2 = léger, 3 = éveil/fin**. Dans l’intégration actuelle, `rem_sleep` indique **l’éveil, et non le sommeil paradoxal**.

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
<font color="#673AB7"><b><big>🌙 Dernier sommeil</big></b></font><br>
<small><font color="#607D8B">{{ sleep.get('sleep_date', '—') }}</font></small><br>
<big><big><font color="#512DA8"><b>{{ duration(total) }}</b></font></big></big><br>
<small><font color="#607D8B">Début — fin</font></small><br>
<big><font color="#263238"><b>{{ sleep.get('start_time') or '—' }} – {{ sleep.get('end_time') or '—' }}</b></font></big><br>
<small><font color="#607D8B">Sommeil profond</font></small><br>
<big><font color="#1565C0"><b>{{ duration(deep) }}</b></font></big><br>
<small><font color="#607D8B">Sommeil léger</font></small><br>
<big><font color="#00897B"><b>{{ duration(light) }}</b></font></big><br>
<small><font color="#607D8B">Éveils</font></small><br>
<big><font color="#E65100"><b>{{ (awake | int ~ ' min') if is_number(awake) else '—' }}</b></font></big><br>
<small><font color="#607D8B">Phases du sommeil</font></small><br>
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
  <small><font color="#607D8B">Aucune donnée sur les phases</font></small>
{% endif -%}
</center>
```

## Carte de sommeil Home Assistant

![Home Assistant sleep dashboard](../../img/0013.png)

Le graphique détaillé du tableau de bord est distinct de la frise compacte du widget Android.

### Ajuster la frise compacte

- La frise utilise **32** blocs `█` accolés sans espaces.
- Pour la raccourcir, remplacez `range(32)` et `i / 32` par `range(24)` et `i / 24`.
- Bleu = profond ; turquoise = léger ; orange = éveil.
- La frise est approximative et non une représentation minute par minute.
- Les durées des capteurs peuvent différer des horodatages des phases.
- Android peut ignorer la compression CSS ; réduire le nombre de blocs est plus fiable.

## Dépannage

- **Valeurs inconnues ou tirets :** vérifiez les entités, la synchronisation et les suffixes.
- **Pas de graphique :** vérifiez `sleep_json` sur `last_sleep_details` ; deux phases au minimum sont nécessaires.
- **Retour à la ligne :** réduisez le nombre de blocs ou élargissez le widget.
- **Mise en forme différente :** le moteur HTML Android est limité, contrairement à Lovelace.
- **Confidentialité :** masquez les identifiants, les données de sommeil et les secrets MQTT avant de signaler un problème.
