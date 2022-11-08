package me.netrum.jartodll;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

public class Jar2DLLClassLoader extends ClassLoader {
    private final Map<String, byte[]> resourceStorage = new HashMap<>();

    public Jar2DLLClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public void appendResource(byte[] src, String name){
        resourceStorage.put(name, src);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(name);
        if(loadedClass != null) return loadedClass;

        for(String klass : resourceStorage.keySet()){
            if(klass.equalsIgnoreCase(name)){
                byte[] bytes = resourceStorage.get(klass);

                return defineClass(klass, bytes, 0, bytes.length);
            }
        }

        return super.findClass(name);
    }

    public void callEntryPoint(Class<?> klass) throws NoSuchMethodException {
        Method method = klass.getDeclaredMethod("main", String[].class);

        new Thread(() -> {
            try {
                method.invoke(null, (Object)new String[]{});
            } catch (Exception e) {
                System.out.printf("[!]: There is something went wrong with application:\n");
                e.printStackTrace();
            }
        }).start(); // Would be better if we will use a new thread
    }

    @Override
    protected URL findResource(String name) {
        for(String resource : resourceStorage.keySet()){
            if(resource.equals(name)){
                try {
                    // From https://stackoverflow.com/questions/17776884/any-way-to-create-a-url-from-a-byte-array

                    return new URL(null, "bytes:///", new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) {
                            return new URLConnection(u) {
                                @Override
                                public void connect() {}

                                @Override
                                public InputStream getInputStream() {
                                    return new ByteArrayInputStream(resourceStorage.get(resource));
                                }
                            };
                        }
                    });
                } catch (Exception e) {
                    System.out.printf("[!]: There is something went wrong with application:\n");
                    e.printStackTrace();
                }
            }
        }

        return super.findResource(name);
    }
}
