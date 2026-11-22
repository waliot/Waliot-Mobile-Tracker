package com.websmithing.gpstracker2.ui.features.home.components

internal enum class MapLoadStatus { Loading, Ready, Failed }

internal data class MapLoadState(
    val attempt: Int = 0,
    val status: MapLoadStatus = MapLoadStatus.Loading,
    private val hasResourceError: Boolean = false,
) {
    fun failed(): MapLoadState =
        if (status == MapLoadStatus.Loading) copy(status = MapLoadStatus.Failed) else this

    fun resourceFailed(): MapLoadState =
        if (status == MapLoadStatus.Ready) this else copy(status = MapLoadStatus.Failed, hasResourceError = true)

    fun finished(): MapLoadState =
        if (hasResourceError) this else copy(status = MapLoadStatus.Ready)

    fun retry(): MapLoadState = MapLoadState(attempt = attempt + 1)
}

internal fun mapStyleUrlForAttempt(styleUrl: String, attempt: Int): String {
    // Asset URLs are file names, so a retry query would make the file unreadable.
    // ObservedMap uses the attempt itself to reload even when this URI is unchanged.
    if (attempt == 0 || styleUrl.startsWith("asset://")) return styleUrl
    val base = styleUrl.substringBefore('#')
    val fragment = styleUrl.substringAfter('#', missingDelimiterValue = "")
    val separator = when {
        '?' !in base -> "?"
        base.endsWith('?') || base.endsWith('&') -> ""
        else -> "&"
    }
    // Append before the fragment without decoding/re-encoding existing path or query values.
    return base + separator + "_waliot_retry=$attempt" +
        if ('#' in styleUrl) "#$fragment" else ""
}
