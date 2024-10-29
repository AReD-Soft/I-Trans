@echo off
echo "Compile resource class"
javac -d bin -sourcepath src src/TranslatorApp.java
echo "Compile .jar file"
jar cvfm I-Trans.jar MANIFEST.MF -C bin . -C res .
echo "Done"
pause