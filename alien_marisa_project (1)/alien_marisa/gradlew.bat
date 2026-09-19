@rem Gradle 启动脚本（Windows）
@rem 缺少 gradle-wrapper.jar 时请先执行: gradle wrapper --gradle-version 8.8
@if "%DEBUG%"=="" @echo off
setlocal
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
    echo Missing %CLASSPATH%
    echo Run: gradle wrapper --gradle-version 8.8
    exit /b 1
)
if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java
)
"%JAVA_EXE%" -Xmx3G -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
