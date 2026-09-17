package org.openardf.radiooracle.shared.files

/** Presentation metadata shared by desktop and Android; actual CSV headers stay in the serializers. */
data class CsvFormatGuide(
    val id: String,
    val title: String,
    val columns: List<String>,
    val importable: Boolean = false,
    val requiredColumns: Set<String> = emptySet(),
    val notes: List<String> = emptyList(),
    val alternatives: List<CsvFormatGuide> = emptyList(),
    val delimiter: Char = ',',
    val includesHeader: Boolean = true
) {
    val headerRow: String get() = CsvCodec.row(columns, delimiter)
    val exampleRow: String get() = CsvCodec.row(columns.map { column -> when {
        id == "readouts" && column == "control_count" -> ((columns.size - 5) / 2).toString()
        id == "readouts" && column == "start_time" -> "10:00:00"
        id == "robis" && column == "start_time" -> "00:00:00"
        else -> CsvFormatGuides.example(column)
    } }, delimiter)
    fun description(column: String): String = when {
        id == "readouts" && column == "start_time" -> "Formatted SI start time; not a relative start-list time."
        id == "robis" && column == "start_time" -> "Time relative to race start in hours:minutes:seconds."
        id.startsWith("starts-") && column == "start_time" -> "Required time relative to race start in minutes:seconds."
        else -> CsvFormatGuides.description(column)
    }
    // Templates intentionally contain no fictional competitor/control rows to import accidentally.
    val template: String get() = if (includesHeader) "$headerRow\r\n" else ""
    val organization: String get() = if (delimiter == ',')
        "UTF-8 CSV with commas between fields. Quote fields containing commas, double quotes or line breaks; double any quotes inside a quoted field."
    else "Compatibility CSV with semicolons between fields. Keep this consumer-specific format unchanged."
    val orderRule: String get() = if (importable)
        "Keep columns in this exact order. Leave empty optional fields in place; headers do not map or reorder columns."
    else "Columns are written in the order shown below."
}

object CsvFormatGuides {
    fun categories(importable: Boolean = false, includeEncryptedIdealOrder: Boolean = false) = CsvFormatGuide(
        "categories", "Categories CSV", EventCsvFormat.Category.columns(includeEncryptedIdealOrder), importable,
        setOf("category", "max_age"), listOf(
            "Use follows_race_presets=1. Older layouts with another value require race_type, time_limit_min and race_band. Race File settings remain authoritative.",
            "Controls are a comma-separated list inside one quoted field, for example \"31,32\". Control locations are not included.",
            "Older semicolon-delimited files and supported headerless files remain accepted."
        )
    )

    fun competitors(importable: Boolean = false) = CsvFormatGuide(
        "competitors", "Competitors CSV", EventCsvFormat.Competitor.HEADER, importable,
        setOf("first_name", "last_name"), listOf(
            "gender uses 0 for male and 1 for female. Start times are minutes:seconds relative to the race start.",
            "The first six columns must be present. Later columns may be omitted in supported headerless layouts; the recommended template includes every column.",
            "Older semicolon-delimited files and recognized legacy headers remain accepted."
        )
    )

    fun starts(importable: Boolean = false): CsvFormatGuide {
        if (!importable) return CsvFormatGuide("starts", "Starts CSV", EventCsvFormat.CompetitorStart.HEADER,
            notes = listOf("Times are minutes:seconds relative to the race start. Category and minute exports use the same columns with different row sorting."))
        val compact = listOf("start_number", "start_time", "si_number")
        return CsvFormatGuide("starts-import", "Starts CSV import", compact, true,
            setOf("start_number", "start_time"), listOf(
                "Times are minutes:seconds relative to the race start. Identify competitors using their existing SI number, bib number (full layout), or an unambiguous start number.",
                "Older semicolon-delimited files and supported headerless files remain accepted."
            ), listOf(
                CsvFormatGuide("starts-corridor", "Compact layout with corridor", compact + "corridor", true, setOf("start_number", "start_time")),
                CsvFormatGuide("starts-full", "Full exported layout", EventCsvFormat.CompetitorStart.HEADER, true, setOf("start_number", "start_time"),
                    notes = listOf("The ten-column exported layout without corridor is also accepted."))
            ))
    }

    fun controls(importable: Boolean = false) = CsvFormatGuide(
        "controls", "Controls CSV", EventCsvFormat.Control.HEADER, importable,
        setOf("si_code", "role"), listOf(
            "role is Control (or Fox), Beacon, or Spectator. fox uses 1 for a scored fox and 0 otherwise; true/false and yes/no are also accepted.",
            "public_label and notes may be blank. This CSV does not contain latitude/longitude or update control locations.",
            "Older semicolon-delimited files and the recognized legacy mandatory header remain accepted."
        )
    )

