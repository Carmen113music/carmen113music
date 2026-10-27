# Alarma per veu

App d'Android personal: obres l'app, dius una frase i:

- si és per a les pròximes 24 hores, posa una **alarma** al Rellotge amb l'hora i el títol;
- si dius un dia concret («divendres», «el 15 de novembre», «passat demà»), l'apunta a l'**agenda** amb avisos el dia abans a les 20:00 i el mateix dia a les 7:00. Si aquell dia ja hi ha alguna cosa apuntada, t'ho diu i et pregunta si l'apunta igualment;
- si preguntes «què tinc divendres?», et mostra el que hi ha apuntat aquell dia.

Si tot va bé, mostra una confirmació breu i es tanca sola.

## Com es fa servir

1. Obre l'app: ja comença a escoltar.
2. Digues l'hora i què t'ha de recordar, per exemple:
   - «a les 7 i mitja, portar el cotxe al taller»
   - «dos quarts de vuit, sopar amb els Puig»
   - «d'aquí a 20 minuts, treure el pa del forn»
   - «demà a les 6 del matí, aeroport»
   - «a les 8 menys quart del vespre, sortir de casa»
   - «divendres tenim sopar a les vuit» (agenda)
   - «el 15 de novembre a les 5 de la tarda, dentista» (agenda)
3. L'alarma queda creada al Rellotge i l'app mostra l'hora, el títol i quant falta.

Per tornar-ho a fer, toca el botó del micròfon. «Veure les alarmes» obre el Rellotge per comprovar-les o esborrar-les.

## Bon saber

- Si no dius «del matí», «de la tarda» o «del vespre», agafa la propera vegada que arribi aquella hora: a les 10 del matí, «a les 5» vol dir les 17:00.
- A l'agenda, si no dius matí o tarda: de les 1 a les 6 entén tarda, de les 7 a les 11 entén matí, excepte si parles de sopar, concert, assaig, cine, teatre o festa.
- El primer cop que s'apunti alguna cosa a l'agenda, l'app demanarà permís de Calendari i, si el mòbil té més d'un calendari, preguntarà en quin s'han d'apuntar les coses. Es pot canviar quan vulguis amb el botó «Agenda: …» de sota.
- Si algun dels avisos ja ha passat (per exemple, apuntes una cosa per a demà a les 22:00), només es posa el que encara toca. Si no en toca cap, l'avís es posa 1 hora abans.
- Algunes apps de rellotge (Samsung, per exemple) poden demanar confirmar l'alarma amb «Desa».

## Compilar l'APK

GitHub la compila sola cada cop que canvia aquesta carpeta (fitxer `.github/workflows/alarma-veu.yml`).
L'APK es descarrega des de la pestanya **Actions** → l'última execució d'«Alarma per veu (APK)» → **Artifacts** → `AlarmaPerVeu`.
També es pot llançar a mà amb el botó «Run workflow».

## Instal·lar-la al mòbil

1. Passa el fitxer `AlarmaPerVeu.apk` al mòbil (per correu, Drive, WhatsApp…). Si l'has descarregat en un `.zip`, descomprimeix-lo primer.
2. Obre l'APK i, si el mòbil ho demana, permet «Instal·lar apps desconegudes» per a aquella app (Fitxers, Chrome…).
3. Les versions noves s'instal·len per sobre de l'anterior sense perdre res.
