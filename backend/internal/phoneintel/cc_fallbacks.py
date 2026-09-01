#!/usr/bin/env python3
"""Country code fallbacks and Go file generation for phone intelligence."""

# Import entries from gen.py
from gen import entries, add

# Country-code-only fallbacks (least specific)
cc_fallbacks = """1|US|United States|unknown|Various
7|RU|Russia|unknown|Various
20|EG|Egypt|unknown|Various
27|ZA|South Africa|unknown|Various
30|GR|Greece|unknown|Various
31|NL|Netherlands|unknown|Various
32|BE|Belgium|unknown|Various
33|FR|France|unknown|Various
34|ES|Spain|unknown|Various
36|HU|Hungary|unknown|Various
39|IT|Italy|unknown|Various
40|RO|Romania|unknown|Various
41|CH|Switzerland|unknown|Various
43|AT|Austria|unknown|Various
44|GB|United Kingdom|unknown|Various
45|DK|Denmark|unknown|Various
46|SE|Sweden|unknown|Various
47|NO|Norway|unknown|Various
48|PL|Poland|unknown|Various
49|DE|Germany|unknown|Various
51|PE|Peru|unknown|Various
52|MX|Mexico|unknown|Various
53|CU|Cuba|unknown|Various
54|AR|Argentina|unknown|Various
55|BR|Brazil|unknown|Various
56|CL|Chile|unknown|Various
57|CO|Colombia|unknown|Various
58|VE|Venezuela|unknown|Various
60|MY|Malaysia|unknown|Various
61|AU|Australia|unknown|Various
62|ID|Indonesia|unknown|Various
63|PH|Philippines|unknown|Various
64|NZ|New Zealand|unknown|Various
65|SG|Singapore|unknown|Various
66|TH|Thailand|unknown|Various
81|JP|Japan|unknown|Various
82|KR|South Korea|unknown|Various
84|VN|Vietnam|unknown|Various
86|CN|China|unknown|Various
90|TR|Turkey|unknown|Various
91|IN|India|unknown|Various
92|PK|Pakistan|unknown|Various
93|AF|Afghanistan|unknown|Various
94|LK|Sri Lanka|unknown|Various
95|MM|Myanmar|unknown|Various
212|MA|Morocco|unknown|Various
213|DZ|Algeria|unknown|Various
216|TN|Tunisia|unknown|Various
218|LY|Libya|unknown|Various
220|GM|Gambia|unknown|Various
221|SN|Senegal|unknown|Various
222|MR|Mauritania|unknown|Various
223|ML|Mali|unknown|Various
224|GN|Guinea|unknown|Various
225|CI|Ivory Coast|unknown|Various
226|BF|Burkina Faso|unknown|Various
227|NE|Niger|unknown|Various
228|TG|Togo|unknown|Various
229|BJ|Benin|unknown|Various
230|MU|Mauritius|unknown|Various
231|LR|Liberia|unknown|Various
232|SL|Sierra Leone|unknown|Various
233|GH|Ghana|unknown|Various
234|NG|Nigeria|unknown|Various
235|TD|Chad|unknown|Various
236|CF|Central African Republic|unknown|Various
237|CM|Cameroon|unknown|Various
238|CV|Cape Verde|unknown|Various
239|ST|Sao Tome and Principe|unknown|Various
240|GQ|Equatorial Guinea|unknown|Various
241|GA|Gabon|unknown|Various
242|CG|Republic of the Congo|unknown|Various
243|CD|Democratic Republic of the Congo|unknown|Various
244|AO|Angola|unknown|Various
245|GW|Guinea-Bissau|unknown|Various
246|IO|British Indian Ocean Territory|unknown|Various
248|SC|Seychelles|unknown|Various
249|SD|Sudan|unknown|Various
250|RW|Rwanda|unknown|Various
251|ET|Ethiopia|unknown|Various
252|SO|Somalia|unknown|Various
253|DJ|Djibouti|unknown|Various
254|KE|Kenya|unknown|Various
255|TZ|Tanzania|unknown|Various
256|UG|Uganda|unknown|Various
257|BI|Burundi|unknown|Various
258|MZ|Mozambique|unknown|Various
260|ZM|Zambia|unknown|Various
261|MG|Madagascar|unknown|Various
262|RE|Reunion|unknown|Various
263|ZW|Zimbabwe|unknown|Various
264|NA|Namibia|unknown|Various
265|MW|Malawi|unknown|Various
266|LS|Lesotho|unknown|Various
267|BW|Botswana|unknown|Various
268|SZ|Eswatini|unknown|Various
269|KM|Comoros|unknown|Various
297|AW|Aruba|unknown|Various
298|FO|Faroe Islands|unknown|Various
299|GL|Greenland|unknown|Various
350|GI|Gibraltar|unknown|Various
351|PT|Portugal|unknown|Various
352|LU|Luxembourg|unknown|Various
353|IE|Ireland|unknown|Various
354|IS|Iceland|unknown|Various
355|AL|Albania|unknown|Various
356|MT|Malta|unknown|Various
357|CY|Cyprus|unknown|Various
358|FI|Finland|unknown|Various
359|BG|Bulgaria|unknown|Various
370|LT|Lithuania|unknown|Various
371|LV|Latvia|unknown|Various
372|EE|Estonia|unknown|Various
373|MD|Moldova|unknown|Various
374|AM|Armenia|unknown|Various
375|BY|Belarus|unknown|Various
376|AD|Andorra|unknown|Various
377|MC|Monaco|unknown|Various
378|SM|San Marino|unknown|Various
380|UA|Ukraine|unknown|Various
381|RS|Serbia|unknown|Various
382|ME|Montenegro|unknown|Various
383|XK|Kosovo|unknown|Various
385|HR|Croatia|unknown|Various
386|SI|Slovenia|unknown|Various
387|BA|Bosnia and Herzegovina|unknown|Various
389|MK|North Macedonia|unknown|Various
420|CZ|Czech Republic|unknown|Various
421|SK|Slovakia|unknown|Various
423|LI|Liechtenstein|unknown|Various
500|FK|Falkland Islands|unknown|Various
501|BZ|Belize|unknown|Various
502|GT|Guatemala|unknown|Various
503|SV|El Salvador|unknown|Various
504|HN|Honduras|unknown|Various
505|NI|Nicaragua|unknown|Various
506|CR|Costa Rica|unknown|Various
507|PA|Panama|unknown|Various
508|PM|Saint Pierre and Miquelon|unknown|Various
509|HT|Haiti|unknown|Various
590|GP|Guadeloupe|unknown|Various
591|BO|Bolivia|unknown|Various
592|GY|Guyana|unknown|Various
593|EC|Ecuador|unknown|Various
594|GF|French Guiana|unknown|Various
595|PY|Paraguay|unknown|Various
596|MQ|Martinique|unknown|Various
597|SR|Suriname|unknown|Various
598|UY|Uruguay|unknown|Various
599|CW|Curacao|unknown|Various
670|TL|East Timor|unknown|Various
672|NF|Norfolk Island|unknown|Various
673|BN|Brunei|unknown|Various
674|NR|Nauru|unknown|Various
675|PG|Papua New Guinea|unknown|Various
676|TO|Tonga|unknown|Various
677|SB|Solomon Islands|unknown|Various
678|VU|Vanuatu|unknown|Various
679|FJ|Fiji|unknown|Various
680|PW|Palau|unknown|Various
681|WF|Wallis and Futuna|unknown|Various
682|CK|Cook Islands|unknown|Various
683|NU|Niue|unknown|Various
685|WS|Samoa|unknown|Various
686|KI|Kiribati|unknown|Various
687|NC|New Caledonia|unknown|Various
688|TV|Tuvalu|unknown|Various
689|PF|French Polynesia|unknown|Various
690|TK|Tokelau|unknown|Various
691|FM|Micronesia|unknown|Various
692|MH|Marshall Islands|unknown|Various
850|KP|North Korea|unknown|Various
852|HK|Hong Kong|unknown|Various
853|MO|Macau|unknown|Various
855|KH|Cambodia|unknown|Various
856|LA|Laos|unknown|Various
880|BD|Bangladesh|unknown|Various
886|TW|Taiwan|unknown|Various
960|MV|Maldives|unknown|Various
961|LB|Lebanon|unknown|Various
962|JO|Jordan|unknown|Various
963|SY|Syria|unknown|Various
964|IQ|Iraq|unknown|Various
965|KW|Kuwait|unknown|Various
966|SA|Saudi Arabia|unknown|Various
967|YE|Yemen|unknown|Various
968|OM|Oman|unknown|Various
970|PS|Palestine|unknown|Various
971|AE|United Arab Emirates|unknown|Various
972|IL|Israel|unknown|Various
973|BH|Bahrain|unknown|Various
974|QA|Qatar|unknown|Various
975|BT|Bhutan|unknown|Various
976|MN|Mongolia|unknown|Various
977|NP|Nepal|unknown|Various
992|TJ|Tajikistan|unknown|Various
993|TM|Turkmenistan|unknown|Various
994|AZ|Azerbaijan|unknown|Various
995|GE|Georgia|unknown|Various
996|KG|Kyrgyzstan|unknown|Various
998|UZ|Uzbekistan|unknown|Various"""

