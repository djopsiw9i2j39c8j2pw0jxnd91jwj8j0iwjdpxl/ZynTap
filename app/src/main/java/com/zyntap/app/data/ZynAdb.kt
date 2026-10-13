package com.zyntap.app.data

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * Quản lý khoá + kết nối ADB của ZynTap (dựa trên libadb-android).
 * Cặp khoá RSA được tạo một lần rồi lưu trong bộ nhớ riêng của app, nên sau khi
 * ghép nối xong, các lần kết nối sau không cần ghép nối lại.
 */
class ZynAdb private constructor(context: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(context.filesDir, "adb_private.key")
        val certFile = File(context.filesDir, "adb_cert.der")
        var key: PrivateKey? = null
        var cert: Certificate? = null
        if (keyFile.exists() && certFile.exists()) {
            runCatching {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            }.onFailure { key = null; cert = null }
        }
        if (key == null || cert == null) {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048, SecureRandom())
            val pair = gen.generateKeyPair()
            val priv = pair.private
            val algorithm = "SHA512withRSA"
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)
            val ext = CertificateExtensions()
            ext.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
            val name = X500Name("CN=ZynTap")
            ext.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(pair.public))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", ext)
            val x509 = X509CertImpl(info)
            x509.sign(priv, algorithm)
            keyFile.writeBytes(priv.encoded)
            certFile.writeBytes(x509.encoded)
            key = priv
            cert = x509
        }
        privateKey = key!!
        certificate = cert!!
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "ZynTap"

    companion object {
        @Volatile private var instance: ZynAdb? = null

        fun manager(context: Context): ZynAdb =
            instance ?: synchronized(this) {
                instance ?: ZynAdb(context.applicationContext).also { instance = it }
            }
    }
}
