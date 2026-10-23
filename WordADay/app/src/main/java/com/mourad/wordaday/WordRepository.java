package com.mourad.wordaday;

import java.time.LocalDate;

public final class WordRepository {

    public static final class Word {
        public final String english;
        public final String italian;
        public final String example;
        public final String exampleItalian;
        public final String category;

        public Word(String english, String italian, String example, String exampleItalian, String category) {
            this.english = english;
            this.italian = italian;
            this.example = example;
            this.exampleItalian = exampleItalian;
            this.category = category;
        }
    }

    private static final Word[] WORDS = new Word[] {
        new Word("achieve", "raggiungere / ottenere", "I want to achieve a B1 level in English.", "Voglio raggiungere il livello B1 in inglese.", "Obiettivi"),
        new Word("improve", "migliorare", "I want to improve my English every day.", "Voglio migliorare il mio inglese ogni giorno.", "Obiettivi"),
        new Word("learn", "imparare", "I learn one new word every day.", "Imparo una nuova parola ogni giorno.", "Studio"),
        new Word("practice", "fare pratica", "Practice helps you speak with confidence.", "Fare pratica ti aiuta a parlare con sicurezza.", "Studio"),
        new Word("remember", "ricordare", "Try to remember this word tomorrow.", "Prova a ricordare questa parola domani.", "Studio"),
        new Word("explain", "spiegare", "Can you explain the problem again?", "Puoi spiegare di nuovo il problema?", "Lavoro"),
        new Word("check", "controllare", "Please check the machine before start-up.", "Controlla la macchina prima dell'avviamento.", "Lavoro"),
        new Word("issue", "problema / anomalia", "We found an issue with the sensor.", "Abbiamo trovato un problema con il sensore.", "Tecnico"),
        new Word("fault", "guasto", "The drive stopped because of a fault.", "L'azionamento si è fermato a causa di un guasto.", "Tecnico"),
        new Word("signal", "segnale", "The PLC is waiting for the start signal.", "Il PLC sta aspettando il segnale di avvio.", "PLC"),
        new Word("input", "ingresso", "This digital input is not active.", "Questo ingresso digitale non è attivo.", "PLC"),
        new Word("output", "uscita", "The output turns on the motor contactor.", "L'uscita attiva il contattore del motore.", "PLC"),
        new Word("enable", "abilitare", "Enable the drive before starting the axis.", "Abilita l'azionamento prima di avviare l'asse.", "PLC"),
        new Word("disable", "disabilitare", "Disable the function during maintenance.", "Disabilita la funzione durante la manutenzione.", "PLC"),
        new Word("speed", "velocità", "The motor speed is too high.", "La velocità del motore è troppo alta.", "Tecnico"),
        new Word("value", "valore", "The current value is shown on the HMI.", "Il valore attuale è mostrato sull'HMI.", "Tecnico"),
        new Word("increase", "aumentare", "Increase the tension slowly.", "Aumenta lentamente la tensione.", "Tecnico"),
        new Word("decrease", "diminuire", "Decrease the speed to 50 meters per minute.", "Diminuisci la velocità a 50 metri al minuto.", "Tecnico"),
        new Word("replace", "sostituire", "We need to replace the battery.", "Dobbiamo sostituire la batteria.", "Manutenzione"),
        new Word("connect", "collegare", "Connect the Ethernet cable to the switch.", "Collega il cavo Ethernet allo switch.", "Rete"),
        new Word("disconnect", "scollegare", "Disconnect the power before maintenance.", "Scollega l'alimentazione prima della manutenzione.", "Sicurezza"),
        new Word("network", "rete", "The PLC is connected to the PROFINET network.", "Il PLC è collegato alla rete PROFINET.", "Rete"),
        new Word("device", "dispositivo", "The device is visible online.", "Il dispositivo è visibile online.", "Tecnico"),
        new Word("available", "disponibile", "Is the technician available this afternoon?", "Il tecnico è disponibile questo pomeriggio?", "Lavoro"),
        new Word("schedule", "programma / pianificare", "We need to schedule the test for Monday.", "Dobbiamo pianificare il test per lunedì.", "Lavoro"),
        new Word("meeting", "riunione", "The meeting starts at nine o'clock.", "La riunione inizia alle nove.", "Lavoro"),
        new Word("customer", "cliente", "The customer asked for a new function.", "Il cliente ha chiesto una nuova funzione.", "Lavoro"),
        new Word("support", "supporto / assistenza", "Our team provides remote support.", "Il nostro team fornisce assistenza da remoto.", "Lavoro"),
        new Word("solution", "soluzione", "We found a solution to the problem.", "Abbiamo trovato una soluzione al problema.", "Lavoro"),
        new Word("report", "rapporto / relazione", "I will send the commissioning report today.", "Invierò oggi il rapporto di collaudo.", "Lavoro"),
        new Word("update", "aggiornamento / aggiornare", "Please update the PLC software.", "Aggiorna il software del PLC.", "Lavoro"),
        new Word("confirm", "confermare", "Please confirm the test result.", "Conferma il risultato del test.", "Lavoro"),
        new Word("require", "richiedere / necessitare", "This function requires a restart.", "Questa funzione richiede un riavvio.", "Lavoro"),
        new Word("during", "durante", "The alarm appeared during production.", "L'allarme è comparso durante la produzione.", "Uso comune"),
        new Word("before", "prima", "Check the guards before starting the machine.", "Controlla le protezioni prima di avviare la macchina.", "Uso comune"),
        new Word("after", "dopo", "Call me after the test.", "Chiamami dopo il test.", "Uso comune"),
        new Word("although", "anche se / sebbene", "Although the motor was hot, it kept running.", "Anche se il motore era caldo, ha continuato a funzionare.", "B1"),
        new Word("instead", "invece", "Use this cable instead of the old one.", "Usa questo cavo invece di quello vecchio.", "B1"),
        new Word("probably", "probabilmente", "The problem is probably in the sensor.", "Il problema è probabilmente nel sensore.", "B1"),
        new Word("enough", "abbastanza / sufficiente", "The pressure is high enough.", "La pressione è abbastanza alta.", "B1"),
        new Word("careful", "attento", "Be careful when the machine is moving.", "Fai attenzione quando la macchina è in movimento.", "Sicurezza"),
        new Word("safe", "sicuro", "Make sure the area is safe.", "Assicurati che l'area sia sicura.", "Sicurezza"),
        new Word("delay", "ritardo", "The flight has a thirty-minute delay.", "Il volo ha trenta minuti di ritardo.", "Viaggio"),
        new Word("luggage", "bagaglio", "Where can I collect my luggage?", "Dove posso ritirare il mio bagaglio?", "Viaggio"),
        new Word("boarding", "imbarco", "Boarding starts at gate twelve.", "L'imbarco inizia al gate dodici.", "Viaggio"),
        new Word("departure", "partenza", "What time is the departure?", "A che ora è la partenza?", "Viaggio"),
        new Word("arrival", "arrivo", "The arrival time is 18:40.", "L'orario di arrivo è le 18:40.", "Viaggio"),
        new Word("journey", "viaggio", "The journey takes about two hours.", "Il viaggio dura circa due ore.", "Viaggio"),
        new Word("nearby", "nelle vicinanze", "Is there a restaurant nearby?", "C'è un ristorante qui vicino?", "Viaggio"),
        new Word("direction", "direzione / indicazione", "Can you give me directions to the station?", "Puoi indicarmi la strada per la stazione?", "Viaggio"),
        new Word("receipt", "ricevuta / scontrino", "Can I have the receipt, please?", "Posso avere la ricevuta, per favore?", "Viaggio"),
        new Word("reservation", "prenotazione", "I have a reservation under my name.", "Ho una prenotazione a mio nome.", "Viaggio"),
        new Word("comfortable", "comodo", "This hotel room is very comfortable.", "Questa camera d'albergo è molto comoda.", "Uso comune"),
        new Word("choose", "scegliere", "Choose the correct program on the HMI.", "Scegli il programma corretto sull'HMI.", "Uso comune"),
        new Word("change", "cambiare", "We need to change the parameter.", "Dobbiamo cambiare il parametro.", "Uso comune"),
        new Word("continue", "continuare", "The machine can continue production.", "La macchina può continuare la produzione.", "Uso comune"),
        new Word("stop", "fermare / fermarsi", "Press this button to stop the line.", "Premi questo pulsante per fermare la linea.", "Uso comune"),
        new Word("start", "avviare / iniziare", "We can start the test now.", "Possiamo iniziare il test adesso.", "Uso comune"),
        new Word("ready", "pronto", "The line is ready for production.", "La linea è pronta per la produzione.", "Uso comune"),
        new Word("missing", "mancante", "A safety signal is missing.", "Manca un segnale di sicurezza.", "Diagnostica"),
        new Word("active", "attivo", "The alarm is still active.", "L'allarme è ancora attivo.", "Diagnostica"),
        new Word("healthy", "in buono stato / regolare", "All drives are healthy.", "Tutti gli azionamenti sono in stato regolare.", "Diagnostica"),
        new Word("warning", "avviso", "The HMI shows a temperature warning.", "L'HMI mostra un avviso di temperatura.", "Diagnostica"),
        new Word("reset", "ripristinare / reset", "Reset the alarm after checking the cause.", "Resetta l'allarme dopo aver controllato la causa.", "Diagnostica"),
        new Word("cause", "causa", "We need to find the root cause.", "Dobbiamo trovare la causa principale.", "Diagnostica"),
        new Word("measure", "misurare", "Measure the battery voltage with a multimeter.", "Misura la tensione della batteria con un multimetro.", "Tecnico"),
        new Word("voltage", "tensione elettrica", "The battery voltage is too low.", "La tensione della batteria è troppo bassa.", "Tecnico"),
        new Word("current", "corrente / attuale", "Check the motor current during acceleration.", "Controlla la corrente del motore durante l'accelerazione.", "Tecnico"),
        new Word("torque", "coppia", "The motor torque increases under load.", "La coppia del motore aumenta sotto carico.", "Tecnico"),
        new Word("load", "carico", "The motor is running without load.", "Il motore sta girando senza carico.", "Tecnico"),
        new Word("tension", "tensione meccanica", "The web tension is 300 newtons per meter.", "La tensione del materiale è 300 newton per metro.", "Processo"),
        new Word("width", "larghezza", "Enter the material width on the HMI.", "Inserisci la larghezza del materiale sull'HMI.", "Processo"),
        new Word("length", "lunghezza", "The print length is within tolerance.", "La lunghezza di stampa è entro tolleranza.", "Processo"),
        new Word("tolerance", "tolleranza", "The allowed tolerance is five millimeters.", "La tolleranza ammessa è cinque millimetri.", "Processo"),
        new Word("adjust", "regolare", "Adjust the speed until the value is stable.", "Regola la velocità finché il valore non è stabile.", "Processo"),
        new Word("stable", "stabile", "The signal is now stable.", "Il segnale adesso è stabile.", "Processo"),
        new Word("increase", "aumentare", "Increase the setpoint by five percent.", "Aumenta il setpoint del cinque per cento.", "Processo"),
        new Word("reduce", "ridurre", "Reduce the pressure if the film stretches.", "Riduci la pressione se il film si allunga.", "Processo"),
        new Word("stretch", "allungare / stirare", "Too much tension can stretch the film.", "Troppa tensione può allungare il film.", "Processo"),
        new Word("result", "risultato", "The test result is better than yesterday.", "Il risultato del test è migliore di ieri.", "Lavoro"),
        new Word("compare", "confrontare", "Compare the two measurements.", "Confronta le due misure.", "Lavoro"),
        new Word("difference", "differenza", "There is a small difference between the values.", "C'è una piccola differenza tra i valori.", "Uso comune"),
        new Word("possible", "possibile", "Is it possible to test it today?", "È possibile provarlo oggi?", "Uso comune"),
        new Word("necessary", "necessario", "A restart is necessary after the update.", "Dopo l'aggiornamento è necessario un riavvio.", "Uso comune"),
        new Word("understand", "capire", "I understand the problem now.", "Adesso capisco il problema.", "Uso comune"),
        new Word("repeat", "ripetere", "Could you repeat that more slowly?", "Potresti ripeterlo più lentamente?", "Conversazione"),
        new Word("mean", "significare", "What does this word mean?", "Cosa significa questa parola?", "Conversazione"),
        new Word("say", "dire", "How do you say this in English?", "Come si dice questo in inglese?", "Conversazione"),
        new Word("speak", "parlare", "Please speak a little more slowly.", "Per favore parla un po' più lentamente.", "Conversazione"),
        new Word("confidence", "sicurezza in sé / fiducia", "Practice gives you more confidence.", "La pratica ti dà più sicurezza.", "Obiettivi")
    };

    private WordRepository() {}

    public static Word forDate(LocalDate date) {
        int index = Math.floorMod((int) date.toEpochDay(), WORDS.length);
        return WORDS[index];
    }

    public static Word today() {
        return forDate(LocalDate.now());
    }

    public static int size() {
        return WORDS.length;
    }
}
