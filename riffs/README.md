# Riffs — Llibreta de riffs

App d'Android personal per gravar idees musicals (riffs de guitarra i melodies cantades), endreçar-les per cançons i escriure'n la lletra.

**Versió 2.28:** gravar (amb metrònom opcional), biblioteca amb carpetes, escoltar sobre l'ona, tempo i primer temps, lletra i notes, compartir.
Inclou l'**Estudi multipista** (2.0). Inclou els **kits de bateria** (2.14) i les **pistes de bateria amb drumpad** (2.17) i les **pistes de sinte amb piano roll** (2.22). Pròximes fases: editor del so del sinte, bases de fàbrica i importar MIDI, marcadors i bucle, exportar la mescla, semitons, detecció de BPM i to, gravar sobre altres pistes.

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

### Pistes de bateria (drumpad)
- **+ Pista nova** pregunta de quin tipus: **Àudio** (per gravar guitarra o veu, com sempre), **Bateria** o **Sinte** (vegeu més avall).
- La pista de **bateria** és **lila**, té la icona del tambor i es crea **buida**.
- **Mantén premut** en un espai buit de la fila de la bateria (vibra): hi apareix una **finestra amb un ritme d'un compàs**, a l'inici del compàs on tens el dit. La primera porta un ritme bàsic (bombo, caixa i hihat); les següents són una **còpia del ritme de la finestra d'abans**, per continuar-lo i fer-ne variacions sense tocar l'altra.
- **Doble toc a una finestra** obre el **drumpad** amb el seu ritme. També: mantén premut el nom de la pista i deixa'l anar → *Obre el drumpad* (obre la finestra triada, o la primera).
- Les finestres es mouen, es retallen i tenen fades com els altres clips. **Duplica** fa còpies **independents**: cada còpia té el seu propi ritme, i si n'edites una, les altres no canvien.
- **Drumpad** (en horitzontal): una fila per cada tipus de so del kit (Kick, Snare, Hihat…) i, per defecte, una casella per semicorxera (16 en 4/4, 12 en 3/4 i 6/8). El botó **Graella** (a dalt) canvia quantes caselles té cada temps: **4** (semicorxeres), **8** (fuses, més fina), **3** o **6** (tresets); en 6/8, 2 o 4. Els cops que ja hi ha es queden al seu lloc. **Toca una casella** per posar o treure un cop (sona). **Mantén-la premuda i arrossega amunt o avall** per canviar-ne la força (suau, normal, fort: es veu per la intensitat del color i les barretes). **Toca el nom d'una fila**: *Tria el so* (variants d'aquell tipus de tots els kits: cada so que toques **sona** i queda triat, i el panell no es tanca fins que toques *Fet*), *Volum*, *Silencia* i *Treu els cops*.
- A dalt: el **kit** (canviar-lo manté els cops), la **llargada** del ritme (1 a 4 compassos; els compassos nous surten **en blanc**; amb més d'un compàs surten pestanyes per anar de l'un a l'altre, o **llisca amb dos dits de costat** per passar al compàs següent o a l'anterior) i el **play**, que fa sonar el ritme en bucle (es pot editar mentre sona). La columna que s'il·lumina segueix el so que surt de debò per l'altaveu o els auriculars.
- En sortir, el ritme es converteix en el so de la finestra. Si n'has canviat la llargada, la finestra s'allarga o s'escurça (vigila que no trepitgi la del costat: sonarien les dues). Si canvies el **tempo** o el **compàs** del projecte, la bateria es torna a fer al tempo nou i es manté als mateixos compassos. Les pistes de bateria no tenen botó de gravar.
- Els ritmes i el seu so es guarden dins la carpeta de la cançó, en una carpeta amagada (`.bateria`) que la Biblioteca no mostra.

