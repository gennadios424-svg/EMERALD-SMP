package net.emeraldsmp.worth;

public enum WorthSort {
    NAME_ASC("Name A → Z"),
    WORTH_ASC("Worth Low → High"),
    WORTH_DESC("Worth High → Low"),
    CATEGORY("Category");

    private final String displayName;
    WorthSort(String displayName){this.displayName=displayName;}
    public String displayName(){return displayName;}
    public WorthSort next(){return values()[(ordinal()+1)%values().length];}
}
