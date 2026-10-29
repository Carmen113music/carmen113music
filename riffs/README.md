# Riffs — Llibreta de riffs

App d'Android personal per gravar idees musicals (riffs de guitarra i melodies cantades), endreçar-les per cançons i escriure'n la lletra.

**Versió 2.4:** gravar (amb metrònom opcional), biblioteca amb carpetes, escoltar sobre l'ona, tempo i primer temps, lletra i notes, compartir.
Inclou l'**Estudi multipista** (2.0). Pròximes fases: marcadors i bucle, exportar la mescla, semitons, detecció de BPM i to, gravar sobre altres pistes.

## Com es fa servir

A baix hi ha tres pestanyes: **Gravar**, **Biblioteca** i **Estudi**.

### Gravar
1. Tria **Guitarra** o **Veu** i, a «Es desa a», la carpeta on vols la gravació.
2. Toca el **botó rodó**: el cercle vermell es fa quadrat, el rellotge corre i l'ona es dibuixa en directe.
3. Torna'l a tocar per aturar. Es desa sola amb el títol «Idea del 28/10 a les 12:30».
4. A sota surt l'**última gravació**: ▶ per escoltar-la, o toca la targeta per obrir-la.

### Metrònom (claqueta)
- La rajola **Metrònom** (al costat de «Es desa a») obre els ajustos: activar-lo, **BPM** amb − i + (mantenint premut va ràpid) o **Tap** tocant al ritme, **compàs** (2/4, 3/4, 4/4, 6/8), **compte enrere** (cap, 1 o 2 compassos), i com vols el **clic**: *Amb auriculars* (per defecte) o *Només visual*. **El clic no sona mai per l'altaveu**, perquè quedaria gravat: si no hi ha auriculars, o si es desconnecten enmig de la gravació, la pantalla fa **pampallugues** a cada temps (més fortes al primer temps del compàs). «Escolta el tempo (per l'altaveu)» només serveix per provar el tempo als ajustos, quan no s'està gravant.
- Mentre graves, els **punts** de sota el rellotge marquen cada temps (ambre durant el compte enrere, vermell gravant) i el text diu el compte enrere i el compàs.
- El compte enrere **també es grava**: si hi ha una anacrusi no es perd.
- En aturar, la gravació es desa amb el seu tempo i amb la **bandera del primer temps**: l'app calcula quan va sonar el primer temps (tenint en compte el retard del mòbil) i l'enganxa a l'atac més proper de la guitarra o la veu.

A dalt: l'**historial** (rellotge) i el **diagnòstic del micròfon** (controls lliscants).
El primer cop l'app demana permís de micròfon.

### Biblioteca
- Cada carpeta pot ser una cançó, i a dins pot tenir subcarpetes (tornada, pont…).
- **+** crea una carpeta dins la que estàs mirant. Dins una carpeta, el **micròfon vermell** de dalt torna a Gravar i fa que les gravacions noves vagin allà.
- Cada gravació mostra la seva **ona**, la durada, i si té lletra o notes. ▶ l'escolta sense obrir-la, i l'ona s'il·lumina a mesura que sona.
- Toca una gravació per obrir-la. Els **tres punts** (o mantenir premut) obren el panell d'opcions: **canvia el nom**, **mou**, **comparteix** i **esborra**. A les carpetes: **grava aquí**, **canvia el nom** i **esborra**.
- Canviar el nom d'una gravació només canvia el títol: el so, la lletra i les notes no es toquen.

### Fitxa d'una gravació (lletra)
- A dalt, l'**ona** amb el capçal: toca-la o arrossega el dit per anar a qualsevol punt. Sota, **enrere 5 s**, **reproduir/pausa** i **repetir**. Tot queda fix a dalt, també amb el teclat obert.
- **Atura el so quan escric** i **En tornar a reproduir, retrocedeix 2 s** (no retrocedeix si acabes de triar tu el punt a l'ona).
- Títol, **lletra** i **notes** es desen sols.
- El botó de color de dalt (Guitarra/Veu) canvia el tipus de gravació.
- **Tempo** (icona de metrònom): posa o canvia el BPM i el compàs d'una gravació (també amb Tap mentre l'escoltes). Sobre l'ona apareixen les línies dels temps i, més marcades, les dels compassos.
- **Primer temps** (bandera): toca'l i arrossega la bandera vermella per l'ona fins al primer temps; el capçal s'hi posa perquè ho puguis escoltar. Torna a tocar «Fet» per acabar. «Treu» l'esborra.
- **Mou**, **Comparteix** (WAV per WhatsApp, correu, Drive…) i **esborrar**.

