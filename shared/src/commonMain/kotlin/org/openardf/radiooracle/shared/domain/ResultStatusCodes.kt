package org.openardf.radiooracle.shared.domain

/** Stable result-status codes used in portable files and compact print displays. */
fun ResultStatus.toResultStatusCode(): String =
    when (this) {
        ResultStatus.OK -> "OK"
        ResultStatus.MISPUNCHED -> "MP"
        ResultStatus.NO_RANKING -> "NR"
        ResultStatus.DISQUALIFIED -> "DSQ"
        ResultStatus.DID_NOT_START -> "DNS"
        ResultStatus.DID_NOT_FINISH -> "DNF"
        ResultStatus.OVER_TIME_LIMIT -> "OVT"
        ResultStatus.UNOFFICIAL -> "UNF"
        ResultStatus.ERROR -> "ERR"
    }

fun resultStatusFromCode(code: String?, blankAsOk: Boolean = false): ResultStatus {
    val trimmed = code?.trim()
    if (trimmed == "" && blankAsOk) {
        return ResultStatus.OK
    }
    return when (trimmed) {
        "OK" -> ResultStatus.OK
        "MP", "Mispunched" -> ResultStatus.MISPUNCHED
        "NR", "No ranking" -> ResultStatus.NO_RANKING
        "DSQ", "Disqualified" -> ResultStatus.DISQUALIFIED
        "DNS", "Did not start" -> ResultStatus.DID_NOT_START
        "DNF", "Did not finish" -> ResultStatus.DID_NOT_FINISH
        "OVT", "Over time limit" -> ResultStatus.OVER_TIME_LIMIT
        "UNF", "Unofficial" -> ResultStatus.UNOFFICIAL
        "ERR", "Error" -> ResultStatus.ERROR
        else -> ResultStatus.NO_RANKING
    }
}
