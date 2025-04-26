package io.github.firstred.iptvproxy.exceptions

class UndefinedEnumSerializerException(clazz: String, value: String) : Exception("Unknown value `$value` on enum `$clazz`")