    fun readouts(punchColumnCount: Int) = CsvFormatGuide("readouts", "Readouts CSV",
        EventCsvRows.readoutColumns(punchColumnCount), notes = listOf(
            "One row per SI-card readout. Control code/time pairs repeat up to the largest control-punch count in the current data; shorter rows are padded with empty pairs.",
            "Times are formatted SI readout times. Android displays clock times."
        ))

    fun results(hasAwards: Boolean, hasRouteLengths: Boolean) = CsvFormatGuide("results", "Results CSV",
        EventCsvExports.resultColumns(hasAwards, hasRouteLengths), notes = listOf(
            "One row per result. Award columns appear when awards are present; route-analysis columns appear when route comparisons are available.",
            "Run time is elapsed time. Route lengths are in meters."
        ))

    fun splits(hasRouteLengths: Boolean = false) = CsvFormatGuide("splits", "Split Results CSV",
        SplitResultExports.csvColumns(hasRouteLengths), notes = listOf(
            "One row per split; a result without splits still has one row with empty split fields.",
            "Leg and cumulative times include formatted elapsed times and numeric seconds. Leg place compares identical directed legs."
        ))

    fun courseReport(columns: List<String>) = CsvFormatGuide("course-report", "Course Report CSV", columns,
        notes = listOf("One row per unique set of SI controls, numbered longest-first. km is horizontal distance in kilometers; m is climb in meters. C1…Cn list SI codes least-to-greatest, with empty padding for shorter courses."))

    fun ardfEventResults() = CsvFormatGuide("ardfevent", "ARDFEvent Results CSV", EventCsvExports.ARDF_EVENT_COLUMNS,
        delimiter = ';', notes = listOf("ARDFEvent compatibility export with its required column names and semicolon separators."))

    fun robisStarts() = CsvFormatGuide("robis", "ROBIS Start List CSV", listOf(
        "reserved_1", "last_name", "first_name", "category", "reserved_2", "start_time", "person_id", "reserved_3", "country", "si_number"),
        delimiter = ';', includesHeader = false, notes = listOf(
            "This ROBIS file has no header row. The names shown describe column positions only; do not insert them into the export.",
            "Reserved columns are empty. country is CZE as required by the existing compatibility export. Start time is hours:minutes:seconds."
        ))

    fun description(column: String): String = descriptions[column] ?: when {
        column.matches(Regex("C\\d+")) -> "SI control code; blank padding for a shorter course."
        column.matches(Regex("control_\\d+_code")) -> "SI code of this control punch."
        column.matches(Regex("control_\\d+_time")) -> "Formatted SI time of this control punch."
        column.startsWith("reserved") -> "Reserved column; leave blank."
        else -> "Exported $column value."
    }

    fun example(column: String): String = examples[column] ?: when {
        column.matches(Regex("C\\d+")) -> (30 + column.drop(1).toInt()).toString()
        column.matches(Regex("control_\\d+_code")) -> "31"
        column.matches(Regex("control_\\d+_time")) -> "10:05:00"
        else -> ""
    }

