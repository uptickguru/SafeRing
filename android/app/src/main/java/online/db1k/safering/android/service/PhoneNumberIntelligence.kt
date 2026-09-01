package online.db1k.safering.android.service

/**
 * Offline phone number intelligence — country, carrier, line type, region.
 * Uses a built-in prefix trie for O(digit_count) lookups with zero network calls.
 * Thread-safe (immutable trie after init). Singleton.
 */
object PhoneNumberIntelligence {

    enum class LineType {
        MOBILE, LANDLINE, VOIP, TOLL_FREE, PREMIUM, UNKNOWN;

        fun displayName(): String = when (this) {
            MOBILE -> "Mobile"
            LANDLINE -> "Landline"
            VOIP -> "VoIP"
            TOLL_FREE -> "Toll-Free"
            PREMIUM -> "Premium"
            UNKNOWN -> ""
        }
    }

    data class Result(
        val country: String,
        val countryName: String,
        val region: String? = null,
        val city: String? = null,
        val carrier: String? = null,
        val lineType: LineType = LineType.UNKNOWN,
        val isValid: Boolean = false
    ) {
        /** Country flag emoji from ISO 3166-1 alpha-2 code using regional indicator symbols. */
        val flagEmoji: String
            get() {
                if (country.length != 2) return "🏳️"
                val base = 0x1F1E6 - 'A'.code
                val first = Character.toChars(base + country[0].uppercaseChar().code)
                val second = Character.toChars(base + country[1].uppercaseChar().code)
                return String(first) + String(second)
            }

        /** Full display: "📍 Lagos, Nigeria · MTN · Mobile" */
        val displayString: String
            get() = buildString {
                append(flagEmoji)
                append(' ')
                city?.let { append(it).append(", ") }
                region?.let {
                    if (city == null) append(it).append(", ")
                }
                append(countryName)
                carrier?.let { append(" · ").append(it) }
                val lt = lineType.displayName()
                if (lt.isNotEmpty()) append(" · ").append(lt)
            }

        /** Short display: "🇳🇬 Nigeria · VoIP" */
        val shortDisplay: String
            get() = buildString {
                append(flagEmoji).append(' ').append(countryName)
                val lt = lineType.displayName()
                if (lt.isNotEmpty()) append(" · ").append(lt)
            }

        companion object {
            val UNKNOWN = Result(
                country = "??",
                countryName = "Unknown",
                isValid = false
            )
        }
    }

    // ── Trie data structure ──────────────────────────────────────────────

    private sealed class TrieNode {
        data class Branch(
            val children: Map<Char, TrieNode>
        ) : TrieNode()

        data class Leaf(
            val country: String,
            val countryName: String,
            val region: String?,
            val city: String?,
            val carrier: String?,
            val lineType: LineType
        ) : TrieNode()
    }

    @Volatile
    private var root: TrieNode.Branch? = null

    /** Initialize the trie. Call once at app startup. */
    internal fun init() {
        root = buildTrie(loadPrefixData())
    }

    /** Lookup intelligence for an E.164 number. <1ms, no network. */
    fun lookup(e164: String): Result {
        val digits = normalizeDigits(e164)
        if (digits.isEmpty()) return Result.UNKNOWN

        val rootNode = root ?: return Result.UNKNOWN

        // Walk the trie greedily, tracking the deepest match
        var best: TrieNode.Leaf? = null
        var current: TrieNode = rootNode

        for (ch in digits) {
            when (val node = current) {
                is TrieNode.Leaf -> break
                is TrieNode.Branch -> {
                    val child = node.children[ch] ?: break
                    if (child is TrieNode.Leaf) {
                        best = child
                        break
                    }
                    current = child
                }
            }
        }

        return best?.let {
            Result(
                country = it.country,
                countryName = it.countryName,
                region = it.region,
                city = it.city,
                carrier = it.carrier,
                lineType = it.lineType,
                isValid = true
            )
        } ?: Result.UNKNOWN
    }

