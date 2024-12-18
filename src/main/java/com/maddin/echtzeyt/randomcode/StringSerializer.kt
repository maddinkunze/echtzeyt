package com.maddin.echtzeyt.randomcode

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Base64


object StringSerializer {
    private val decoder = Base64.getDecoder()
    private val encoder = Base64.getEncoder()

    fun fromString(s: String): Any {
        val data: ByteArray = decoder.decode(s)
        val ois = ObjectInputStream(
            ByteArrayInputStream(data)
        )
        val o = ois.readObject()
        ois.close()
        return o
    }

    fun toString(o: Serializable): String {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(o)
        oos.close()
        return encoder.encodeToString(baos.toByteArray())
    }
}

fun Serializable.serializeToString() = StringSerializer.toString(this)

fun String.deserialize() = StringSerializer.fromString(this)
inline fun <reified T> String.deserializeTo() = deserialize() as? T