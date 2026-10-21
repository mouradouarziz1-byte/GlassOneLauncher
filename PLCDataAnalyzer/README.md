# PLC Data Analyzer v0.2

Build Windows 11 x64 per TIA Portal Openness V21.

## Correzioni v0.2
- eseguibile forzato x64
- ricerca esplicita del registro Windows 64 bit di TIA Openness V21
- diagnostica del gruppo Windows `Siemens TIA Openness`
- log persistente in `%LOCALAPPDATA%\PLCDataAnalyzer\PLCDataAnalyzer.log`
- gestione del callback di Station Upload
- campo password PLC opzionale per accesso in lettura
- scelta interfaccia PN/IE più robusta con ricerca Accessible Devices
- estrazione dei membri dei DB tramite export SimaticML quando disponibile

## Requisiti
- Windows 11 x64
- TIA Portal V21 + STEP 7
- .NET Framework 4.8
- utente Windows membro del gruppo locale `Siemens TIA Openness`
- accesso Ethernet al PLC

Al primo collegamento TIA Portal può mostrare il firewall Openness: autorizzare `PLCDataAnalyzer.exe` con **Sì** o **Sì per tutte**.

Se qualcosa non funziona, premi **Apri log** nell'app oppure esegui `Diagnostica-PLCDataAnalyzer.cmd`.
