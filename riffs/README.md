# Riffs — Llibreta de riffs

App d'Android personal per gravar idees musicals (riffs de guitarra i melodies cantades), endreçar-les per cançons i escriure'n la lletra.

**Versió 2.13:** gravar (amb metrònom opcional), biblioteca amb carpetes, escoltar sobre l'ona, tempo i primer temps, lletra i notes, compartir.
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
- **Graella (snap)**: la icona de la graella, a dalt, obre un desplegable amb *Compàs*, *Temps* o *Lliure* (al costat de la icona es veu quin està triat). En moure un clip, el que s'enganxa a la graella és la seva bandera (o l'inici, si no en té).
- **Afinar la bandera (mode Lliure)**: en mode *Lliure*, quan mous un clip que té la bandera visible, **la bandera es queda quieta** i l'àudio llisca per sota. Posa el cop de l'ona just sota la bandera i deixa anar: la bandera queda marcant aquell punt de l'àudio, també a la fitxa de la gravació. A dalt es veu quant has mogut l'àudio. Si la bandera quedés fora de la part que sona, es mou amb el clip com abans.
- **Gestos**: **un dit edita** (arrossegar un clip el mou; tocar un espai buit hi posa el capçal). **Dos dits mouen la pantalla**: arrossegar en horitzontal recorre la cançó, en vertical passa per les pistes, i obrir o tancar els dits **de costat** fa zoom en el temps, i **de dalt a baix** fa les pistes més altes o més baixes. El zoom només comença quan els dits s'obren o es tanquen clarament, i en un mateix gest només se'n fa un (temps o alçada); si no, només es desplaça. Si poses un segon dit mentre mous un clip, el clip torna on era.
- **Clip triat** (toca'l): les **cantonades de baix** retallen o allarguen la finestra, fins i tot més enllà del principi o del final del so (aquella part és silenci), per quadrar-la a compàs; el fitxer no es toca mai; les **cantonades de dalt** fan el **fade in** i el **fade out** (línia de silenci a 0 dB); el **punt del mig** baixa el **volum del clip** de 0 a -24 dB. Mentre arrossegues, a dalt es veu el valor.
- **Canviar clips de pista**: arrossega un clip (o un grup triat) **amunt o avall** i passa a una altra pista **sense moure's en el temps**, com per un carril. Si l'arrossegues de costat, es mou en el temps com sempre. El primer moviment del dit decideix el sentit. També funciona mantenint premut i arrossegant amunt o avall.
- **Triar diversos clips**: mantén premut un clip (el mòbil vibra) i, sense aixecar el dit, arrossega a dreta o esquerra: es van triant els clips consecutius de la pista. Tocar la capçalera d'una pista tria tots els seus clips. Per moure el grup, arrossega qualsevol dels clips triats. Les manetes (retall, fades, volum) només surten quan hi ha un sol clip triat; tocar un clip d'un grup el deixa triat sol.
- **Duplicar**: amb clips triats, a dalt surten **Duplica** i **Treu**. Duplica posa una còpia just a continuació i deixa triada la còpia: si el clip dura un compàs exacte, cada toc afegeix un compàs més al loop. Treu els treu del projecte (la gravació no s'esborra). Si una pista es queda sense clips, toca la seva capçalera per tornar-hi a posar la gravació sencera.
- **Gravar des de l'Estudi**: tocar el play només reprodueix; **mantenir-lo premut** grava. Si una pista té el **botó de gravar** (el cercle, al costat de M i S) en vermell, la gravació va a aquella pista sense preguntar. Si no, tria a quina pista vols gravar, o *Pista nova · guitarra* / *Pista nova · veu*; la que triïs (o la nova) queda amb el cercle vermell per a la propera vegada. Toca el cercle d'una altra pista per canviar-la, o el mateix per apagar-lo. La gravació entra a l'inici del compàs més proper al capçal (com a mínim el compàs 2): l'Estudi comença a sonar **un compàs abans** amb el compte («Compte: 4, 3, 2, 1» a dalt) i després surt «● Gravant». Mentrestant sonen les altres pistes i l'**ona es dibuixa en directe** (en vermell) a la pista on grava, o en una fila nova a sota si és una pista nova. Toca el botó (ara és un quadrat vermell) per aturar. La gravació nova queda amb la **bandera al primer temps del compàs d'entrada**, ja col·locada al seu lloc i corregida amb el retard mesurat al diagnòstic. També es desa a la biblioteca, dins la carpeta de la cançó.
- **Clic**: a la pastilla del tempo (a dalt), la **icona del metrònom** encén o apaga el clic (ambre = encès), tant en reproduir com en gravar; **mantenint-la premuda** surt el volum del clic (0–150 %). El **número del BPM** continua servint per canviar el tempo. **Mentre es grava, tot el so (pistes i clic) només surt pels auriculars**: sense auriculars no sona res i el compte és visual (per l'altaveu es gravaria). Si els auriculars es desconnecten enmig de la gravació, el so se silencia a l'instant. Quan només reprodueixes, sí que sona per l'altaveu.
- Una pista pot tenir clips de gravacions diferents (les que s'hi han gravat a sobre). La pista pren el nom i el color de la seva gravació principal.
- **Pistes numerades**. **Ordenar-les**: mantén premut el nom d'una pista (0,4 s, vibra) i arrossega-la amunt o avall; les altres s'aparten per fer-li lloc. Si la deixes anar sense moure-la, surten les opcions: **Canvia el nom** (només a l'Estudi; la gravació conserva el seu títol), **Nom de la gravació** (per tornar-hi) i **Esborra la pista** (es treu del projecte amb els seus clips; les gravacions continuen a la biblioteca).
- **Pista nova**: sota l'última pista hi ha un espai amb **+ Pista nova**. Crea una pista buida («Pista 4») amb el botó de gravar encès: mantén premut el play i grava-hi.
- **Pestanyes (a l'esquerra)**: **Pistes** és el taulell de sempre; **Taula** és la taula de so: una tira per pista amb el número i el nom (una línia), un **fader gros** (0 dB a tres quarts; doble toc = 0 dB) amb el **vúmetre** al costat (verd, ambre a partir de -6 dB, vermell per sobre de 0), i una columna fina amb **M**, **S** i el botó de gravar. A baix, el valor del fader i el pic dels darrers segons. **Un dit mou els faders; dos dits desplacen la taula.** La taula **es pot girar**: en vertical els faders són més llargs (s'amaguen el nom del projecte i la posició de la barra de dalt). El taulell de pistes sempre és horitzontal. També hi ha un **+** al final. L'app recorda quina pestanya feies servir.
- **Capçalera de cada pista**: **M** (silenci), **S** (solo), **cercle de gravar** (la pista on va la gravació) i el **fader** de volum (de -∞ a +6 dB; s'enganxa a 0 dB).
- Tot es desa sol a la carpeta, al fitxer `_projecte.riffs`.

### Diagnòstic
Estat del micròfon i de l'espai, i **tria de la font del micròfon**: prova-les amb «Prova 3 segons» i queda't la que soni millor. «Copia el diagnòstic» el copia per enganxar-lo al xat.

**Retard del so (latència)**: «Mesura el retard» fa sonar 12 clics (per l'altaveu o pels auriculars, si n'hi ha de connectats) i els grava. Diu quant tarda el so a arribar a la gravació i si aquest retard és sempre igual. Cal fer silenci i apujar el volum; amb auriculars, posa'n un tocant el micròfon del mòbil.
- **Cada sortida recorda el seu retard**: l'altaveu i cada parell d'auriculars (pel seu nom). En gravar, la bandera es corregeix amb el de la sortida que estiguis fent servir. Uns auriculars que no s'han mesurat mai fan servir la mitjana dels altres del mateix tipus.
- A sota hi ha la **llista de sortides** amb el seu retard: la que està connectada ara surt destacada. Amb **−** i **+** l'ajustes a mà (mantenint premut va ràpid). La paperera l'oblida. Si canvien les condicions, torna a fer la prova: la nova mesura substitueix l'anterior.
- Cada mesura també queda a la llista del diagnòstic i a «Copia el diagnòstic».

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
- **2.13** (30/10/2026) — Taula de so: número i nom més petits en una línia, M/S/gravar en una columna fina al costat del fader (més llarg), dos dits per desplaçar-la, i es pot girar en vertical. Taulell: el desplaçament amb dos dits ja no tremola (el zoom no comença fins que els dits s'obren o es tanquen prou, i un sol sentit per gest).
- **2.12** (30/10/2026) — Estudi: pistes numerades; el menú de la pista permet esborrar-la (puja/baixa ja es fa arrossegant); espai amb «+ Pista nova» sota l'última pista; tira de pestanyes a l'esquerra amb la nova **taula de so** (faders grossos, vúmetres i dB, M, S i gravar).
- **2.11** (30/10/2026) — Estudi: els clips (sols o en grup) es passen a una altra pista arrossegant amunt o avall, sense moure's en el temps. La graella és una icona amb desplegable (Compàs / Temps / Lliure). Corregit: un text llarg a la barra de dalt podia amagar el play; ja no surt «Es gravarà a…».
- **2.10** (30/10/2026) — Gravant a l'Estudi, res no sona per l'altaveu: pistes i clic només pels auriculars; sense auriculars, silenci i compte visual. Si es desconnecten els auriculars, se silencia tot.
- **2.9** (30/10/2026) — Estudi: l'ona de la gravació es veu en directe. La icona del metrònom de dalt encén o apaga el clic (també en reproduir) i, mantenint-la premuda, en canvia el volum; el BPM continua obrint el tempo.
- **2.8** (30/10/2026) — Estudi: la R de cada pista passa a ser el símbol de gravar (un cercle, vermell a la pista on es grava).
- **2.7** (30/10/2026) — Corregit: en moure una pista l'Estudi es tancava. Mentre arrossegues una pista, les altres s'aparten. Botó R a cada pista: mantenir premut el play hi grava directament, sense preguntar. Barra de dalt de l'Estudi més prima.
- **2.6** (30/10/2026) — Retard per a cada sortida (l'altaveu i cada parell d'auriculars pel seu nom), amb ajust a mà (− / +) i opció d'oblidar-lo; les mesures d'abans es conserven. Estudi: zoom vertical (dos dits de dalt a baix), canviar el nom de les pistes i ordenar-les mantenint premut i arrossegant.
- **2.5** (29/10/2026) — Estudi: gravar mantenint premut el play, a una pista existent o a una de nova, amb un compàs de compte i les altres pistes sonant; la bandera queda al primer temps del compàs d'entrada, corregida amb el retard mesurat. Les finestres dels clips es poden allargar més enllà del so (silenci) per quadrar-les a compàs. Una pista pot tenir clips de gravacions diferents.
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
