package me.netrum.jartodll.base;

public class ResourceEntry {
    public final String name;
    public final byte[] bytes;

    public ResourceEntry(String name, byte[] bytes) {
        this.name = name;
        this.bytes = bytes;
    }
}
