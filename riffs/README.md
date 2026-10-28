# Riffs — Llibreta de riffs

App d'Android personal per gravar idees musicals (riffs de guitarra i melodies cantades), endreçar-les per cançons i escriure'n la lletra.

**Versió 1.1:** gravar, biblioteca amb carpetes, escoltar sobre l'ona, lletra i notes, compartir.
Pròximes fases: anàlisi (to, BPM i notes), estudi multipista (mini DAW), gravar sobre altres pistes.

## Com es fa servir

A baix hi ha tres pestanyes: **Gravar**, **Biblioteca** i **Estudi**.

### Gravar
1. Tria **Guitarra** o **Veu** i, a «Es desa a», la carpeta on vols la gravació.
2. Toca el **botó rodó**: el cercle vermell es fa quadrat, el rellotge corre i l'ona es dibuixa en directe.
3. Torna'l a tocar per aturar. Es desa sola amb el títol «Idea del 28/10 a les 12:30».
4. A sota surt l'**última gravació**: ▶ per escoltar-la, o toca la targeta per obrir-la.

A dalt: l'**historial** (rellotge) i el **diagnòstic del micròfon** (controls lliscants).
El primer cop l'app demana permís de micròfon.

### Biblioteca
- Cada carpeta pot ser una cançó, i a dins pot tenir subcarpetes (tornada, pont…).
- **+** crea una carpeta dins la que estàs mirant. Dins una carpeta, el **micròfon vermell** de dalt torna a Gravar i fa que les gravacions noves vagin allà.
- Cada gravació mostra la seva **ona**, la durada, i si té lletra o notes. ▶ l'escolta sense obrir-la.
- Els **tres punts** (o mantenir premut) obren les opcions: obrir, moure, compartir, esborrar; a les carpetes, gravar-hi, canviar el nom o esborrar.

### Fitxa d'una gravació (lletra)
- A dalt, l'**ona** amb el capçal: toca-la o arrossega el dit per anar a qualsevol punt. Sota, **enrere 5 s**, **reproduir/pausa** i **repetir**. Tot queda fix a dalt, també amb el teclat obert.
- **Atura el so quan escric** i **En tornar a reproduir, retrocedeix 2 s** (no retrocedeix si acabes de triar tu el punt a l'ona).
- Títol, **lletra** i **notes** es desen sols.
- El botó de color de dalt (Guitarra/Veu) canvia el tipus de gravació.
- **Mou**, **Comparteix** (WAV per WhatsApp, correu, Drive…) i **esborrar**.

### Estudi
De moment explica què farà l'estudi multipista (fase 3) i mostra quines carpetes es podran obrir com a projecte (les que tenen dues gravacions o més).

### Diagnòstic
Estat del micròfon i de l'espai, i **tria de la font del micròfon**: prova-les amb «Prova 3 segons» i queda't la que soni millor. «Copia el diagnòstic» el copia per enganxar-lo al xat.

## Bon saber
- Es grava en **WAV** (qualitat de CD, mono): ocupa uns **5 MB per minut**.
- Si l'app passa a segon pla mentre grava (una trucada, canvies d'app), **s'atura i desa** el que s'ha gravat. Mentre grava, la pantalla no s'apaga.
- Si l'app es tanqués de cop enmig d'una gravació, en tornar-la a obrir la recupera.
- Les gravacions són a la memòria interna de l'app. **Si desinstal·les l'app, s'esborren.** Per actualitzar-la no cal desinstal·lar: el nou APK s'instal·la per sobre i ho conserva tot.

## Com compilar-la
La compila GitHub Actions al repositori `Carmen113music/carmen113music`:
- Codi a la carpeta `riffs/` i workflow a `.github/workflows/riffs.yml` (només s'activa amb canvis a `riffs/`).
- Java, minSdk 26, targetSdk 34, AGP 8.7.3, Gradle 8.11.1, JDK 17. Sense llibreries externes. Les icones es dibuixen amb codi (`Icona.java`).
- Signada amb la clau fixa `app/debug.keystore`, perquè cada versió s'instal·li per sobre de l'anterior.
- L'APK surt a **Actions** → la darrera execució de «Riffs (APK)» → **Artifacts** → `Riffs`.

## Historial de versions
- **1.1** (28/10/2026) — Corregit: la pantalla principal sortia buida (un espai invisible de la barra de dalt ocupava tota l'alçada). Disseny nou: tema fosc d'estudi, icones pròpies en lloc d'emojis, botó de gravar professional, ona en directe i rellotge amb dècimes, navegació a baix (Gravar · Biblioteca · Estudi), ona a cada gravació de la biblioteca, ona tocable per saltar a la fitxa, pestanya Estudi.
- **1.0** (28/10/2026) — Primera versió: gravar (guitarra/veu), biblioteca amb carpetes i subcarpetes, reproducció, lletra i notes, compartir, historial i diagnòstic amb tria de font del micròfon.
