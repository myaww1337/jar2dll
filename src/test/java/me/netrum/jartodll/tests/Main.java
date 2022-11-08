package me.netrum.jartodll.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) throws IOException { // DLL will start here
        System.out.printf("[+]: DLL was loaded successfully\n");

        try{
            Class.forName("TestClass");
            System.out.printf("[+]: Class#forName TestClass\n");
        } catch (ClassNotFoundException e) {
            System.out.printf("[-]: Class#forName TestClass\n");
        }

        TestClass.doTest();
        System.out.printf("[+]: TestClass#doTest\n");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int cb;
        InputStream resource = Main.class.getResourceAsStream("/resource");

        while((cb = resource.read()) != -1){
            byteArrayOutputStream.write(cb);
        }

        String content = byteArrayOutputStream.toString();
        System.out.printf("%s\n", content);
        System.out.printf("[+]: Read from the resource\n");
    }
}