### Estudi (multipista)
- La pestanya **Estudi** llista els projectes: cada carpeta amb gravacions. També s'obre des de la Biblioteca (tres punts d'una carpeta → **Obre a l'Estudi**).
- L'Estudi sempre es veu **en horitzontal**; la resta de l'app, en vertical.
- **Cada gravació és una pista.** Les que tenen la bandera del primer temps s'encaixen soles a l'inici d'un compàs (a partir del 2, perquè hi càpiguen les anacrusis). El tempo del projecte surt del primer take amb tempo; es canvia amb el botó de tempo de dalt. Cada pista pot tenir **diversos clips** del mateix fitxer (per exemple, per fer un loop): cada clip té la seva posició, retall, fades i volum, independents dels altres. La bandera és de la gravació i es veu a tots els seus clips.
- **Regla** de compassos i temps: tocar-la o arrossegar-hi el dit mou el capçal. **Posició** a dalt a la dreta en format compàs.temps.semicorxera.
- **Snap**: *Compàs*, *Temps* o *Lliure*. En moure un clip, el que s'enganxa a la graella és la seva bandera (o l'inici, si no en té).
- **Afinar la bandera (mode Lliure)**: en mode *Lliure*, quan mous un clip que té la bandera visible, **la bandera es queda quieta** i l'àudio llisca per sota. Posa el cop de l'ona just sota la bandera i deixa anar: la bandera queda marcant aquell punt de l'àudio, també a la fitxa de la gravació. A dalt es veu quant has mogut l'àudio. Si la bandera quedés fora de la part que sona, es mou amb el clip com abans.
- **Gestos**: **un dit edita** (arrossegar un clip el mou; tocar un espai buit hi posa el capçal). **Dos dits mouen la pantalla**: arrossegar en horitzontal recorre la cançó, en vertical passa per les pistes, i obrir o tancar els dits fa zoom. Si poses un segon dit mentre mous un clip, el clip torna on era.
- **Clip triat** (toca'l): les **cantonades de baix** retallen o allarguen el que sona (el fitxer no es toca mai); les **cantonades de dalt** fan el **fade in** i el **fade out** (línia de silenci a 0 dB); el **punt del mig** baixa el **volum del clip** de 0 a -24 dB. Mentre arrossegues, a dalt es veu el valor.
- **Triar diversos clips**: mantén premut un clip (el mòbil vibra) i, sense aixecar el dit, arrossega a dreta o esquerra: es van triant els clips consecutius de la pista. Tocar la capçalera d'una pista tria tots els seus clips. Per moure el grup, arrossega qualsevol dels clips triats. Les manetes (retall, fades, volum) només surten quan hi ha un sol clip triat; tocar un clip d'un grup el deixa triat sol.
- **Duplicar**: amb clips triats, a dalt surten **Duplica** i **Treu**. Duplica posa una còpia just a continuació i deixa triada la còpia: si el clip dura un compàs exacte, cada toc afegeix un compàs més al loop. Treu els treu del projecte (la gravació no s'esborra). Si una pista es queda sense clips, toca la seva capçalera per tornar-hi a posar la gravació sencera.
- **Capçalera de cada pista**: **M** (silenci), **S** (solo) i el **fader** de volum (de -∞ a +6 dB; s'enganxa a 0 dB).
- Tot es desa sol a la carpeta, al fitxer `_projecte.riffs`.

### Diagnòstic
Estat del micròfon i de l'espai, i **tria de la font del micròfon**: prova-les amb «Prova 3 segons» i queda't la que soni millor. «Copia el diagnòstic» el copia per enganxar-lo al xat.

**Retard del so (latència)**: «Mesura el retard» fa sonar 12 clics (per l'altaveu o pels auriculars, si n'hi ha de connectats) i els grava. Diu quant tarda el so a arribar a la gravació respecte del que l'app calcula, i si aquest retard és sempre igual o varia. Cal fer silenci i apujar el volum; amb auriculars, posa'n un tocant el micròfon del mòbil. Cada mesura queda a la llista del diagnòstic (amb la sortida, la font del micròfon i totes les mesures) i s'inclou a «Copia el diagnòstic». En gravar amb metrònom, la **bandera del primer temps es corregeix sola** amb el retard mesurat per a la sortida (altaveu, Bluetooth, cable) i la font de micròfon que facis servir. Si per aquella font no hi ha mesura, fa servir la mitjana de les altres fonts amb la mateixa sortida; si no n'hi ha cap, no corregeix res. El diagnòstic mostra la correcció que s'aplica.

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
- **2.4** (29/10/2026) — Estudi: diversos clips per pista, cadascun amb el seu retall, fades, volum i posició. Mantenir premut i arrossegar tria clips consecutius; el grup es mou de cop. Botons Duplica (just a continuació, per fer loops) i Treu. Els projectes antics s'obren igual.
- **2.3** (29/10/2026) — La bandera del primer temps es corregeix sola amb el retard mesurat al diagnòstic (per a cada sortida i font de micròfon). Estudi: en mode Lliure la bandera es queda quieta i l'àudio llisca per sota, per afinar-la a ull; el canvi es desa a la fitxa.
- **2.2** (29/10/2026) — Diagnòstic: prova de **retard del so**. Mesura quant arriba tard el clic del metrònom a la gravació (amb l'altaveu o amb auriculars) i si el retard és fix o variable. Desa totes les mesures per decidir com corregir la bandera del primer temps.
- **2.1** (28/10/2026) — Estudi: la pantalla només es desplaça amb dos dits (horitzontal, vertical i zoom alhora); un dit és només per editar. Si entra un segon dit durant una edició, el clip torna on era.
- **2.0** (28/10/2026) — Estudi multipista: pantalla horitzontal, regla de compassos, una pista per take encaixada per la bandera, moure amb snap (compàs, temps, lliure), retallar, fades, volum del clip, M/S/fader per pista, reproducció amb capçal que segueix, zoom amb dos dits. Llista de projectes a la pestanya Estudi i «Obre a l'Estudi» a les carpetes.
- **1.4** (28/10/2026) — Corregit: el clic del metrònom ja no pot sonar mai per l'altaveu (es gravava). Només amb auriculars o visual; si es desconnecten els auriculars, el clic s'apaga a l'instant. Pampallugues a tota la pantalla a cada temps i punts més grans.
- **1.3** (28/10/2026) — Metrònom per gravar (BPM, tap, compàs, compte enrere, clic amb auriculars, sempre o visual) i punts de temps a la pantalla. Bandera del primer temps automàtica i ajustable, tempo de cada gravació, graella de temps i compassos sobre l'ona, BPM a la biblioteca.
- **1.2** (28/10/2026) — L'ona de la biblioteca s'il·lumina mentre sona. Panell d'opcions nou amb l'estil de l'app (icones grans, sense «Obre»). Nova opció per canviar el nom d'una gravació. Triar carpeta, confirmar i escriure un nom també tenen l'estil de l'app.
- **1.1** (28/10/2026) — Corregit: la pantalla principal sortia buida (un espai invisible de la barra de dalt ocupava tota l'alçada). Disseny nou: tema fosc d'estudi, icones pròpies en lloc d'emojis, botó de gravar professional, ona en directe i rellotge amb dècimes, navegació a baix (Gravar · Biblioteca · Estudi), ona a cada gravació de la biblioteca, ona tocable per saltar a la fitxa, pestanya Estudi.
- **1.0** (28/10/2026) — Primera versió: gravar (guitarra/veu), biblioteca amb carpetes i subcarpetes, reproducció, lletra i notes, compartir, historial i diagnòstic amb tria de font del micròfon.
