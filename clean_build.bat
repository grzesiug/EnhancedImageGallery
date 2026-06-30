@echo off
set JAVA_HOME=C:\Program Files\Autopsy-4.23.1\jre
"C:\Program Files\NetBeans-17\netbeans\extide\ant\bin\ant.bat" -f "%~dp0" clean
"C:\Program Files\NetBeans-17\netbeans\extide\ant\bin\ant.bat" -f "%~dp0" -Dcontinue.after.failing.tests=true run
