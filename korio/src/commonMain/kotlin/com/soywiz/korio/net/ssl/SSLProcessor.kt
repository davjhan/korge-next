package com.soywiz.korio.net.ssl

import com.soywiz.korio.lang.*
import com.soywiz.korio.net.*

expect fun DefaultSSLProcessor(): SSLProcessor

interface SSLProcessor {
    val isAlive: Boolean
    val needInput: Boolean

    fun setEndPoint(host: String, port: Int)

    fun addEncryptedServerData(data: ByteArray, offset: Int, size: Int)
    fun addDecryptedClientData(data: ByteArray, offset: Int, size: Int)

    fun getDecryptedServerData(data: ByteArray, offset: Int = 0, size: Int = data.size - offset): Int
    fun getEncryptedClientData(data: ByteArray, offset: Int = 0, size: Int = data.size - offset): Int

    fun clientClose()
}

fun SSLProcessor.getDecryptedServerData(): ByteArray? {
    val out = ByteArray(1024)
    val size = getDecryptedServerData(out)
    return if (size > 0) out.copyOf(size) else null
}
fun SSLProcessor.getEncryptedClientData(): ByteArray? {
    val out = ByteArray(1024)
    val size = getEncryptedClientData(out)
    return if (size > 0) out.copyOf(size) else null
}

fun SSLProcessor.getAllDecryptedServerData(): List<ByteArray> {
    val out = arrayListOf<ByteArray>()
    while (true) {
        val data = getDecryptedServerData() ?: break
        out.add(data)
    }
    return out
}
fun SSLProcessor.getAllEncryptedClientData(): List<ByteArray> {
    val out = arrayListOf<ByteArray>()
    while (true) {
        val data = getEncryptedClientData() ?: break
        out.add(data)
    }
    return out
}

class AsyncClientSSLProcessor(val client: AsyncClient, val processor: SSLProcessor = DefaultSSLProcessor()) : AsyncClient {
    override suspend fun connect(host: String, port: Int) {
        processor.setEndPoint(host, port)
        client.connect(host, port)
        //println("HANDSHAKE!")
        while (processor.needInput) {
            //println("DO SYNC")
            sync()
            if (processor.needInput) readData()
        }
        //println("/HANDSHAKE!")
    }

    private suspend fun sync() {
        for (chunk in processor.getAllEncryptedClientData()) {
            client.write(chunk)
        }
    }

    override val connected: Boolean get() = client.connected
    private val temp = ByteArray(32 * 1024)

    private suspend fun readData(): Int {
        //println("READ SOCKET DATA")
        val readCount = client.read(temp, 0, temp.size)
        //println("READ SOCKET DATA: readCount=$readCount")
        //if (readCount < 0) throw EOFException("end of socket")
        if (readCount > 0) processor.addEncryptedServerData(temp, 0, readCount)
        return readCount
    }

    override suspend fun read(buffer: ByteArray, offset: Int, len: Int): Int {
        while (true) {
            sync()
            val out = processor.getDecryptedServerData(buffer, offset, len)
            if (out == 0) {
                if (readData() < 0) return -1
            } else {
                return out
            }
        }
    }

    override suspend fun write(buffer: ByteArray, offset: Int, len: Int) {
        processor.addDecryptedClientData(buffer, offset, len)
        sync()
    }

    override suspend fun close() {
        return client.close()
    }
}
