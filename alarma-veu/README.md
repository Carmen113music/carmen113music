# Alarma per veu

App d'Android personal: obres l'app, dius una frase i:

- si és per a les pròximes 24 hores, posa una **alarma** al Rellotge amb l'hora i el títol;
- si dius un dia concret («divendres», «el 15 de novembre», «passat demà»), l'apunta a l'**agenda** amb avisos el dia abans a les 20:00 i el mateix dia a les 7:00. Si aquell dia ja hi ha alguna cosa apuntada, t'ho diu i et pregunta si l'apunta igualment;
- si preguntes «què tinc divendres?», et mostra el que hi ha apuntat aquell dia;
- si demanes un temporitzador («temporitzador de 15 minuts per l'arròs», «posa 15 minuts per bullir l'aigua», «avisa'm d'aquí mitja hora»), posa una **alarma al Rellotge** a l'hora que toca (ara + el temps), amb el títol «⏱ …» (el temporitzador del Rellotge d'aquest mòbil no accepta el temps des d'una altra app).

Si tot va bé, mostra una confirmació breu i es tanca sola.

A dalt de tot hi ha sempre dos botons: **📅 Agenda** (obre l'agenda del mòbil) i **🕘 Historial** (tot el que s'ha demanat, el més recent a dalt).

## Com es fa servir

1. Obre l'app: ja comença a escoltar.
2. Digues l'hora i què t'ha de recordar, per exemple:
   - «a les 7 i mitja, portar el cotxe al taller»
   - «dos quarts de vuit, sopar amb els Puig»
   - «d'aquí a 20 minuts, treure el pa del forn» (temporitzador)
   - «demà a les 6 del matí, aeroport»
   - «a les 8 menys quart del vespre, sortir de casa»
   - «divendres tenim sopar a les vuit» (agenda)
   - «el 15 de novembre a les 5 de la tarda, dentista» (agenda)
3. L'app mostra una confirmació breu i es tanca.

Per tornar a parlar, toca el micròfon. «Alarmes» obre el Rellotge per comprovar-les o esborrar-les.

## Bon saber

- Si no dius «del matí», «de la tarda» o «del vespre», agafa la propera vegada que arribi aquella hora: a les 10 del matí, «a les 5» vol dir les 17:00.
- A l'agenda, si no dius matí o tarda: de les 1 a les 6 entén tarda, de les 7 a les 11 entén matí, excepte si parles de sopar, concert, assaig, cine, teatre o festa.
- Mentre la finestra de dictat de Google és oberta, el primer toc fora d'ella la tanca; després ja es poden tocar els botons.
- Com que el Rellotge treballa en minuts, els temporitzadors s'arrodoneixen al minut més proper.
- El primer cop que s'apunti alguna cosa a l'agenda, l'app demanarà permís de Calendari i preguntarà en quin calendari s'han d'apuntar les coses. La llista mostra cada calendari amb l'app (Google Calendar, Calendari del telèfon…) i el compte; el principal de cada compte porta una ★. Es pot canviar quan vulguis amb el botó «Agenda: …» de sota. Si un compte no hi surt, cal activar-ne la sincronització del calendari a Configuració → Comptes.
- Si algun dels avisos ja ha passat (per exemple, apuntes una cosa per a demà a les 22:00), només es posa el que encara toca. Si no en toca cap, l'avís es posa 1 hora abans.
- «Avisa'm d'aquí a mitja hora» posa un **temporitzador**; «avisa'm a les quatre i mitja» posa una **alarma**.
- Els avisos de l'agenda també queden escrits a la descripció de l'esdeveniment. Google Calendar els mostra com a temps abans de l'esdeveniment (per exemple, per a un sopar a les 20:00: «1 dia abans» i «13 hores abans»).
- Totes les alarmes que posa l'app comencen per «VR-» (per exemple, «VR-Portar el cotxe al taller» o «VR-⏱ L'arròs»), perquè sigui fàcil trobar-les i esborrar-les al Rellotge. El botó «Esborra VR-» de sota demana al Rellotge que esborri les que ha posat l'app (és una prova: cada Rellotge ho fa a la seva manera).
- Algunes apps de rellotge (Samsung, per exemple) poden demanar confirmar l'alarma amb «Desa».

## Compilar l'APK

GitHub la compila sola cada cop que canvia aquesta carpeta (fitxer `.github/workflows/alarma-veu.yml`).
L'APK es descarrega des de la pestanya **Actions** → l'última execució d'«Alarma per veu (APK)» → **Artifacts** → `AlarmaPerVeu`.
També es pot llançar a mà amb el botó «Run workflow».

## Instal·lar-la al mòbil

1. Passa el fitxer `AlarmaPerVeu.apk` al mòbil (per correu, Drive, WhatsApp…). Si l'has descarregat en un `.zip`, descomprimeix-lo primer.
2. Obre l'APK i, si el mòbil ho demana, permet «Instal·lar apps desconegudes» per a aquella app (Fitxers, Chrome…).
3. Les versions noves s'instal·len per sobre de l'anterior sense perdre res.
