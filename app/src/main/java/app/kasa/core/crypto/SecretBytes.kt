package app.kasa.core.crypto

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bellekte tutulan gizli baytlar için silinebilir sarmalayıcı.
 *
 * `String` kullanılmaz: Java'da `String` değişmezdir, çöp toplayıcı onu ne zaman
 * temizleyeceğini söylemez ve bellek dökümünde ana parola saatlerce durabilir.
 * Bu sınıf, işi biten anahtarı [wipe] ile sıfırlar ve bir daha okunmasını engeller.
 */
class SecretBytes(private val bytes: ByteArray) : AutoCloseable {

    private val wiped = AtomicBoolean(false)

    val size: Int get() = bytes.size

    val isWiped: Boolean get() = wiped.get()

    /** İçeriğe doğrudan erişim. Kopya çıkarmaz; çağıran kopyalamamalıdır. */
    fun raw(): ByteArray {
        check(!wiped.get()) { "Silinmiş gizli veriye erişildi" }
        return bytes
    }

    /** Bağımsız bir kopya döndürür; çağıran kendi kopyasını silmekle yükümlüdür. */
    fun copy(): SecretBytes {
        check(!wiped.get()) { "Silinmiş gizli veriye erişildi" }
        return SecretBytes(bytes.copyOf())
    }

    /** Baytları sıfırla. Çağrıdan sonra nesne kullanılamaz. */
    fun wipe() {
        if (wiped.compareAndSet(false, true)) bytes.fill(0)
    }

    override fun close() = wipe()

    override fun toString(): String = "SecretBytes(${bytes.size} bayt, silindi=${wiped.get()})"

    override fun equals(other: Any?): Boolean {
        if (other !is SecretBytes) return false
        if (wiped.get() || other.wiped.get()) return false
        return Crypto.constantTimeEquals(bytes, other.bytes)
    }

    override fun hashCode(): Int = size

    companion object {
        fun ofUtf8(text: CharArray): SecretBytes {
            val bytes = Crypto.charsToUtf8(text)
            return SecretBytes(bytes)
        }

        fun random(size: Int): SecretBytes = SecretBytes(Crypto.randomBytes(size))
    }
}
