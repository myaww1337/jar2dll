package me.netrum.jartodll;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class Jar2DLLClassLoader extends ClassLoader {
    private final Map<String, byte[]> classStorage = new HashMap<>();

    public Jar2DLLClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    public void appendClass(byte[] klass, String name){
        classStorage.put(name, klass);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> loadedClass = findLoadedClass(name);
        if(loadedClass != null) return loadedClass;

        for(String klass : classStorage.keySet()){
            if(klass.equalsIgnoreCase(name)){
                byte[] bytes = classStorage.get(klass);

                return defineClass(klass, bytes, 0, bytes.length);
            }
        }

        return super.findClass(name);
    }

    public void callEntryPoint(Class<?> klass) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = klass.getDeclaredMethod("main", String[].class);
        method.invoke(null, (Object)new String[]{});
    }
}
