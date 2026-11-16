package com.plan.ingsoft

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Bloqueo de edición: se abre con la contraseña o resolviendo un puzzle (10 min). */
object Lock {
    private var until = 0L

    private const val ITERACIONES = 120_000
    private const val MAX_FALLOS = 5

    fun isUnlocked(): Boolean = System.currentTimeMillis() < until
    fun unlock(minutes: Int = 10) {
        until = System.currentTimeMillis() + minutes * 60_000L
    }

    fun lockNow() {
        until = 0L
    }

    fun hasPassword(st: Store): Boolean = st.pwHash() != null

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    /** Formato antiguo (versión 1), solo para reconocer contraseñas guardadas antes. */
    private fun hashV1(salt: String, pw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return b64(md.digest((salt + pw).toByteArray(Charsets.UTF_8)))
    }

    private fun hashV2(salt: String, pw: String): String {
        val spec = PBEKeySpec(pw.toCharArray(), Base64.getDecoder().decode(salt), ITERACIONES, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return b64(f.generateSecret(spec).encoded)
    }

    fun setPassword(st: Store, pw: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = b64(saltBytes)
        st.savePassword(salt, hashV2(salt, pw), 2)
        st.pwFallos = 0
        st.pwBloqueadoHasta = 0L
    }

    /** Segundos que faltan para poder volver a intentarlo (0 = ya se puede). */
    fun segundosEspera(st: Store): Int {
        val resta = st.pwBloqueadoHasta - System.currentTimeMillis()
        return if (resta > 0) ((resta + 999) / 1000).toInt() else 0
    }

    private fun igual(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun check(st: Store, pw: String): Boolean {
        if (segundosEspera(st) > 0) return false
        val salt = st.pwSalt() ?: return false
        val h = st.pwHash() ?: return false
        val ok = if (st.pwVersion() >= 2) igual(hashV2(salt, pw), h) else igual(hashV1(salt, pw), h)
        if (ok) {
            st.pwFallos = 0
            st.pwBloqueadoHasta = 0L
            // Contraseña guardada con el formato antiguo: se mejora sin que tengas que hacer nada.
            if (st.pwVersion() < 2) setPassword(st, pw)
            return true
        }
        val fallos = st.pwFallos + 1
        st.pwFallos = fallos
        if (fallos >= MAX_FALLOS) {
            // 30 s, 60 s, 120 s... hasta 15 min.
            val extra = (fallos - MAX_FALLOS).coerceAtMost(5)
            val espera = (30_000L shl extra).coerceAtMost(15 * 60_000L)
            st.pwBloqueadoHasta = System.currentTimeMillis() + espera
        }
        return false
    }
}