    /** Extract safe prefix (first 5 digits after +) for API calls. */
    fun safePrefix(e164: String): String {
        val digits = normalizeDigits(e164)
        return digits.take(5)
    }

    // ── Known scam-origin countries ──────────────────────────────────────

    /** Countries frequently associated with scam call centers. */
    private val highRiskCountries = setOf(
        "IN", "PK", "BD", "PH", "VN", "KH", "MM"
    )

    fun isHighRiskCountry(isoCode: String): Boolean =
        isoCode.uppercase() in highRiskCountries

    /** Check if a line type is suspicious for unknown callers. */
    fun isSuspiciousLineType(lineType: LineType): Boolean =
        lineType == LineType.VOIP || lineType == LineType.PREMIUM

    // ── Internals ────────────────────────────────────────────────────────

    private fun normalizeDigits(e164: String): String {
        val s = e164.trim()
        return if (s.startsWith("+")) s.substring(1).filter { it.isDigit() }
        else s.filter { it.isDigit() }
    }

    private fun buildTrie(entries: List<PrefixEntry>): TrieNode.Branch {
        val rootChildren = mutableMapOf<Char, MutableMap<String, PrefixEntry>>()
        // Group by first digit for initial split
        for (entry in entries) {
            val prefix = entry.prefix
            if (prefix.isEmpty()) continue
            val firstChar = prefix[0]
            rootChildren.getOrPut(firstChar) { mutableMapOf() }[prefix] = entry
        }

        val children = mutableMapOf<Char, TrieNode>()
        for ((firstChar, group) in rootChildren) {
            children[firstChar] = buildSubTrie(group, firstChar.toString())
        }
        return TrieNode.Branch(children)
    }

    private fun buildSubTrie(entries: Map<String, PrefixEntry>, path: String): TrieNode {
        // Find the longest matching prefix entry for the current path
        val exactMatch = entries[path]
        // Filter entries that extend beyond the current path
        val deeper = entries.filter { (k, _) -> k.length > path.length && k.startsWith(path) }

        if (deeper.isEmpty() && exactMatch != null) {
            return exactMatch.toLeaf()
        }

        if (deeper.isEmpty()) {
            return exactMatch?.toLeaf() ?: TrieNode.Leaf("??", "Unknown", null, null, null, LineType.UNKNOWN)
        }

        // There are deeper entries — build branches
        val nextDigitGroups = mutableMapOf<Char, MutableMap<String, PrefixEntry>>()
        for ((key, entry) in deeper) {
            val nextChar = key[path.length]
            nextDigitGroups.getOrPut(nextChar) { mutableMapOf() }[key] = entry
        }

        val children = mutableMapOf<Char, TrieNode>()
        for ((nextChar, group) in nextDigitGroups) {
            children[nextChar] = buildSubTrie(group, path + nextChar)
        }

        // If there's an exact match at this level, wrap it as a default
        if (exactMatch != null) {
            // Return a branch with a fallback leaf via a special key
            val leafChild = exactMatch.toLeaf()
            children['_'] = leafChild // fallback won't be traversed by digit lookup
        }

        return TrieNode.Branch(children)
    }

    private fun PrefixEntry.toLeaf() = TrieNode.Leaf(
        country = country,
        countryName = countryName,
        region = region,
        city = city,
        carrier = carrier,
        lineType = lineType
    )

    private data class PrefixEntry(
        val prefix: String,
        val country: String,
        val countryName: String,
        val region: String? = null,
        val city: String? = null,
        val carrier: String? = null,
        val lineType: LineType = LineType.UNKNOWN
    )

    // ── Prefix Database ──────────────────────────────────────────────────