### Pistes de sinte (piano roll)
- **+ Pista nova → Sinte** i tria un **so de fàbrica**: *Baix sub*, *Baix gros*, *Baix àcid*, *Baix pluck*, *Lead serra*, *Lead quadrat*, *Lead suau*, *Lead brut*, *Pluck* o *Pad*. El sinte és **monofònic**: toca una sola nota alhora (com un baix o una melodia).
- La pista de sinte és **verda**, amb la icona d'un teclat, i es crea **buida**. Funciona com la bateria: **mantén premut** un espai buit de la fila per posar-hi una **finestra d'un compàs** (les noves surten buides, amb el mateix so), i **doble toc** a la finestra per obrir el **piano roll**. També: mantén premut el nom de la pista i deixa'l anar → *Obre el piano roll*. **Duplica** fa còpies independents.
- **Piano roll** (en horitzontal): a l'esquerra un **teclat** (de Do0 a Do6; el Do central és el **Do3**) i a la dreta la graella del compàs. Es veu una **octava i mitja**; **llisca amunt o avall** per anar a les notes greus o agudes. S'obre a la zona de les notes que ja hi ha (o a la dels baixos o dels leads, segons el so).
  - **Toca una casella buida**: hi posa una nota (sona). **Toca una nota**: la treu.
  - **Arrossega una nota** per moure-la de costat (en el temps) o amunt i avall (d'altura; va sonant a cada nota).
  - **Arrossega la punta dreta** d'una nota per allargar-la o escurçar-la. Les notes noves surten amb la llargada de l'última que has allargat (al principi, una corxera).
  - **Dos dits** (com a l'Estudi: un dit edita, dos dits mouen): **obre'ls o tanca'ls de costat** per fer **zoom del temps** (de tota la frase fins a un sol temps: amb les notes més llargues és molt més fàcil agafar-ne la punta i allargar-les o escurçar-les); **obre'ls amunt i avall** per fer les **files més altes o més baixes** (es recorda); **arrossega'ls** per moure't per la frase i per les notes. Les pestanyes de compàs porten aquell compàs a l'esquerra.
  - Com que només sona una nota alhora, si en poses una a sobre d'una altra, la de davant s'escurça (o es treu).
  - **Toca el teclat** per escoltar una nota.
  - A dalt: el **so** (cada so que toques sona amb una frase curta i queda triat; s'aplica a **totes les finestres de la pista**), la **llargada** (1 a 8 compassos), la **graella** (4, 8, 3 o 6 caselles per temps) i el **play** (en bucle; es pot editar mentre sona). Amb més d'un compàs, pestanyes per anar a cada compàs.
  - **Solo** (a dalt): vegeu «Solo als editors».
  - En els sons amb *glide* (els leads i el *Baix àcid*), dues notes que es toquen (l'una acaba on comença l'altra) **llisquen** l'una cap a l'altra; si vols que no llisquin, deixa una casella buida entre mig.
- En sortir, la frase es converteix en el so de la finestra. Si canvies el tempo o el compàs del projecte, es torna a fer al tempo nou. Les frases es guarden a la carpeta amagada `.sinte` de la cançó.
- **Gravar tocant en directe** (com amb l'àudio): les pistes de sinte tenen el **botó de gravar**. Encén-lo i **mantén premut el play**: sona el projecte des d'un compàs abans (amb el compte i el clic, si el tens encès; aquí sí que pot sonar per l'altaveu, perquè no es grava cap micròfon) i als **dos terços de baix** surt un **teclat de dues octaves**. Amb **−** i **+** el pujes o el baixes d'octava (s'obre a Do1–Do3 per als baixos i Do3–Do5 per als leads). El que toques sona al moment amb el so de la pista. Pots fer servir més d'un dit, però sona només l'última tecla premuda (si la deixes i n'hi ha una altra de premuda, torna a aquella; arrossegar el dit canvia de nota, i amb glide, llisca).
  - Toca el **quadrat de dalt** per aturar: el que has tocat es posa a la pista en **finestres noves** a partir del compàs d'entrada (de fins a 8 compassos cadascuna), **ajustat a semicorxeres**, i ho pots retocar al **piano roll** (doble toc). El que toques durant el compte no es desa (si una nota comença just abans de l'entrada, comença a l'entrada).
  - Si a la pista ja hi havia finestres en aquell tros, no es treuen: sonarien juntes (com els clips d'àudio que es trepitgen).

### Tocar sense gravar (assajar)
- Les pistes de **sinte**, **MIDI** i **teremin** tenen un **botó petit amb l'instrument** a la capçalera, al costat del nom. En tocar-lo s'obre el teclat (o la quadrícula del teremin) als dos terços de baix: sona en directe amb el so de la pista, però **no es grava res**.
- Pots tocar sol o fer sonar el projecte amb el **play** de dalt i tocar-hi a sobre. Hi tens totes les opcions: octaves, **Acords** i el llapis per triar-los (MIDI), **Notes justes** (teremin).
- **Tanca** (o tornar a tocar el botó de la pista) el tanca. Si mantens premut el play per gravar, el panell d'assaig es tanca i comença la gravació normal.

### Pistes MIDI (128 instruments)
- Primer cal el **banc de sons** (una sola vegada, uns 31 MB): Gravar → botó d'ajustos → **Instruments MIDI** → *Baixa el banc*. També es pot importar un altre banc `.sf2` del mòbil. El banc és **GeneralUser GS**, de S. Christian Collins (lliure per fer-ne música).
- **+ Pista nova → MIDI**: tria la família (pianos, guitarres, baixos, cordes, metalls…) i l'instrument. La pista és **blava**, amb la icona d'un endoll MIDI. És **polifònica**: pot fer acords.
- Funciona com el sinte: **mantén premut** la fila per posar-hi una finestra, **doble toc** per obrir el **piano roll** (aquí les notes es poden posar una a sobre de l'altra per fer acords; només es trepitgen les de la mateixa altura). El botó del so tria l'**instrument**: primer la família i després l'instrument, que sona en tocar-lo; s'aplica a tota la pista.
- **Gravar tocant**: botó de gravar de la pista i **mantén premut el play**. El teclat de dues octaves és **polifònic** (diversos dits alhora).
- **Acords** (com els pads d'acords del Cubase): al teclat, el botó **Acords** fa que **cada tecla toqui un acord sencer**, amb el nom escrit a la tecla. D'entrada són els acords de Do major a les blanques (Do, Rem, Mim, Fa, Sol, Lam, Sidim) i acords majors a les negres (Do#, Mib, Fa#, Lab, Sib). El **llapis** del costat canvia l'acord de cada tecla (arrel i tipus: major, menor, 7, maj7, m7, sus2, sus4, 6, add9, 9, dim, aug, power…). L'arrel de l'acord queda a la nota més propera a la tecla, així a l'octava de baix sona més greu.
- En aturar, el que has tocat queda en finestres noves, ajustat a semicorxeres, i es pot retocar al piano roll.

### Pistes de teremin
- **+ Pista nova → Teremin** i tria un so: *clàssic*, *de pel·lícula*, *de vidre*, *veu*, *violoncel* o *sirena*. La pista és **rosa**, amb la icona d'una antena, i ja surt amb el **botó de gravar encès**.
- El teremin **no es dibuixa**: es toca. **Mantén premut el play**: sona el projecte des d'un compàs abans (amb el compte i el clic) i als dos terços de baix surt una **quadrícula amb un punt al mig**:
  - **A la dreta del punt, més agut; a l'esquerra, més greu.** Hi ha una línia per semitò (més marcada a cada Do, amb el nom) i, d'un costat a l'altre, dues octaves. El to és **continu**, com un teremin de debò: pots lliscar i fer vibrato amb el dit.
  - **Amunt, més fort; avall, més fluix.** Quan aixeques el dit, calla.
  - Amb **−** i **+** canvies l'octava del punt del mig (es recorda).
  - **Notes justes**: si l'encens, el to salta a la nota més propera (més fàcil per afinar). Apagat, és lliure.
  - Toca el **quadrat de dalt** per aturar: el que has tocat queda en **finestres noves** a partir del compàs d'entrada (de fins a 8 compassos).
- **Es desa el gest del dit, no el so.** Amb **doble toc** a una finestra (o mantenint premut el nom de la pista → *Obre el teremin*) veus el camí del dit (el to amunt i avall, més gruixut on sonava més fort), l'escoltes en bucle (amb **Solo** o amb la resta de pistes) i pots **canviar el so**: cada so que toques sona i, en triar-lo, el que vas tocar es torna a fer amb el so nou a **totes les finestres de la pista**. El gest no s'edita: si no t'agrada, torna'l a gravar.
- Les finestres es mouen, es retallen i es dupliquen com les altres. Si canvies el tempo, el gest es manté als mateixos compassos. Es guarda a la carpeta amagada `.teremin` de la cançó.

### Desfés
- La **fletxa que fa la volta** desfà l'últim canvi. N'hi ha una a la barra de dalt de l'**Estudi** (al costat de la posició) i una al **piano roll**, al **drumpad** i a la pantalla del **teremin**. Quan no hi ha res per desfer, es veu apagada. Es poden desfer fins a 60 passos seguits.
- A l'Estudi desfà el que fas amb les pistes: moure, retallar, fades i volum de clips, duplicar o treure clips, finestres noves, faders, M/S, noms, ordre i pistes noves o esborrades, el tempo i les gravacions. Una gravació desfeta **no s'esborra**: surt de les pistes però continua a la Biblioteca.
- Als editors desfà les notes (posar, treure, moure, allargar), els cops, la llargada, la graella, el kit i el so.
- Els passos per desfer es perden en sortir de la pantalla. El que canvies dins el drumpad o el piano roll es desfà allà, no a l'Estudi.

### Solo als editors (drumpad i piano roll)
- El botó **Solo** de dalt tria què sona amb el play: **encès**, només el que estàs editant; **apagat** (per defecte), sona **amb la resta de pistes de l'Estudi** que no estiguin mutejades (amb els seus faders i el solo de la taula), just al tros on és la finestra, i el bucle es repeteix sobre aquell tros. La tria es recorda i és la mateixa als dos editors.

### Kits de bateria
- S'hi entra des de **Diagnòstic i ajustos** (botó d'ajustos de la pantalla Gravar), apartat **Bateria virtual**: **Importa samples de bateria** obre directament el selector de carpetes; **Baixa els kits de GitHub** baixa el zip dels kits del grup (vegeu més avall) i l'importa; la fila «N kits importats» obre la llista de kits.
- **Importar**: posa la carpeta dels kits al mòbil (per exemple a Baixades), toca el **+** i tria-la. Pots triar la carpeta d'un sol kit o la que en conté uns quants: **cada carpeta amb fitxers WAV es converteix en un kit** amb el nom de la carpeta.
- L'app llegeix qualsevol WAV habitual (mono o estèreo; 8, 16, 24 o 32 bits; 44,1, 48, 88,2 o 96 kHz) i el **converteix** al format de l'app (mono, 44.100 Hz, 16 bits), en treu el silenci de l'inici i del final, i l'**endreça pel nom del fitxer**: Kick, Snare, Rimshot, Palmes, Hihat tancat/obert/pedal, Toms, Ride, Crash, Percussió. El que no reconeix va a **Altres**. Del nom es treu el prefix comú («Gimme_Kick1» → «Kick 1»). En acabar surt un resum amb el format original dels fitxers.
- **Kits a GitHub**: el fitxer `kits-bateria.zip` va adjunt a una *release* del repositori amb l'etiqueta `kits` (adreça: `https://github.com/Carmen113music/carmen113music/releases/download/kits/kits-bateria.zip`). Dins el zip, una carpeta per kit (pot haver-hi una carpeta mare). Els kits que ja tens no es tornen a importar. Per canviar els kits: edita la release, esborra el zip vell i puja'n un de nou amb el mateix nom. Millor sense accents als noms de les carpetes (els zips de Windows els poden desfigurar).
- Els kits es guarden **dins l'app** (carpeta `kits`): no cal tornar-los a importar quan instal·les una versió nova. Si es desinstal·la l'app, es perden. Els fitxers originals no es toquen.
- Toca un kit per desplegar-ne els sons, agrupats per tipus. **Toca un so per escoltar-lo.** Els tres punts (o mantenir premut) d'un so: **Canvia el tipus** (a quina fila del drumpad sortirà), **Canvia el nom** i **Treu del kit**. D'un kit: **Canvia el nom** i **Esborra**.

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
- **2.28** (30/10/2026) — Botó a les pistes d'instruments (sinte, MIDI, teremin) per obrir el teclat i **tocar sense gravar**, amb el play o sense, amb totes les opcions (acords, octaves, notes justes).
- **2.27** (30/10/2026) — **Pistes MIDI**: banc de sons GeneralUser GS (128 instruments) amb reproductor propi, piano roll polifònic, gravar tocant amb el teclat polifònic i **acords** a les tecles (com els pads d'acords del Cubase). Pantalla «Instruments MIDI» per baixar o importar el banc.
- **2.26** (30/10/2026) — **Desfés** a l'Estudi, al piano roll, al drumpad i a la pantalla del teremin (fins a 60 passos).
- **2.25** (30/10/2026) — **Teremin**: nou tipus de pista amb 6 sons. Es toca en directe sobre una quadrícula (de costat el to, continu; amunt i avall el volum; opció «Notes justes»). Es desa el gest i no el so, així es pot canviar de so després de gravar. Pantalla del teremin per veure el gest, escoltar-lo i canviar-ne el so.
- **2.24** (30/10/2026) — **Gravar el sinte en directe**: les pistes de sinte tenen botó de gravar; mantenint premut el play surt un **teclat virtual de dues octaves** als dos terços de baix, el sinte sona en directe (amb la mateixa veu que el piano roll) i, en aturar, el que has tocat queda en finestres noves, ajustat a semicorxeres.
- **2.23** (30/10/2026) — Piano roll: **zoom amb dos dits** (de costat, el temps; amunt i avall, l'alçada de les files, que es recorda) i **arrossegar amb dos dits** per moure's; així és més fàcil allargar i escurçar les notes. Drumpad i piano roll: botó **Solo**; amb el Solo apagat, el bucle sona amb la resta de pistes de l'Estudi que no estan mutejades.
- **2.22** (30/10/2026) — **Pistes de sinte**: «+ Pista nova» ara pregunta **Àudio, Bateria o Sinte**. Sinte monofònic propi (dos osciladors, sub, soroll, filtre amb ressonància i envolupant, LFO, glide i distorsió) amb **10 sons de fàbrica** (baixos i leads). **Piano roll** amb teclat de Do0 a Do6, una octava i mitja a la vista, notes que es posen, es mouen i s'allarguen amb el dit, bucle, 1–8 compassos i graella triable.
- **2.21** (30/10/2026) — Drumpad: la columna que s'il·lumina va sincronitzada amb el que sona (abans anava per davant pel retard del mòbil). Botó **Graella** per triar 4, 8, 3 o 6 caselles per temps (2 o 4 en 6/8).
- **2.20** (30/10/2026) — Drumpad: en triar el so d'una fila, cada so que toques sona i el panell es queda obert (*Fet* per tancar). Els compassos nous surten en blanc. Dos dits de costat sobre la graella per canviar de compàs.
- **2.19** (30/10/2026) — Bateria: **Duplica** fa còpies independents de les finestres (cada una amb el seu ritme), per poder canviar-ne una sense tocar les altres.
- **2.18** (30/10/2026) — La pista de bateria es crea **buida**: **mantenir premut** un espai buit de la fila hi posa una **finestra d'un compàs** (la primera amb un ritme bàsic, les altres amb una còpia del ritme d'abans) i el **doble toc** a una finestra obre el seu drumpad. Ja no s'omple sola fins al final.
- **2.17** (30/10/2026) — **Pistes de bateria**: «+ Pista nova» pregunta Àudio o Bateria. La bateria (lila) es crea amb un ritme bàsic i s'edita amb el **drumpad** (graella de semicorxeres, força per cop, so, volum i silenci per fila, kit, 1–4 compassos, bucle). Doble toc al nom de la pista per obrir-lo. Segueix els canvis de tempo i compàs del projecte.
- **2.16** (30/10/2026) — **Baixa els kits de GitHub** (a Diagnòstic i ajustos i a la pantalla de kits buida): baixa `kits-bateria.zip` de la release «kits», l'obre i importa cada carpeta com a kit, saltant els que ja tens. L'app demana permís d'internet (Android el dona sol).
- **2.15** (30/10/2026) — La importació de kits passa a **Diagnòstic i ajustos** (apartat «Bateria virtual», botó «Importa samples de bateria»); l'Estudi ja no té el botó de la bateria. El diagnòstic copiat diu quants kits hi ha.
- **2.14** (30/10/2026) — **Kits de bateria**: importar carpetes de WAV des del mòbil (un o diversos kits de cop), amb conversió automàtica a mono 44.100 Hz 16 bits, silencis retallats i classificació pel nom (Kick, Snare, Hihat…). Pantalla per escoltar els sons, canviar-ne el tipus o el nom i esborrar. Primer pas de les pistes de bateria.
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
