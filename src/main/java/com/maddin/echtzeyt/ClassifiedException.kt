package com.maddin.echtzeyt

data class ClassifiedException(val exception: Exception, val classified: Boolean, val classifiedAs: String) {
    constructor(exception: Exception, classified: Boolean) : this(exception, classified, "")
    constructor(exception: Exception, classifiedAs: String) : this(exception, classifiedAs.isNotEmpty(), classifiedAs)

    override fun toString() : String {
        return "${if (classified) { "Classified" } else { "Unclassified" }} exception${if (classified) { " (classified as \"${classifiedAs}\"" } else { "" }}: \n${exception.stackTraceToString()}"
    }
}
