# ⭐ Jar2DLL
Easy to use Jar to DLL converter (not a direct Java to C++ translator)
<br>
# Features
 - Auto compilation
 - Fast
 - Supports all Java versions
 - Resource support
 - GUI
# Requirements
You need to have few pre-installed things to use Jar2DLL:
 - CMake
 - Visual Studio >2019 (Thanks for microshit, we can't simply install MSVC compiler without VS)
 # How to use
 You can convert your JAR into DLL by 1 step:
 
 ```java -jar Jar2DLL.jar --input input.jar --output output.dll```
 
 There also few parameters:
  - --cmake - Accepts full CMake path (C:/Program Files/CMake)
  - --entryPoint - Accepts name of main class with main method (Yes, your DLL will start from `main(String[])` method)
  - --saveSource - Jar2DLL will save auto-generated C++ code
  
# Explanation
Well, Jar2DLL is **NOT** Java to C++ translator. It works more easily.
It allocates all your Java classes to arrays in a C++ source file, then does magic with [JNI](https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/jniTOC.html) and defines your classes. 
If you were wondering how this works at a low level, you can check out how Jar2DLL generates a C++ source file.

# Example of working
To show how Jar2DLL works, i made a little Minecraft hack that uses Reflection

(In video im running Vanilla 1.12.2 Minecraft, not Forge)

```java
Class<?> minecraftClass = Class.forName("bhz"); // Searching for net/minecraft/client/Minecraft class

Field theMinecraft = minecraftClass.getDeclaredField("R"); // theMinecraft field
theMinecraft.setAccessible(true);

Object mcInstance = theMinecraft.get(null);
Field thePlayer = minecraftClass.getDeclaredField("h");
thePlayer.setAccessible(true);
Object player = thePlayer.get(mcInstance); // thePlayer instance
if(player != null){
  if(!isMessageSent){
    Method method = player.getClass().getDeclaredMethod("g", String.class);
    method.invoke(player, "Hello from Jar2DLL");
    isMessageSent = true;
  }
  Field onGround = player.getClass().getField("z"); // onGround field
  onGround.set(player, true);
}
```

[![Example](https://yt-embed.herokuapp.com/embed?v=Np5UBqoP2yY)](https://www.youtube.com/watch?v=Np5UBqoP2yY "Example")
