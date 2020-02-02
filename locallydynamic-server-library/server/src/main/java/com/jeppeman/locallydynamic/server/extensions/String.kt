package com.jeppeman.locallydynamic.server.extensions

import java.util.*

fun String.toBase64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))