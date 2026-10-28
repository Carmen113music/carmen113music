# Riffs — Llibreta de riffs

App d'Android personal per gravar idees musicals (riffs de guitarra i melodies cantades), endreçar-les per cançons i escriure'n la lletra.

**Versió 1.0 (fase 1):** gravar, biblioteca amb carpetes, escoltar, lletra i notes.
Pròximes fases: anàlisi (to, BPM i notes), mini DAW multipista, gravar sobre altres pistes.

## Com es fa servir

### Gravar
1. Tria a dalt **on es desa** (la biblioteca o una carpeta de cançó) i **què graves**: 🎸 Guitarra o 🎤 Veu.
2. Toca el **botó vermell gran**. L'anell que creix al voltant indica el volum.
3. Torna'l a tocar per aturar. La gravació es desa sola amb el títol «Idea del 28/10 a les 12:30».
4. A sota surt l'**última gravació**: toca-la per obrir-la.

El primer cop l'app demana permís de micròfon.

### Biblioteca 📁
- Cada carpeta pot ser una cançó, i a dins pot tenir subcarpetes (tornada, pont…).
- **＋ Carpeta** crea una carpeta dins la que estàs mirant.
- **🎙 Grava aquí** torna al botó gran i fa que les gravacions noves vagin a aquesta carpeta.
- Toca **▶** per escoltar una gravació sense obrir-la.
- **Mantén premuda** una gravació per moure-la, compartir-la o esborrar-la; una carpeta, per canviar-li el nom o esborrar-la.

### Fitxa d'una gravació (lletra)
- El reproductor queda sempre a dalt, també amb el teclat obert: **▶/⏸**, **⏪ 5 s**, **🔁** (repetir) i la barra per anar a qualsevol punt.
- **Atura el so quan escric**: en començar a escriure a la lletra, el so es posa en pausa.
- **En tornar a ▶, retrocedeix 2 s**: per no perdre el fil quan tornes a escoltar.
- Pots canviar el títol, escriure la **lletra** i **notes** (acords, to, idees). Es desa tot sol.
- El botó de dalt a la dreta (🎸/🎤) canvia el tipus de gravació.
- **↗ Comparteix** envia el so (WAV) per WhatsApp, correu, Drive…

### Historial 🕘 i diagnòstic 🔧
- **🕘** mostra tot el que s'ha fet, el més recent a dalt.
- **🔧** mostra l'estat del micròfon i l'espai, i permet **triar la font del micròfon**: prova-les amb «Prova 3 segons» i queda't la que soni millor. «Copia el diagnòstic» el copia per enganxar-lo al xat.

## Bon saber
- Es grava en **WAV** (qualitat de CD, mono): ocupa uns **5 MB per minut**.
- Si l'app passa a segon pla mentre grava (una trucada, canvies d'app), **s'atura i desa** el que s'ha gravat fins aquell moment. Mentre grava, la pantalla no s'apaga.
- Si l'app es tanqués de cop enmig d'una gravació, en tornar-la a obrir la recupera.
- Les gravacions són a la memòria interna de l'app. **Si desinstal·les l'app, s'esborren.** Per actualitzar-la no cal desinstal·lar: el nou APK s'instal·la per sobre i ho conserva tot.

## Com compilar-la
La compila GitHub Actions al repositori `Carmen113music/carmen113music`:
- Codi a la carpeta `riffs/` i workflow a `.github/workflows/riffs.yml` (només s'activa amb canvis a `riffs/`).
- Java, minSdk 26, targetSdk 34, AGP 8.7.3, Gradle 8.11.1, JDK 17. Sense llibreries externes.
- Signada amb la clau fixa `app/debug.keystore`, perquè cada versió s'instal·li per sobre de l'anterior.
- L'APK surt a la pestanya **Actions** → la darrera execució de «Riffs (APK)» → **Artifacts** → `Riffs`.

## Historial de versions
- **1.0** (28/10/2026) — Primera versió: gravar (guitarra/veu), biblioteca amb carpetes i subcarpetes, reproducció, lletra i notes, compartir, historial i diagnòstic amb tria de font del micròfon.
