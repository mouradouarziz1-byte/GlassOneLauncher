# PLC Data Analyzer v0.1

Prima build Windows 11 di un data browser Siemens basato su TIA Portal Openness V21.

## Funzioni incluse
- rilevamento automatico TIA Portal Openness V21
- apertura di un progetto `.ap21`
- Station Upload di una CPU tramite `StationUploadProvider`
- scelta IP e interfaccia PN/IE
- importazione PLC Tag Tables
- enumerazione Data Block e UDT
- tentativo di export SimaticML per ricavare i membri simbolici di DB/UDT
- ricerca rapida delle variabili
- esportazione catalogo in CSV

## Requisiti sul PC
- Windows 11 x64
- TIA Portal V21 con STEP 7 e TIA Portal Openness
- .NET Framework 4.8
- accesso di rete al PLC
- autorizzazioni necessarie sul PLC/TIA Portal

Al primo utilizzo Openness può richiedere l'approvazione dell'applicazione nella AllowList/Firewall di TIA Portal.

## Stato
Questa è la v0.1: il modulo di browsing/importazione è già presente. L'acquisizione ciclica e i trend realtime saranno aggiunti in una build successiva.