    private fun loadPrefixData(): List<PrefixEntry> {
        val entries = mutableListOf<PrefixEntry>()

        // ═══════════════════════════════════════════════════════════════════
        // NANPA (US + Canada) — Country code 1
        // ═══════════════════════════════════════════════════════════════════

        // Toll-free prefixes (NANPA)
        for (code in listOf("800", "888", "877", "866", "855", "844", "833")) {
            entries.add(PrefixEntry(
                prefix = "1$code",
                country = "US",
                countryName = "United States",
                region = "Toll-Free",
                lineType = LineType.TOLL_FREE
            ))
        }

        // Premium rate
        entries.add(PrefixEntry(
            prefix = "1900",
            country = "US",
            countryName = "United States",
            region = "Premium Rate",
            lineType = LineType.PREMIUM
        ))

        // VoIP / Burner prefixes
        val voipPrefixes = listOf(
            "12025551", // Google Voice (202)
            "12025552",
            "1206210",  // Bandwidth (Seattle)
            "1408330",  // Google Voice (408)
            "14155551", // Twilio (SF)
            "14155552",
            "14155553",
            "1510555",  // Google Voice (510)
            "1619555",  // Google Voice (619)
            "1646555",  // TextNow (646)
            "1702555",  // Google Voice (702)
            "1800555",  // Various VoIP (800)
            "1855555",  // Various VoIP (855)
            "1866555",  // Various VoIP (866)
            "1877555",  // Various VoIP (877)
            "1888555",  // Various VoIP (888)
        )
        for (vp in voipPrefixes) {
            entries.add(PrefixEntry(
                prefix = vp,
                country = "US",
                countryName = "United States",
                carrier = "VoIP",
                lineType = LineType.VOIP
            ))
        }

        // US area codes — state mapping
        val usAreaCodes = mapOf(
            "201" to Pair("New Jersey", null),
            "202" to Pair("Washington D.C.", "Washington"),
            "203" to Pair("Connecticut", "Bridgeport"),
            "205" to Pair("Alabama", "Birmingham"),
            "206" to Pair("Washington", "Seattle"),
            "207" to Pair("Maine", null),
            "208" to Pair("Idaho", "Boise"),
            "209" to Pair("California", "Stockton"),
            "210" to Pair("Texas", "San Antonio"),
            "212" to Pair("New York", "Manhattan"),
            "213" to Pair("California", "Los Angeles"),
            "214" to Pair("Texas", "Dallas"),
            "215" to Pair("Pennsylvania", "Philadelphia"),
            "216" to Pair("Ohio", "Cleveland"),
            "217" to Pair("Illinois", "Springfield"),
            "218" to Pair("Minnesota", "Duluth"),
            "219" to Pair("Indiana", "Gary"),
            "220" to Pair("Ohio", "Newark"),
            "224" to Pair("Illinois", "Elgin"),
            "225" to Pair("Louisiana", "Baton Rouge"),
            "228" to Pair("Mississippi", "Gulfport"),
            "229" to Pair("Georgia", "Albany"),
            "231" to Pair("Michigan", "Muskegon"),
            "234" to Pair("Ohio", "Akron"),
            "239" to Pair("Florida", "Fort Myers"),
            "240" to Pair("Maryland", "Germantown"),
            "248" to Pair("Michigan", "Troy"),
            "251" to Pair("Alabama", "Mobile"),
            "252" to Pair("North Carolina", "Greenville"),
            "253" to Pair("Washington", "Tacoma"),
            "254" to Pair("Texas", "Killeen"),
            "256" to Pair("Alabama", "Huntsville"),
            "260" to Pair("Indiana", "Fort Wayne"),
            "262" to Pair("Wisconsin", "Kenosha"),
            "267" to Pair("Pennsylvania", "Philadelphia"),
            "269" to Pair("Michigan", "Kalamazoo"),
            "270" to Pair("Kentucky", "Bowling Green"),
            "272" to Pair("Pennsylvania", "Scranton"),
            "276" to Pair("Virginia", "Bristol"),
            "281" to Pair("Texas", "Houston"),
            "301" to Pair("Maryland", "Silver Spring"),
            "302" to Pair("Delaware", "Wilmington"),
            "303" to Pair("Colorado", "Denver"),
            "304" to Pair("West Virginia", "Charleston"),
            "305" to Pair("Florida", "Miami"),
            "307" to Pair("Wyoming", null),
            "308" to Pair("Nebraska", "Grand Island"),
            "309" to Pair("Illinois", "Peoria"),
            "310" to Pair("California", "Los Angeles"),
            "312" to Pair("Illinois", "Chicago"),
            "313" to Pair("Michigan", "Detroit"),
            "314" to Pair("Missouri", "St. Louis"),
            "315" to Pair("New York", "Syracuse"),
            "316" to Pair("Kansas", "Wichita"),
            "317" to Pair("Indiana", "Indianapolis"),
            "318" to Pair("Louisiana", "Shreveport"),
            "319" to Pair("Iowa", "Cedar Rapids"),
            "320" to Pair("Minnesota", "St. Cloud"),
            "321" to Pair("Florida", "Orlando"),
            "323" to Pair("California", "Los Angeles"),
            "330" to Pair("Ohio", "Akron"),
            "331" to Pair("Illinois", "Aurora"),
            "334" to Pair("Alabama", "Montgomery"),
            "336" to Pair("North Carolina", "Greensboro"),
            "337" to Pair("Louisiana", "Lafayette"),
            "339" to Pair("Massachusetts", "Boston"),
            "346" to Pair("Texas", "Houston"),
            "347" to Pair("New York", "New York City"),
            "351" to Pair("Massachusetts", "Lowell"),
            "352" to Pair("Florida", "Gainesville"),
            "360" to Pair("Washington", "Vancouver"),
            "361" to Pair("Texas", "Corpus Christi"),
            "385" to Pair("Utah", "Salt Lake City"),
            "386" to Pair("Florida", "Daytona Beach"),
            "401" to Pair("Rhode Island", "Providence"),
            "402" to Pair("Nebraska", "Omaha"),
            "404" to Pair("Georgia", "Atlanta"),
            "405" to Pair("Oklahoma", "Oklahoma City"),
            "406" to Pair("Montana", null),
            "407" to Pair("Florida", "Orlando"),
            "408" to Pair("California", "San Jose"),
            "409" to Pair("Texas", "Beaumont"),
            "410" to Pair("Maryland", "Baltimore"),
            "412" to Pair("Pennsylvania", "Pittsburgh"),
            "413" to Pair("Massachusetts", "Springfield"),
            "414" to Pair("Wisconsin", "Milwaukee"),
            "415" to Pair("California", "San Francisco"),
            "417" to Pair("Missouri", "Springfield"),
            "419" to Pair("Ohio", "Toledo"),
            "423" to Pair("Tennessee", "Chattanooga"),
            "424" to Pair("California", "Los Angeles"),
            "425" to Pair("Washington", "Bellevue"),
            "430" to Pair("Texas", "Tyler"),
            "432" to Pair("Texas", "Midland"),
            "434" to Pair("Virginia", "Charlottesville"),
            "435" to Pair("Utah", "St. George"),
            "440" to Pair("Ohio", "Parma"),
            "442" to Pair("California", "Oceanside"),
            "443" to Pair("Maryland", "Baltimore"),
            "458" to Pair("Oregon", "Eugene"),
            "469" to Pair("Texas", "Dallas"),
            "470" to Pair("Georgia", "Atlanta"),
            "475" to Pair("Connecticut", "Waterbury"),
            "478" to Pair("Georgia", "Macon"),
            "479" to Pair("Arkansas", "Fort Smith"),
            "480" to Pair("Arizona", "Scottsdale"),
            "484" to Pair("Pennsylvania", "Allentown"),
            "501" to Pair("Arkansas", "Little Rock"),
            "502" to Pair("Kentucky", "Louisville"),
            "503" to Pair("Oregon", "Portland"),
            "504" to Pair("Louisiana", "New Orleans"),
            "505" to Pair("New Mexico", "Albuquerque"),
            "507" to Pair("Minnesota", "Rochester"),
            "508" to Pair("Massachusetts", "Worcester"),
            "509" to Pair("Washington", "Spokane"),
            "510" to Pair("California", "Oakland"),
            "512" to Pair("Texas", "Austin"),
            "513" to Pair("Ohio", "Cincinnati"),
            "515" to Pair("Iowa", "Des Moines"),
            "516" to Pair("New York", "Hempstead"),
            "517" to Pair("Michigan", "Lansing"),
            "518" to Pair("New York", "Albany"),
            "520" to Pair("Arizona", "Tucson"),
            "530" to Pair("California", "Redding"),
            "531" to Pair("Nebraska", "Omaha"),
            "534" to Pair("Wisconsin", "Eau Claire"),
            "539" to Pair("Oklahoma", "Tulsa"),
            "540" to Pair("Virginia", "Roanoke"),
            "541" to Pair("Oregon", "Eugene"),
            "551" to Pair("New Jersey", "Jersey City"),
            "559" to Pair("California", "Fresno"),
            "561" to Pair("Florida", "West Palm Beach"),
            "562" to Pair("California", "Long Beach"),
            "563" to Pair("Iowa", "Davenport"),
            "567" to Pair("Ohio", "Toledo"),
            "570" to Pair("Pennsylvania", "Scranton"),
            "571" to Pair("Virginia", "Arlington"),
            "573" to Pair("Missouri", "Columbia"),
            "574" to Pair("Indiana", "South Bend"),
            "575" to Pair("New Mexico", "Las Cruces"),
            "580" to Pair("Oklahoma", "Lawton"),
            "585" to Pair("New York", "Rochester"),
            "586" to Pair("Michigan", "Warren"),
            "601" to Pair("Mississippi", "Jackson"),
            "602" to Pair("Arizona", "Phoenix"),
            "603" to Pair("New Hampshire", "Manchester"),
            "605" to Pair("South Dakota", "Sioux Falls"),
            "606" to Pair("Kentucky", "Ashland"),
            "607" to Pair("New York", "Binghamton"),
            "608" to Pair("Wisconsin", "Madison"),
            "609" to Pair("New Jersey", "Trenton"),
            "610" to Pair("Pennsylvania", "Allentown"),
            "612" to Pair("Minnesota", "Minneapolis"),
            "614" to Pair("Ohio", "Columbus"),
            "615" to Pair("Tennessee", "Nashville"),
            "616" to Pair("Michigan", "Grand Rapids"),
            "617" to Pair("Massachusetts", "Boston"),
            "618" to Pair("Illinois", "Belleville"),
            "619" to Pair("California", "San Diego"),
            "620" to Pair("Kansas", "Hutchinson"),
            "623" to Pair("Arizona", "Glendale"),
            "626" to Pair("California", "Pasadena"),
            "628" to Pair("California", "San Francisco"),
            "629" to Pair("Tennessee", "Nashville"),
            "630" to Pair("Illinois", "Naperville"),
            "631" to Pair("New York", "Brentwood"),
            "636" to Pair("Missouri", "O'Fallon"),
            "640" to Pair("New Jersey", "Trenton"),
            "641" to Pair("Iowa", "Mason City"),
            "646" to Pair("New York", "Manhattan"),
            "650" to Pair("California", "Palo Alto"),
            "651" to Pair("Minnesota", "St. Paul"),
            "657" to Pair("California", "Anaheim"),
            "660" to Pair("Missouri", "Sedalia"),
            "661" to Pair("California", "Bakersfield"),
            "662" to Pair("Mississippi", "Tupelo"),
            "667" to Pair("Maryland", "Baltimore"),
            "669" to Pair("California", "San Jose"),
            "678" to Pair("Georgia", "Atlanta"),
            "679" to Pair("Michigan", "Detroit"),
            "680" to Pair("New York", "Syracuse"),
            "681" to Pair("West Virginia", "Charleston"),
            "682" to Pair("Texas", "Fort Worth"),
            "701" to Pair("North Dakota", "Fargo"),
            "702" to Pair("Nevada", "Las Vegas"),
            "703" to Pair("Virginia", "Arlington"),
            "704" to Pair("North Carolina", "Charlotte"),
            "706" to Pair("Georgia", "Augusta"),
            "707" to Pair("California", "Santa Rosa"),
            "708" to Pair("Illinois", "Cicero"),
            "712" to Pair("Iowa", "Sioux City"),
            "713" to Pair("Texas", "Houston"),
            "714" to Pair("California", "Anaheim"),
            "715" to Pair("Wisconsin", "Wausau"),
            "716" to Pair("New York", "Buffalo"),
            "717" to Pair("Pennsylvania", "Lancaster"),
            "718" to Pair("New York", "Brooklyn"),
            "719" to Pair("Colorado", "Colorado Springs"),
            "720" to Pair("Colorado", "Denver"),
            "724" to Pair("Pennsylvania", "New Castle"),
            "725" to Pair("Nevada", "Las Vegas"),
            "727" to Pair("Florida", "St. Petersburg"),
            "731" to Pair("Tennessee", "Jackson"),
            "732" to Pair("New Jersey", "New Brunswick"),
            "734" to Pair("Michigan", "Ann Arbor"),
            "737" to Pair("Texas", "Austin"),
            "740" to Pair("Ohio", "Newark"),
            "743" to Pair("North Carolina", "Greensboro"),
            "747" to Pair("California", "Burbank"),
            "754" to Pair("Florida", "Fort Lauderdale"),
            "757" to Pair("Virginia", "Virginia Beach"),
            "760" to Pair("California", "Oceanside"),
            "762" to Pair("Georgia", "Augusta"),
            "763" to Pair("Minnesota", "Brooklyn Park"),
            "765" to Pair("Indiana", "Muncie"),
            "769" to Pair("Mississippi", "Jackson"),
            "770" to Pair("Georgia", "Roswell"),
            "772" to Pair("Florida", "Port St. Lucie"),
            "773" to Pair("Illinois", "Chicago"),
            "774" to Pair("Massachusetts", "Worcester"),
            "775" to Pair("Nevada", "Reno"),
            "779" to Pair("Illinois", "Rockford"),
            "781" to Pair("Massachusetts", "Lynn"),
            "785" to Pair("Kansas", "Topeka"),
            "786" to Pair("Florida", "Miami"),
            "801" to Pair("Utah", "Salt Lake City"),
            "802" to Pair("Vermont", null),
            "803" to Pair("South Carolina", "Columbia"),
            "804" to Pair("Virginia", "Richmond"),
            "805" to Pair("California", "Oxnard"),
            "806" to Pair("Texas", "Amarillo"),
            "808" to Pair("Hawaii", "Honolulu"),
            "810" to Pair("Michigan", "Flint"),
            "812" to Pair("Indiana", "Evansville"),
            "813" to Pair("Florida", "Tampa"),
            "814" to Pair("Pennsylvania", "Erie"),
            "815" to Pair("Illinois", "Rockford"),
            "816" to Pair("Missouri", "Kansas City"),
            "817" to Pair("Texas", "Fort Worth"),
            "818" to Pair("California", "Burbank"),
            "828" to Pair("North Carolina", "Asheville"),
            "830" to Pair("Texas", "New Braunfels"),
            "831" to Pair("California", "Salinas"),
            "832" to Pair("Texas", "Houston"),
            "843" to Pair("South Carolina", "Charleston"),
            "845" to Pair("New York", "New City"),
            "847" to Pair("Illinois", "Evanston"),
            "848" to Pair("New Jersey", "New Brunswick"),
            "850" to Pair("Florida", "Tallahassee"),
            "854" to Pair("South Carolina", "Charleston"),
            "856" to Pair("New Jersey", "Camden"),
            "857" to Pair("Massachusetts", "Boston"),
            "858" to Pair("California", "San Diego"),
            "859" to Pair("Kentucky", "Lexington"),
            "860" to Pair("Connecticut", "Hartford"),
            "862" to Pair("New Jersey", "Newark"),
            "863" to Pair("Florida", "Lakeland"),
            "864" to Pair("South Carolina", "Greenville"),
            "865" to Pair("Tennessee", "Knoxville"),
            "870" to Pair("Arkansas", "Jonesboro"),
            "872" to Pair("Illinois", "Chicago"),
            "878" to Pair("Pennsylvania", "Pittsburgh"),
            "901" to Pair("Tennessee", "Memphis"),
            "903" to Pair("Texas", "Tyler"),
            "904" to Pair("Florida", "Jacksonville"),
            "906" to Pair("Michigan", "Marquette"),
            "907" to Pair("Alaska", "Anchorage"),
            "908" to Pair("New Jersey", "Elizabeth"),
            "909" to Pair("California", "San Bernardino"),
            "910" to Pair("North Carolina", "Fayetteville"),
            "912" to Pair("Georgia", "Savannah"),
            "913" to Pair("Kansas", "Overland Park"),
            "914" to Pair("New York", "Yonkers"),
            "915" to Pair("Texas", "El Paso"),
            "916" to Pair("California", "Sacramento"),
            "917" to Pair("New York", "New York City"),
            "918" to Pair("Oklahoma", "Tulsa"),
            "919" to Pair("North Carolina", "Raleigh"),
            "920" to Pair("Wisconsin", "Green Bay"),
            "925" to Pair("California", "Concord"),
            "928" to Pair("Arizona", "Yuma"),
            "929" to Pair("New York", "New York City"),
            "930" to Pair("Indiana", "Evansville"),
            "931" to Pair("Tennessee", "Clarksville"),
            "936" to Pair("Texas", "Conroe"),
            "937" to Pair("Ohio", "Dayton"),
            "938" to Pair("Alabama", "Huntsville"),
            "940" to Pair("Texas", "Denton"),
            "941" to Pair("Florida", "Sarasota"),
            "947" to Pair("Michigan", "Troy"),
            "949" to Pair("California", "Irvine"),
            "951" to Pair("California", "Riverside"),
            "952" to Pair("Minnesota", "Bloomington"),
            "954" to Pair("Florida", "Fort Lauderdale"),
            "956" to Pair("Texas", "Laredo"),
            "959" to Pair("Connecticut", "Hartford"),
            "970" to Pair("Colorado", "Fort Collins"),
            "971" to Pair("Oregon", "Portland"),
            "972" to Pair("Texas", "Dallas"),
            "973" to Pair("New Jersey", "Newark"),
            "978" to Pair("Massachusetts", "Lowell"),
            "979" to Pair("Texas", "College Station"),
            "980" to Pair("North Carolina", "Charlotte"),
            "984" to Pair("North Carolina", "Raleigh"),
            "985" to Pair("Louisiana", "Houma"),
            "989" to Pair("Michigan", "Saginaw")
        )

        for ((areaCode, pair) in usAreaCodes) {
            entries.add(PrefixEntry(
                prefix = "1$areaCode",
                country = "US",
                countryName = "United States",
                region = pair.first,
                city = pair.second,
                lineType = LineType.UNKNOWN
            ))
        }

        // ═══════════════════════════════════════════════════════════════════
        // Canada (NANPA — country code 1, specific area codes)
        // ═══════════════════════════════════════════════════════════════════
        val canadianAreaCodes = mapOf(
            "204" to Pair("Manitoba", "Winnipeg"),
            "226" to Pair("Ontario", "London"),
            "236" to Pair("British Columbia", "Vancouver"),
            "249" to Pair("Ontario", "Sudbury"),
            "250" to Pair("British Columbia", "Victoria"),
            "289" to Pair("Ontario", "Hamilton"),
            "306" to Pair("Saskatchewan", "Saskatoon"),
            "343" to Pair("Ontario", "Ottawa"),
            "365" to Pair("Ontario", "Hamilton"),
            "367" to Pair("Quebec", "Quebec City"),
            "