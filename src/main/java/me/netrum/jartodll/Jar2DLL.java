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
    public static final Logger logger = LogManager.getLogger();

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

        List<ResourceEntry> classes = new ArrayList<>(), resources = new ArrayList<>();
        Manifest manifest = null;

        byte[] bootstrapBytes = getBytes(Jar2DLL.class.getResourceAsStream("/me/netrum/jartodll/Jar2DLLClassLoader.class"));
        classes.add(new ResourceEntry("me.netrum.jartodll.Jar2DLLClassLoader", bootstrapBytes));

        byte[] bootstrapBytes_1 = getBytes(Jar2DLL.class.getResourceAsStream("/me/netrum/jartodll/Jar2DLLClassLoader$1.class"));
        classes.add(new ResourceEntry("me.netrum.jartodll.Jar2DLLClassLoader$1", bootstrapBytes_1));

        byte[] bootstrapBytes_2 = getBytes(Jar2DLL.class.getResourceAsStream("/me/netrum/jartodll/Jar2DLLClassLoader$1$1.class"));
        classes.add(new ResourceEntry("me.netrum.jartodll.Jar2DLLClassLoader$1$1", bootstrapBytes_2));

        while(entries.hasMoreElements()){
            JarEntry entry = entries.nextElement();
            byte[] bytes = getBytes(jarFile.getInputStream(entry));

            /*
                0xCAFEBABE - Java class file magic number (first 4 bytes of file)
             */
            if(bytes.length > 4 && bytes[0] == (byte)0xCA && bytes[1] == (byte)0xFE && bytes[2] == (byte)0xBA && bytes[3] == (byte)0xBE){
                logger.info("Loading '{}' class...", entry.getName().replaceAll("/", "\\.").replace(".class", ""));
                classes.add(new ResourceEntry(entry.getName().replaceAll("/", "\\.").replace(".class", ""), bytes));
            }else if(entry.getName().equals("META-INF/MANIFEST.MF")){
                manifest = new Manifest(jarFile.getInputStream(entry));
            }else{
                if(!entry.isDirectory()) resources.add(new ResourceEntry(entry.getName(), bytes));
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

        StringBuilder cppOutput = new StringBuilder();

        cppOutput.append("#include \"pch.h\"\n");
        cppOutput.append("#include \"jni.h\"\n");
        cppOutput.append("#include <Windows.h>\n\n");
        cppOutput.append("typedef jint(*GetCreatedJavaVMS)(JavaVM**, jsize, jsize*);\n\n");

        // classes
        for(ResourceEntry resourceEntry : classes){
            StringBuilder content = new StringBuilder();
            /*
                Allocates a new array in the C++ source
             */
            cppOutput.append("const jbyte ").append(resourceEntry.name.replaceAll("\\.", "_")).append("[] = {\n\t");
            int split = 0;

            for(byte b : resourceEntry.bytes){
                if(!Character.isISOControl((char)b)){
                    content.append((char)b).append(" ");
                }

                cppOutput.append("0x").append(String.format("%02x", b).toUpperCase()).append(", ");
                if(++split % 10 == 0) {
                    if(!content.toString().isEmpty()){
                        cppOutput.append(" // ").append(content);
                    }
                    cppOutput.append("\n\t");
                    content = new StringBuilder();
                }
            }
            cppOutput = new StringBuilder(cppOutput.substring(0, cppOutput.length() - 2));
            cppOutput.append("\n};\n\n");
        }

        // resources
        for(ResourceEntry resourceEntry : resources){
            cppOutput.append("const jbyte ").append(resourceEntry.name.replaceAll("/", "_")).append("[] = {\n\t");
            int split = 0;
            StringBuilder content = new StringBuilder();

            for(byte b : resourceEntry.bytes){
                if(!Character.isISOControl((char)b)){
                    content.append((char)b).append(" ");
                }

                cppOutput.append("0x").append(String.format("%02x", b).toUpperCase()).append(", ");
                if(++split % 10 == 0) {
                    cppOutput.append(" // ").append(content).append("\n\t");
                    content = new StringBuilder();
                }
            }
            cppOutput = new StringBuilder(cppOutput.substring(0, cppOutput.length() - 2));
            cppOutput.append("\n};\n\n");
        }

        cppOutput.append("BOOL APIENTRY DllMain(HMODULE, DWORD ul_reason_for_call, LPVOID){\n");
        cppOutput.append("  if(ul_reason_for_call == DLL_PROCESS_ATTACH){\n");
        cppOutput.append("      HMODULE jvmLib = GetModuleHandleA(\"jvm.dll\");\n");
        cppOutput.append("      if(!jvmLib){\n");
        cppOutput.append("          MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL [https://github.com/netrumbtw/jar2dll]\", MB_ICONERROR | MB_OK);\n");
        cppOutput.append("      }else{\n");
        cppOutput.append("          JavaVM* vms[1];\n");
        cppOutput.append("          ((GetCreatedJavaVMS)GetProcAddress(jvmLib, \"JNI_GetCreatedJavaVMs\"))(vms, 1, NULL);\n");
        cppOutput.append("          JavaVM* vm = vms[0];\n");
        cppOutput.append("          if(vm == 0){\n");
        cppOutput.append("              MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL [https://github.com/netrumbtw/jar2dll]\", MB_ICONERROR | MB_OK);\n");
        cppOutput.append("              return FALSE;\n");
        cppOutput.append("          }\n");
        cppOutput.append("          JNIEnv* env;\n");
        cppOutput.append("          vm->AttachCurrentThread((void**)&env, NULL);\n");
        cppOutput.append("          if(env == 0){\n");
        cppOutput.append("              MessageBoxA(NULL, \"Sorry! But We can't load this DLL in this process\", \"Jar2DLL\", MB_ICONERROR | MB_OK);\n");
        cppOutput.append("              return FALSE;\n");
        cppOutput.append("          }\n\n");

        // system classes begins here
        cppOutput.append("          jclass loaderClass = env->DefineClass(\"me/netrum/jartodll/Jar2DLLClassLoader\", NULL, me_netrum_jartodll_Jar2DLLClassLoader, ").append(bootstrapBytes.length)
                .append("); // System class\n");

        cppOutput.append("          env->DefineClass(\"me/netrum/jartodll/Jar2DLLClassLoader$1\", NULL, me_netrum_jartodll_Jar2DLLClassLoader$1, ").append(bootstrapBytes_1.length)
                .append("); // System class\n");

        cppOutput.append("          env->DefineClass(\"me/netrum/jartodll/Jar2DLLClassLoader$1$1\", NULL, me_netrum_jartodll_Jar2DLLClassLoader$1$1, ").append(bootstrapBytes_2.length)
                .append("); // System class\n");

        cppOutput.append("          jmethodID loaderConstructor = env->GetMethodID(loaderClass, \"<init>\", \"()V\");\n");
        cppOutput.append("          jobject instance = env->NewObject(loaderClass, loaderConstructor);\n\n");


        for(int i = 3; i < classes.size(); ++i){ // Loop starts from 3 because we have system classes (Jar2DLLClassLoader, Jar2DLLClassLoader$1 and $1$1)
            ResourceEntry entry = classes.get(i);
            String bufferName = entry.name.replaceAll("\\.", "_") + "_buf";
            String arrayName = bufferName.replaceAll("_buf", "");

            /*
                Here we allocate a new Java byte array, because Jar2DLLClassLoader#appendResource accepts byte array
             */
            cppOutput.append("          jbyteArray ").append(bufferName).append(" = env->NewByteArray(").append(entry.bytes.length).append(");");
            cppOutput.append(" // ").append(entry.name).append(" class\n");
            cppOutput.append("          env->SetByteArrayRegion(").append(bufferName).append(", 0, sizeof(").append(arrayName).append(") / sizeof(").append(bufferName).append("[0]), ").append(arrayName)
                    .append(");\n"); // Filling array with class bytes

            cppOutput.append(String.format("          env->CallVoidMethod(instance, env->GetMethodID(loaderClass, \"appendResource\", \"([BLjava/lang/String;)V\"), %s, env->NewStringUTF(\"%s\"));\n\n",
                    bufferName, entry.name)); // A-a-a-nd... finally. We are ready to call appendResource method.
        }

        for(ResourceEntry resource : resources){
            String bufferName = resource.name.replaceAll("/", "_") + "_buf";
            String arrayName = bufferName.replaceAll("_buf", "");


            cppOutput.append("          jbyteArray ").append(bufferName).append(" = env->NewByteArray(").append(resource.bytes.length).append(");");
            cppOutput.append(" // ").append(resource.name).append(" resource\n");
            cppOutput.append("          env->SetByteArrayRegion(").append(bufferName).append(", 0, sizeof(").append(arrayName).append(") / sizeof(").append(bufferName).append("[0]), ").append(arrayName)
                    .append(");\n");

            cppOutput.append(String.format("          env->CallVoidMethod(instance, env->GetMethodID(loaderClass, \"appendResource\", \"([BLjava/lang/String;)V\"), %s, env->NewStringUTF(\"%s\"));\n\n",
                    bufferName, resource.name));
        }

        String mainClass;
        if(customEntryPoint != null){
            mainClass = customEntryPoint;
        }else mainClass = manifest.getMainAttributes().getValue("Main-Class");

        /*
            That's why I used own class loader.
            Here we call findClass method from our classloader instance.
            For first, classloader checks are same class was loaded?
            If not, then it iterates over each of all appended classes and define it.
         */

        cppOutput.append(String.format("          jobject klass = env->CallObjectMethod(instance, env->GetMethodID(loaderClass, \"findClass\", \"(Ljava/lang/String;)Ljava/lang/Class;\"), env->NewStringUTF(\"%s\"));\n",
                mainClass));
        cppOutput.append(String.format("          env->CallVoidMethod(instance, env->GetMethodID(loaderClass, \"callEntryPoint\", \"(Ljava/lang/Class;)V\"), klass); // Main class from %s\n",
                customEntryPoint == null ? "manifest file" : "custom entry point"));

        cppOutput.append("      }\n");
        cppOutput.append("  }\n");
        cppOutput.append("}");

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
