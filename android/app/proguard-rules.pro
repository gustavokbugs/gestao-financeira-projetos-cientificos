# DTOs são preenchidos pelo Gson via reflexão: manter nomes de campos.
-keep class br.gestaofinanceira.app.data.remote.dto.** { *; }

# Retrofit (interfaces e assinaturas genéricas)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
