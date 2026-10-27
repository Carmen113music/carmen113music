# Alarma per veu

App d'Android personal: obres l'app, dius una frase i et posa una alarma real al Rellotge del mòbil amb l'hora i el títol que has dit.

## Com es fa servir

1. Obre l'app: ja comença a escoltar.
2. Digues l'hora i què t'ha de recordar, per exemple:
   - «a les 7 i mitja, portar el cotxe al taller»
   - «dos quarts de vuit, sopar amb els Puig»
   - «d'aquí a 20 minuts, treure el pa del forn»
   - «demà a les 6 del matí, aeroport»
   - «a les 8 menys quart del vespre, sortir de casa»
3. L'alarma queda creada al Rellotge i l'app mostra l'hora, el títol i quant falta.

Per tornar-ho a fer, toca el botó del micròfon. «Veure les alarmes» obre el Rellotge per comprovar-les o esborrar-les.

## Bon saber

- Si no dius «del matí», «de la tarda» o «del vespre», agafa la propera vegada que arribi aquella hora: a les 10 del matí, «a les 5» vol dir les 17:00.
- El Rellotge només admet alarmes per a les pròximes 24 hores. Per això no accepta dies de la setmana («dijous a les 8») ni «passat demà».
- Algunes apps de rellotge (Samsung, per exemple) poden demanar confirmar l'alarma amb «Desa».

## Compilar l'APK

GitHub la compila sola cada cop que canvia aquesta carpeta (fitxer `.github/workflows/alarma-veu.yml`).
L'APK es descarrega des de la pestanya **Actions** → l'última execució d'«Alarma per veu (APK)» → **Artifacts** → `AlarmaPerVeu`.
També es pot llançar a mà amb el botó «Run workflow».

## Instal·lar-la al mòbil

1. Passa el fitxer `AlarmaPerVeu.apk` al mòbil (per correu, Drive, WhatsApp…). Si l'has descarregat en un `.zip`, descomprimeix-lo primer.
2. Obre l'APK i, si el mòbil ho demana, permet «Instal·lar apps desconegudes» per a aquella app (Fitxers, Chrome…).
3. Les versions noves s'instal·len per sobre de l'anterior sense perdre res.