    private val descriptions = mapOf(
        "category" to "Category name, for example M21.", "is_man" to "Male category flag: 1 male, 0 female; standard category names determine this when recognized.",
        "max_age" to "Maximum age; 99 represents an open age limit.", "length_m" to "Horizontal course length in meters, a nonnegative integer; blank means 0.",
        "climb_m" to "Course climb in meters, a nonnegative integer; blank means 0.", "follows_race_presets" to "1 uses race settings; otherwise the following three settings are required.",
        "race_type" to "Race type name or display label, such as CLASSIC, SPRINT or FOXORING.", "time_limit_min" to "Time limit in whole minutes when not using race presets.",
        "race_band" to "Race band name or display label from the race settings.", "control_count" to "Number of assigned controls or control punches, as appropriate to this format.",
        "controls" to "Control list in a single field; quote comma-separated lists. Special-role syntax follows the category control-list format.",
        "encrypted_ideal_order" to "Existing encrypted course-order data; this optional export column is not a plain-text order.",
        "si_number" to "SI-card number; blank when not assigned. Use the existing card number when importing starts.",
        "start_number" to "Start-list number (time slot), distinct from a bib number; required for starts import.",
        "first_name" to "Competitor’s first name.", "last_name" to "Competitor’s last name.", "gender" to "0 male, 1 female.",
        "birth_year" to "Four-digit birth year, or blank.", "club" to "Club name, or blank.", "person_id" to "IOF Person ID, or blank; not a row index.",
        "start_time" to "Time relative to race start in minutes:seconds for standard starts and competitor CSV; blank if not assigned.",
        "si_rent" to "1 for a rented SI card, 0 otherwise.", "preferred_start_group" to "Preferred start group: 1, 2 or 3, or blank.",
        "bib_number" to "Competitor bib number, or blank; separate from the start-list number.", "call_sign" to "Call sign, or blank.",
        "email" to "Email address, or blank.", "cell_phone" to "Cell phone number, or blank.",
        "national_champ_eligible" to "1 if eligible for national championship awards, 0 otherwise.",
        "regional_champ_eligible" to "1 if eligible for regional championship awards, 0 otherwise.", "corridor" to "Start corridor, or blank.",
        "si_code" to "SPORTident control station code.", "role" to "Control (or Fox), Beacon, or Spectator.",
        "fox" to "Scoring flag: 1 scored fox, 0 otherwise. Blank uses the role’s default.", "public_label" to "Visible control label, or blank.",
        "notes" to "Control notes, or blank; quote text containing commas.", "check_time" to "Formatted SI check time.", "finish_time" to "Formatted SI finish time.",
        "km" to "Horizontal course length in kilometers.", "m" to "Course climb in meters.", "Course" to "Unique control-set course number.",
        "Place" to "Rank or placing text; may be blank for an unranked result.", "Competitor" to "Competitor display name.",
        "Status" to "Result status, such as OK, DNS or DNF.", "Points" to "Result score.", "Run time" to "Formatted elapsed race time.",
        "USA award" to "National championship award text.", "Region 2 award" to "Regional championship award text.",
        "Race" to "Race name.", "Start" to "Race start date/time.", "Category" to "Category name.", "Bib" to "Competitor bib number.",
        "Club" to "Club name.", "Person ID" to "IOF Person ID.", "SI" to "SI-card number.", "Total Time" to "Formatted total elapsed time.",
        "Transmitters" to "Number of credited transmitters.", "Split #" to "Split sequence number.", "From" to "Previous point label.",
        "Control" to "Visited point label.", "SI Code" to "Visited control station code.", "Punch Status" to "Classification of the punch in the result.",
        "Leg Time" to "Formatted elapsed time since the previous point.", "Cumulative Time" to "Formatted elapsed time from the start.",
        "Leg Place" to "Rank among results that traversed the same leg in the same direction.",
        "Kategorie" to "Category name.", "Pořadí" to "Place.", "Jméno" to "Competitor display name.", "Čas" to "Formatted elapsed race time.",
        "TX" to "Result points / transmitter score.", "Kontroly" to "Visited SI control codes in punch order, separated by spaces.",
        "Leg Seconds" to "Numeric seconds since the previous point.", "Cumulative Seconds" to "Numeric elapsed seconds from the start.",
        "Estimated effective route length (m)" to "Estimated effective route length in meters.",
        "Analysis ideal effective length (m)" to "Course analysis ideal effective route length in meters.",
        "Route comparison" to "Comparison with the analysis ideal route.", "country" to "Compatibility country value CZE."
    )
    private val examples = mapOf(
        "category" to "M21", "is_man" to "1", "max_age" to "99", "length_m" to "4000", "climb_m" to "100",
        "follows_race_presets" to "1", "control_count" to "2", "controls" to "31,32", "si_number" to "100001", "start_number" to "1",
        "first_name" to "Alex", "last_name" to "Taylor", "gender" to "0", "birth_year" to "1990", "club" to "Example Club",
        "start_time" to "0:00", "si_rent" to "0", "bib_number" to "101", "national_champ_eligible" to "1", "regional_champ_eligible" to "1",
        "si_code" to "31", "role" to "Control", "fox" to "1", "public_label" to "Fox 1", "notes" to "Near path, north side",
        "check_time" to "09:55:00", "finish_time" to "10:45:00", "Course" to "1", "km" to "4", "m" to "100", "country" to "CZE",
        "Kategorie" to "M21", "Pořadí" to "1", "Jméno" to "Alex Taylor", "Čas" to "0:45:00", "TX" to "5", "Kontroly" to "31 32",
        "Place" to "1", "Competitor" to "Alex Taylor", "Status" to "OK", "Points" to "5", "Run time" to "0:45:00",
        "Race" to "Example race", "Category" to "M21", "SI" to "100001", "Bib" to "101", "Total Time" to "0:45:00",
        "Split #" to "1", "From" to "S", "Control" to "Fox 1", "SI Code" to "31", "Leg Time" to "0:05:00", "Leg Seconds" to "300",
        "Cumulative Time" to "0:05:00", "Cumulative Seconds" to "300", "Leg Place" to "1"
    )
}
