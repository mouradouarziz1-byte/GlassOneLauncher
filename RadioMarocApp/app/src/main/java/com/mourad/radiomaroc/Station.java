package com.mourad.radiomaroc;

public final class Station {
    public final String id;
    public final String name;
    public final String city;
    public final String category;
    public final String description;
    public final String primaryUrl;
    public final String backupUrl;
    public final String lowDataUrl;
    public final String badge;

    public Station(String id, String name, String city, String category, String description,
                   String primaryUrl, String backupUrl, String lowDataUrl, String badge) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.category = category;
        this.description = description;
        this.primaryUrl = primaryUrl;
        this.backupUrl = backupUrl;
        this.lowDataUrl = lowDataUrl;
        this.badge = badge;
    }

    public String subtitle() {
        if (city == null || city.isEmpty()) return category;
        return category + " • " + city;
    }
}
