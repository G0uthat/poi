# Conserva los números de línea en los errores (útil para depurar).
-keepattributes SourceFile,LineNumberTable

# WorkManager crea el trabajo por reflexión a partir del nombre de la clase.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