for line in cc_fallbacks.strip().split("\n"):
    parts = line.split("|")
    p, cc, name, lt, carrier = parts[0], parts[1], parts[2], parts[3], parts[4]
    add(p, cc, name, lt, carrier)

# Generate the Go file
outpath = "prefixdb.go"
with open(outpath, "w") as f:
    f.write("package phoneintel\n\n")
    f.write("// prefixData is the embedded phone number prefix database.\n")
    f.write("// All data is compiled into the binary - no external files needed.\n")
    f.write("//\n")
    f.write("// Prefixes are digit strings WITHOUT the leading +.\n")
    f.write("// Longest-prefix-match wins, so more specific entries override general ones.\n")
    f.write("//\n")
    f.write("// Generated by gen.py - DO NOT EDIT BY HAND\n")
    f.write(f"// Total entries: {len(entries)}\n")
    f.write("var prefixData = []PrefixEntry{\n")
    for e in entries:
        prefix, country, country_name, region, city, carrier, line_type = e
        # Escape any double quotes in fields
        region_esc = region.replace('"', '\\"')
        city_esc = city.replace('"', '\\"')
        carrier_esc = carrier.replace('"', '\\"')
        country_name_esc = country_name.replace('"', '\\"')
        parts = [
            f'Prefix: "{prefix}"',
            f'Country: "{country}"',
            f'CountryName: "{country_name_esc}"',
        ]
        if region:
            parts.append(f'Region: "{region_esc}"')
        if city:
            parts.append(f'City: "{city_esc}"')
        if carrier:
            parts.append(f'Carrier: "{carrier_esc}"')
        parts.append(f'LineType: "{line_type}"')
        f.write("\t{" + ", ".join(parts) + "},\n")
    f.write("}\n")

print(f"Generated {outpath} with {len(entries)} prefix entries")
