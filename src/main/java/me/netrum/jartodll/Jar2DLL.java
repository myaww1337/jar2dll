package me.netrum.jartodll;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Jar2DLL {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws IOException, InterruptedException {
        String input = parseVariable(args, "input", true),
                output = parseVariable(args, "output", true),
                customEntryPoint = parseVariable(args, "entryPoint", false), cmakePath = parseVariable(args, "cmake", false);

        if(cmakePath == null) cmakePath = "C:/Program Files/CMake/";

        Path path = Paths.get("jar2dll-temp");

        if(!Files.exists(path)){
            Files.createDirectory(path);
        }else recursiveDelete(path);

        JarFile jarFile = new JarFile(input);
        Enumeration<JarEntry> entries = jarFile.entries();

        List<ClassEntry> classes = new ArrayList<>();
        Manifest manifest = null;

        byte[] bootstrapBytes = getBytes(Jar2DLL.class.getResourceAsStream("/me/netrum/jartodll/Jar2DLLClassLoader.class"));
        classes.add(new ClassEntry("me.netrum.jartodll.Jar2DLLClassLoader", bootstrapBytes));

        while(entries.hasMoreElements()){
            JarEntry entry = entries.nextElement();
            byte[] bytes = getBytes(jarFile.getInputStream(entry));

            /*
                0xCAFEBABE - Java class file magic number (first 4 bytes of file)
             */
            if(bytes.length > 4 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE && bytes[2] == (byte)0xBA && bytes[3] == (byte)0xBE){
                logger.info("Loading '{}' class...", entry.getName().replaceAll("/", "\\.").replace(".class", ""));
                classes.add(new ClassEntry(entry.getName().replaceAll("/", "\\.").replace(".class", ""), bytes));
            }else if(entry.getName().equals("META-INF/MANIFEST.MF")){
                manifest = new Manifest(jarFile.getInputStream(entry));
            }
        }

        if(classes.size() < 2){
            logger.fatal("There is no Java classes...");
            System.exit(-1);
        }
        if(manifest == null && customEntryPoint == null){
            logger.fatal("Unable to find entry point...");
            System.exit(-1);
        }

        logger.info("Creating C++ code...");

        StringBuilder cppOutput = new StringBuilder("#include \"pch.h\"\n#include \"jni.h\"\n#include <Windows.h>\n\n" +
                "typedef jint(*GetCreatedJavaVMS)(JavaVM**, jsize, jsize*);\n\n");

        for(ClassEntry classEntry : classes){
            cppOutput.append("const jbyte ").append(classEntry.name.replaceAll("\\.", "_")).append("[] = {\n\t");
            int split = 0;

            for(byte b : classEntry.bytes){
                cppOutput.append("0x").append(String.format("%02x", b).toUpperCase()).append(", ");
                if(++split % 10 == 0) {
                    cppOutput.append("\n\t");
                }
            }
            cppOutput = new StringBuilder(cppOutput.substring(0, cppOutput.length() - 2));
            cppOutput.append("\n};\n\n");
        }

        cppOutput.append("BOOL APIENTRY DllMain(HMODULE, DWORD ul_reason_for_call, LPVOID){\n" +
                        "   if(ul_reason_for_call == DLL_PROCESS_ATTACH){\n" +
                "       HMODULE jvmLib = GetModuleHandleA(\"jvm.dll\");\n" +
                "       if(!jvmLib){\n" + // If jvmLib is 0, then this process is not a Java process
                "           MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL\", MB_ICONERROR | MB_OK);\n" +
                "       }else{\n" +
                "           JavaVM* vms[1];\n" +
                "           ((GetCreatedJavaVMS)GetProcAddress(jvmLib, \"JNI_GetCreatedJavaVMs\"))(vms, 1, NULL);\n" +
                "           JavaVM* vm = vms[0];\n" +
                "           if(vm == 0){\n" +
                "               MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL\", MB_ICONERROR | MB_OK);\n" +
                        "       return FALSE;" +
                "           }\n" +
                "           JNIEnv* env;\n" +
                "           vm->AttachCurrentThread((void**)&env, NULL);\n" +
                "           if(env == 0){\n" +
                "               MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL\", MB_ICONERROR | MB_OK);\n" +
                        "       return FALSE;" +
                "           }\n\n" +
                            "" +
                            "       jclass loaderClass = env->DefineClass(\"me/netrum/jartodll/Jar2DLLClassLoader\", NULL, me_netrum_jartodll_Jar2DLLClassLoader, ").append(bootstrapBytes.length)
                .append(");\n" +
                        "       jmethodID loaderConstructor = env->GetMethodID(loaderClass, \"<init>\", \"()V\");\n" +
                        "       jobject instance = env->NewObject(loaderClass, loaderConstructor);\n\n" // Here we call constructor of Jar2DLLClassLoader to load classes
                );


        for(int i = 1; i < classes.size(); ++i){
            ClassEntry entry = classes.get(i);
            String bufferName = entry.name.replaceAll("\\.", "_") + "_buf";
            String arrayName = bufferName.replaceAll("_buf", "");

            cppOutput.append("      jbyteArray ").append(bufferName).append(" = env->NewByteArray(").append(entry.bytes.length).append(");\n");
            cppOutput.append("      env->SetByteArrayRegion(").append(bufferName).append(", 0, sizeof(").append(arrayName).append(") / sizeof(").append(bufferName).append("[0]), ").append(arrayName)
                            .append(");\n");
            cppOutput.append(String.format("        env->CallVoidMethod(instance, env->GetMethodID(loaderClass, \"appendClass\", \"([BLjava/lang/String;)V\"), %s, env->NewStringUTF(\"%s\"));\n",
                    bufferName, entry.name));
        }

        String mainClass = manifest.getMainAttributes().getValue("Main-Class");
        cppOutput.append(String.format(
                "      jobject klass = env->CallObjectMethod(instance, env->GetMethodID(loaderClass, \"findClass\", \"(Ljava/lang/String;)Ljava/lang/Class;\"), env->NewStringUTF(\"%s\"));\n" +
                        "       env->CallVoidMethod(instance, env->GetMethodID(loaderClass, \"callEntryPoint\", \"(Ljava/lang/Class;)V\"), klass);",
                mainClass
        ));

        cppOutput.append("\n   }\n\treturn TRUE;\n\t}\n}");

        Files.write(path.resolve(Paths.get("source.cpp")), cppOutput.toString().getBytes());
        Files.write(path.resolve(Paths.get("CMakeLists.txt")), getBytes(Jar2DLL.class.getResourceAsStream("/CMakeLists.txt")));
        Files.write(path.resolve(Paths.get("jni.h")), getBytes(Jar2DLL.class.getResourceAsStream("/jni.h")));
        Files.write(path.resolve(Paths.get("jni_md.h")), getBytes(Jar2DLL.class.getResourceAsStream("/jni_md.h")));
        Files.write(path.resolve(Paths.get("pch.cpp")), getBytes(Jar2DLL.class.getResourceAsStream("/pch.cpp")));
        Files.write(path.resolve(Paths.get("pch.h")), getBytes(Jar2DLL.class.getResourceAsStream("/pch.h")));

        if(!Files.exists(Paths.get(cmakePath))){
            logger.fatal("Specify CMake path with '--cmake' parameter");
            System.exit(-1);
        }

        Path cmake = Paths.get(cmakePath).resolve("bin/cmake.exe");

        logger.info("Compiling...");
        Process process = Runtime.getRuntime().exec(new String[]{
                cmake.toFile().getAbsolutePath(),
                "CMakeLists.txt"
        }, null, path.toFile().getAbsoluteFile());
        process.waitFor();

        process = Runtime.getRuntime().exec(new String[]{
                cmake.toFile().getAbsolutePath(),
                "--build",
                "."
        }, null, path.toFile().getAbsoluteFile());
        InputStream inputStream = process.getInputStream();
        int cb;
        while((cb = inputStream.read()) != -1){
            System.out.printf("%s", (char)cb);
        }

        logger.info("Cleaning up...");
        byte[] dllBytes = getBytes(Files.newInputStream(path.resolve(Paths.get("build/lib/jartodll.dll"))));
        recursiveDelete(path);
        Files.write(path.resolve(output), dllBytes);
        logger.info("Output path: {}", path.resolve(output).toFile().getAbsolutePath());
    }

    private static String parseVariable(String[] args, String variableName, boolean required){
        for(int i = 0; i < args.length; ++i){
            if(args[i].equals(String.format("--%s", variableName))){
                return args[i + 1];
            }
        }

        if(required) {
            logger.fatal("'{}' parameter is required", variableName);
            System.exit(-1);
        }

        return null;
    }

    private static byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int cb;

        while((cb = inputStream.read()) != -1){
            byteArrayOutputStream.write(cb);
        }

        return byteArrayOutputStream.toByteArray();
    }

    private static void recursiveDelete(Path path){
        for(File file : path.toFile().listFiles()){
            if(file.isDirectory()) recursiveDelete(file.toPath());

            file.delete();
        }
    }
}
