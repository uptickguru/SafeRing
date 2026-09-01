#!/usr/bin/env python3
"""Generate prefixdb.go for SafeRing phone intelligence service."""
import os, sys

outdir = os.path.dirname(os.path.abspath(__file__))
outpath = os.path.join(outdir, "prefixdb.go")

entries = []

def add(prefix, country, country_name, line_type, carrier="", region="", city=""):
    entries.append((prefix, country, country_name, region, city, carrier, line_type))

# === NANPA Toll-Free ===
for p in ["1800","1888","1877","1866","1855","1844","1833"]:
    add(p, "US", "United States", "toll_free", "Toll-Free")
add("1900", "US", "United States", "premium", "Premium Rate")

# === US Area Codes ===
us_data = """1205|Alabama|Birmingham|AT&T|landline
1251|Alabama|Mobile|AT&T|landline
1256|Alabama|Huntsville|AT&T|landline
1334|Alabama|Montgomery|AT&T|landline
1659|Alabama|Birmingham|Various|mobile
1938|Alabama|Huntsville|Various|mobile
1907|Alaska|Anchorage|ACS|landline
1480|Arizona|Phoenix|CenturyLink|landline
1520|Arizona|Tucson|CenturyLink|landline
1602|Arizona|Phoenix|CenturyLink|landline
1623|Arizona|Phoenix|CenturyLink|landline
1928|Arizona|Yuma|CenturyLink|landline
1479|Arkansas|Fort Smith|AT&T|landline
1501|Arkansas|Little Rock|AT&T|landline
1870|Arkansas|Jonesboro|AT&T|landline
1209|California|Stockton|AT&T|landline
1213|California|Los Angeles|AT&T|landline
1310|California|Los Angeles|AT&T|landline
1323|California|Los Angeles|AT&T|landline
1408|California|San Jose|AT&T|landline
1415|California|San Francisco|AT&T|landline
1424|California|Los Angeles|Various|mobile
1442|California|Oceanside|Various|mobile
1510|California|Oakland|AT&T|landline
1530|California|Sacramento|AT&T|landline
1559|California|Fresno|AT&T|landline
1562|California|Long Beach|AT&T|landline
1619|California|San Diego|AT&T|landline
1626|California|Pasadena|AT&T|landline
1628|California|San Francisco|Various|mobile
1650|California|Palo Alto|AT&T|landline
1661|California|Bakersfield|AT&T|landline
1669|California|San Jose|Various|mobile
1707|California|Santa Rosa|AT&T|landline
1714|California|Anaheim|AT&T|landline
1747|California|Los Angeles|Various|mobile
1760|California|Oceanside|AT&T|landline
1805|California|Santa Barbara|AT&T|landline
1818|California|Los Angeles|AT&T|landline
1820|California|Thousand Oaks|Various|mobile
1831|California|Salinas|AT&T|landline
1858|California|San Diego|Various|mobile
1909|California|San Bernardino|AT&T|landline
1916|California|Sacramento|AT&T|landline
1925|California|Concord|AT&T|landline
1949|California|Irvine|AT&T|landline
1951|California|Riverside|AT&T|landline
1303|Colorado|Denver|CenturyLink|landline
1719|Colorado|Colorado Springs|CenturyLink|landline
1720|Colorado|Denver|CenturyLink|landline
1970|Colorado|Fort Collins|CenturyLink|landline
1983|Colorado|Denver|Various|mobile
1203|Connecticut|Bridgeport|Frontier|landline
1475|Connecticut|New Haven|Various|mobile
1860|Connecticut|Hartford|Frontier|landline
1959|Connecticut|Hartford|Various|mobile
1302|Delaware|Wilmington|Verizon|landline
1239|Florida|Fort Myers|CenturyLink|landline
1305|Florida|Miami|AT&T|landline
1321|Florida|Orlando|AT&T|landline
1352|Florida|Gainesville|AT&T|landline
1386|Florida|Daytona Beach|AT&T|landline
1407|Florida|Orlando|AT&T|landline
1448|Florida|Orlando|Various|mobile
1561|Florida|West Palm Beach|AT&T|landline
1656|Florida|Tampa|Various|mobile
1689|Florida|Orlando|Various|mobile
1727|Florida|St. Petersburg|Frontier|landline
1754|Florida|Fort Lauderdale|Various|mobile
1772|Florida|Port St. Lucie|AT&T|landline
1786|Florida|Miami|AT&T|landline
1813|Florida|Tampa|Frontier|landline
1850|Florida|Tallahassee|CenturyLink|landline
1863|Florida|Lakeland|CenturyLink|landline
1904|Florida|Jacksonville|AT&T|landline
1941|Florida|Sarasota|Frontier|landline
1954|Florida|Fort Lauderdale|AT&T|landline
1229|Georgia|Albany|AT&T|landline
1404|Georgia|Atlanta|AT&T|landline
1470|Georgia|Atlanta|Various|mobile
1478|Georgia|Macon|AT&T|landline
1678|Georgia|Atlanta|AT&T|landline
1706|Georgia|Augusta|AT&T|landline
1762|Georgia|Columbus|Various|mobile
1770|Georgia|Atlanta|AT&T|landline
1912|Georgia|Savannah|AT&T|landline
1943|Georgia|Atlanta|Various|mobile
1808|Hawaii|Honolulu|Hawaiian Telcom|landline
1208|Idaho|Boise|CenturyLink|landline
1986|Idaho|Boise|Various|mobile
1217|Illinois|Springfield|AT&T|landline
1224|Illinois|Chicago|AT&T|landline
1309|Illinois|Peoria|AT&T|landline
1312|Illinois|Chicago|AT&T|landline
1331|Illinois|Chicago|Various|mobile
1447|Illinois|East St. Louis|Various|mobile
1464|Illinois|Chicago|Various|mobile
1618|Illinois|East St. Louis|AT&T|landline
1630|Illinois|Chicago|AT&T|landline
1708|Illinois|Chicago|AT&T|landline
1730|Illinois|Springfield|Various|mobile
1773|Illinois|Chicago|AT&T|landline
1779|Illinois|Rockford|Various|mobile
1815|Illinois|Rockford|AT&T|landline
1847|Illinois|Chicago|AT&T|landline
1872|Illinois|Chicago|Various|mobile
1219|Indiana|Gary|AT&T|landline
1260|Indiana|Fort Wayne|Frontier|landline
1317|Indiana|Indianapolis|AT&T|landline
1463|Indiana|Indianapolis|Various|mobile
1574|Indiana|South Bend|Frontier|landline
1765|Indiana|Muncie|AT&T|landline
1812|Indiana|Evansville|AT&T|landline
1930|Indiana|Bloomington|Various|mobile
1319|Iowa|Cedar Rapids|CenturyLink|landline
1515|Iowa|Des Moines|CenturyLink|landline
1563|Iowa|Davenport|CenturyLink|landline
1641|Iowa|Ottumwa|CenturyLink|landline
1712|Iowa|Sioux City|CenturyLink|landline
1316|Kansas|Wichita|AT&T|landline
1620|Kansas|Hutchinson|AT&T|landline
1785|Kansas|Topeka|AT&T|landline
1913|Kansas|Kansas City|AT&T|landline
1270|Kentucky|Bowling Green|AT&T|landline
1364|Kentucky|Bowling Green|Various|mobile
1502|Kentucky|Louisville|AT&T|landline
1606|Kentucky|Ashland|AT&T|landline
1859|Kentucky|Lexington|AT&T|landline
1225|Louisiana|Baton Rouge|AT&T|landline
1318|Louisiana|Shreveport|AT&T|landline
1337|Louisiana|Lafayette|AT&T|landline
1504|Louisiana|New Orleans|AT&T|landline
1985|Louisiana|New Orleans|Various|mobile
1207|Maine|Portland|Consolidated|landline
1240|Maryland|Silver Spring|Verizon|landline
1301|Maryland|Silver Spring|Verizon|landline
1410|Maryland|Baltimore|Verizon|landline
1443|Maryland|Baltimore|Verizon|landline
1667|Maryland|Baltimore|Various|mobile
1339|Massachusetts|Boston|Various|mobile
1351|Massachusetts|Lowell|Various|mobile
1413|Massachusetts|Springfield|Verizon|landline
1508|Massachusetts|Worcester|Verizon|landline
1617|Massachusetts|Boston|Verizon|landline
1774|Massachusetts|Worcester|Various|mobile
1781|Massachusetts|Boston|Verizon|landline
1857|Massachusetts|Boston|Various|mobile
1978|Massachusetts|Lowell|Verizon|landline
1231|Michigan|Muskegon|Frontier|landline
1248|Michigan|Detroit|AT&T|landline
1269|Michigan|Kalamazoo|AT&T|landline
1313|Michigan|Detroit|AT&T|landline
1517|Michigan|Lansing|AT&T|landline
1586|Michigan|Detroit|AT&T|landline
1616|Michigan|Grand Rapids|AT&T|landline
1734|Michigan|Ann Arbor|AT&T|landline
1810|Michigan|Flint|AT&T|landline
1906|Michigan|Marquette|AT&T|landline
1947|Michigan|Detroit|Various|mobile
1989|Michigan|Saginaw|AT&T|landline
1218|Minnesota|Duluth|CenturyLink|landline
1320|Minnesota|St. Cloud|CenturyLink|landline
1507|Minnesota|Rochester|CenturyLink|landline
1612|Minnesota|Minneapolis|CenturyLink|landline
1651|Minnesota|St. Paul|CenturyLink|landline
1763|Minnesota|Minneapolis|CenturyLink|landline
1952|Minnesota|Minneapolis|CenturyLink|landline
1228|Mississippi|Biloxi|AT&T|landline
1471|Mississippi|Jackson|Various|mobile
1601|Mississippi|Jackson|AT&T|landline
1662|Mississippi|Tupelo|AT&T|landline
1769|Mississippi|Jackson|Various|mobile
1314|Missouri|St. Louis|AT&T|landline
1417|Missouri|Springfield|AT&T|landline
1557|Missouri|St. Louis|Various|mobile
1573|Missouri|Columbia|AT&T|landline
1636|Missouri|St. Louis|AT&T|landline
1660|Missouri|Sedalia|AT&T|landline
1816|Missouri|Kansas City|AT&T|landline
1975|Missouri|Kansas City|Various|mobile
1406|Montana|Billings|CenturyLink|landline
1308|Nebraska|Grand Island|CenturyLink|landline
1402|Nebraska|Omaha|CenturyLink|landline
1531|Nebraska|Omaha|Various|mobile
1702|Nevada|Las Vegas|CenturyLink|landline
1725|Nevada|Las Vegas|Various|mobile
1775|Nevada|Reno|CenturyLink|landline
1603|New Hampshire|Manchester|Consolidated|landline
1201|New Jersey|Jersey City|Verizon|landline
1551|New Jersey|Jersey City|Various|mobile
1609|New Jersey|Trenton|Verizon|landline
1640|New Jersey|Trenton|Various|mobile
1732|New Jersey|New Brunswick|Verizon|landline
1848|New Jersey|New Brunswick|Various|mobile
1856|New Jersey|Camden|Verizon|landline
1862|New Jersey|Newark|Various|mobile
1908|New Jersey|Elizabeth|Verizon|landline
1973|New Jersey|Newark|Verizon|landline
1505|New Mexico|Albuquerque|CenturyLink|landline
1575|New Mexico|Las Cruces|CenturyLink|landline
1212|New York|New York|Verizon|landline
1315|New York|Syracuse|Verizon|landline
1332|New York|New York|Various|mobile
1347|New York|New York|Verizon|landline
1516|New York|Hempstead|Verizon|landline
1518|New York|Albany|Verizon|landline
1585|New York|Rochester|Verizon|landline
1607|New York|Binghamton|Verizon|landline
1631|New York|Brentwood|Verizon|landline
1646|New York|New York|Verizon|landline
1680|New York|Syracuse|Various|mobile
1716|New York|Buffalo|Verizon|landline
1718|New York|New York|Verizon|landline
1838|New York|Albany|Various|mobile
1845|New York|Poughkeepsie|Verizon|landline
1914|New York|Yonkers|Verizon|landline
1917|New York|New York|Various|mobile
1929|New York|New York|Various|mobile
1934|New York|Brentwood|Various|mobile
1252|North Carolina|Greenville|AT&T|landline
1336|North Carolina|Greensboro|AT&T|landline
1472|North Carolina|Greensboro|Various|mobile
1704|North Carolina|Charlotte|AT&T|landline
1743|North Carolina|Charlotte|Various|mobile
1828|North Carolina|Asheville|AT&T|landline
1910|North Carolina|Fayetteville|AT&T|landline
1919|North Carolina|Raleigh|AT&T|landline
1980|North Carolina|Raleigh|Various|mobile
1984|North Carolina|Greenville|Various|mobile
1701|North Dakota|Bismarck|CenturyLink|landline
1216|Ohio|Cleveland|AT&T|landline
1220|Ohio|Canton|Various|mobile
1234|Ohio|Canton|Various|mobile
1283|Ohio|Cincinnati|Various|mobile
1326|Ohio|Dayton|Various|mobile
1330|Ohio|Akron|AT&T|landline
1380|Ohio|Columbus|Various|mobile
1419|Ohio|Toledo|AT&T|landline
1440|Ohio|Cleveland|AT&T|landline
1513|Ohio|Cincinnati|AT&T|landline
1567|Ohio|Toledo|Various|mobile
1614|Ohio|Columbus|AT&T|landline
1740|Ohio|Newark|AT&T|landline
1937|Ohio|Dayton|AT&T|landline
1405|Oklahoma|Oklahoma City|AT&T|landline
1539|Oklahoma|Tulsa|Various|mobile
1572|Oklahoma|Oklahoma City|Various|mobile
1580|Oklahoma|Lawton|AT&T|landline
1918|Oklahoma|Tulsa|AT&T|landline
1458|Oregon|Eugene|Various|mobile
1503|Oregon|Portland|CenturyLink|landline
1541|Oregon|Eugene|CenturyLink|landline
1971|Oregon|Portland|CenturyLink|landline
1215|Pennsylvania|Philadelphia|Verizon|landline
1223|Pennsylvania|Pittsburgh|Various|mobile
1267|Pennsylvania|Philadelphia|Verizon|landline
1272|Pennsylvania|Scranton|Various|mobile
1412|Pennsylvania|Pittsburgh|Verizon|landline
1445|Pennsylvania|Philadelphia|Various|mobile
1484|Pennsylvania|Allentown|Verizon|landline
1570|Pennsylvania|Scranton|Verizon|landline
1582|Pennsylvania|Erie|Various|mobile
1610|Pennsylvania|Allentown|Verizon|landline
1717|Pennsylvania|Lancaster|Verizon|landline
1724|Pennsylvania|New Castle|Verizon|landline
1814|Pennsylvania|Erie|Verizon|landline
1835|Pennsylvania|Allentown|Various|mobile
1878|Pennsylvania|Pittsburgh|Various|mobile
1401|Rhode Island|Providence|Verizon|landline
1803|South Carolina|Columbia|AT&T|landline
1839|South Carolina|Columbia|Various|mobile
1843|South Carolina|Charleston|AT&T|landline
1854|South Carolina|Charleston|Various|mobile
1864|South Carolina|Greenville|AT&T|landline
1605|South Dakota|Sioux Falls|CenturyLink|landline
1423|Tennessee|Chattanooga|AT&T|landline
1615|Tennessee|Nashville|AT&T|landline
1629|Tennessee|Nashville|Various|mobile
1731|Tennessee|Jackson|AT&T|landline
1865|Tennessee|Knoxville|AT&T|landline
1901|Tennessee|Memphis|AT&T|landline
1931|Tennessee|Clarksville|AT&T|landline
1210|Texas|San Antonio|AT&T|landline
1214|Texas|Dallas|AT&T|landline
1254|Texas|Waco|AT&T|landline
1281|Texas|Houston|AT&T|landline
1325|Texas|Abilene|AT&T|landline
1346|Texas|Houston|AT&T|landline
1361|Texas|Corpus Christi|AT&T|landline
1409|Texas|Beaumont|AT&T|landline
1430|Texas|San Antonio|Various|mobile
1432|Texas|Midland|AT&T|landline
1469|Texas|Dallas|Various|mobile
1512|Texas|Austin|AT&T|landline
1682|Texas|Fort Worth|AT&T|landline
1713|Texas|Houston|AT&T|landline
1726|Texas|San Antonio|Various|mobile
1737|Texas|Austin|Various|mobile
1806|Texas|Lubbock|AT&T|landline
1817|Texas|Fort Worth|AT&T|landline
1830|Texas|San Antonio|AT&T|landline
1832|Texas|Houston|AT&T|landline
1903|Texas|Tyler|AT&T|landline
1915|Texas|El Paso|AT&T|landline
1936|Texas|Houston|Various|mobile
1940|Texas|Denton|AT&T|landline
1945|Texas|Dallas|Various|mobile
1956|Texas|Laredo|AT&T|landline
1972|Texas|Dallas|Various|mobile
1979|Texas|College Station|AT&T|landline
1385|Utah|Salt Lake City|Various|mobile
1435|Utah|St. George|CenturyLink|landline
1801|Utah|Salt Lake City|CenturyLink|landline
1802|Vermont|Burlington|Consolidated|landline
1276|Virginia|Martinsville|Verizon|landline
1434|Virginia|Lynchburg|Verizon|landline
1540|Virginia|Roanoke|Verizon|landline
1571|Virginia|Arlington|Verizon|landline
1703|Virginia|Arlington|Verizon|landline
1757|Virginia|Norfolk|Verizon|landline
1804|Virginia|Richmond|Verizon|landline
1826|Virginia|Roanoke|Various|mobile
1948|Virginia|Norfolk|Various|mobile
1206|Washington|Seattle|CenturyLink|landline
1253|Washington|Tacoma|CenturyLink|landline
1360|Washington|Vancouver|CenturyLink|landline
1425|Washington|Bellevue|CenturyLink|landline
1509|Washington|Spokane|CenturyLink|landline
1564|Washington|Vancouver|Various|mobile
1235|West Virginia|Huntington|Various|mobile
1304|West Virginia|Charleston|Frontier|landline
1681|West Virginia|Charleston|Various|mobile
1262|Wisconsin|Milwaukee|AT&T|landline
1414|Wisconsin|Milwaukee|AT&T|landline
1534|Wisconsin|Eau Claire|Various|mobile
1608|Wisconsin|Madison|AT&T|landline
1715|Wisconsin|Eau Claire|AT&T|landline
1920|Wisconsin|Green Bay|AT&T|landline
1307|Wyoming|Cheyenne|CenturyLink|landline
1202|District of Columbia|Washington|Verizon|landline
1771|District of Columbia|Washington|Various|mobile"""

for line in us_data.strip().split("\n"):
    p, state, city, carrier, lt = line.split("|")
    add(p, "US", "United States", lt, carrier, region=state, city=city)

# === Canada ===
ca_data = """1204|Manitoba|Winnipeg|Bell MTS|landline
1226|Ontario|London|Bell Canada|landline
1236|British Columbia|Vancouver|Telus|mobile
1250|British Columbia|Victoria|Telus|landline
1289|Ontario|Hamilton|Bell Canada|landline
1306|Saskatchewan|Regina|SaskTel|landline
1343|Ontario|Ottawa|Bell Canada|mobile
1403|Alberta|Calgary|Telus|landline
1416|Ontario|Toronto|Bell Canada|landline
1418|Quebec|Quebec City|Bell Canada|landline
1438|Quebec|Montreal|Bell Canada|mobile
1450|Quebec|Laval|Bell Canada|landline
1506|New Brunswick|Fredericton|Bell Aliant|landline
1514|Quebec|Montreal|Bell Canada|landline
1519|Ontario|London|Bell Canada|landline
1587|Alberta|Calgary|Telus|mobile
1604|British Columbia|Vancouver|Telus|landline
1613|Ontario|Ottawa|Bell Canada|landline
1647|Ontario|Toronto|Bell Canada|mobile
1705|Ontario|Sudbury|Bell Canada|landline
1709|Newfoundland|St. Johns|Bell Aliant|landline
1778|British Columbia|Vancouver|Telus|landline
1780|Alberta|Edmonton|Telus|landline
1782|Nova Scotia|Halifax|Bell Aliant|mobile
1807|Ontario|Thunder Bay|Bell Canada|landline
1819|Quebec|Sherbrooke|Bell Canada|landline
1825|Alberta|Edmonton|Telus|mobile
1867|Yukon|Whitehorse|Northwestel|landline
1873|Quebec|Montreal|Bell Canada|mobile
1902|Nova Scotia|Halifax|Bell Aliant|landline
1905|Ontario|Hamilton|Bell Canada|landline"""

for line in ca_data.strip().split("\n"):
    p, prov, city, carrier, lt = line.split("|")
    add(p, "CA", "Canada", lt, carrier, region=prov, city=city)

# === UK ===
uk_data = """4420|England|London|BT|landline
44121|England|Birmingham|BT|landline
44131|Scotland|Edinburgh|BT|landline
44141|Scotland|Glasgow|BT|landline
44151|England|Liverpool|BT|landline
44161|England|Manchester|BT|landline
44113|England|Leeds|BT|landline
44114|England|Sheffield|BT|landline
44115|England|Nottingham|BT|landline
44116|England|Leicester|BT|landline
44117|England|Bristol|BT|landline
44118|England|Reading|BT|landline
44191|England|Newcastle upon Tyne|BT|landline
4429|Wales|Cardiff|BT|landline
4428|Northern Ireland|Belfast|BT|landline
4474|England|Mobile|Various|mobile
4475|England|Mobile|Various|mobile
4477|England|Mobile|Various|mobile
4478|England|Mobile|Various|mobile
4479|England|Mobile|Various|mobile
4473|England|Mobile|Various|mobile
4471|England|Mobile|Various|mobile
4456|England|VoIP|Various|voip
44843|England|Non-geographic|Various|landline
44845|England|Non-geographic|Various|landline
44870|England|Non-geographic|Various|landline
449|England|Premium Rate|Various|premium"""

for line in uk_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "GB", "United Kingdom", lt, carrier, region=region, city=city)

# === Nigeria ===
ng_data = """234703|Lagos|MTN Nigeria|mobile
234706|Lagos|MTN Nigeria|mobile
234803|Lagos|MTN Nigeria|mobile
234806|Lagos|MTN Nigeria|mobile
234810|Lagos|MTN Nigeria|mobile
234813|Lagos|MTN Nigeria|mobile
234814|Lagos|MTN Nigeria|mobile
234816|Lagos|MTN Nigeria|mobile
234903|Lagos|MTN Nigeria|mobile
234906|Lagos|MTN Nigeria|mobile
234913|Lagos|MTN Nigeria|mobile
234916|Lagos|MTN Nigeria|mobile
234705|Lagos|Glo|mobile
234805|Lagos|Glo|mobile
234807|Lagos|Glo|mobile
234811|Lagos|Glo|mobile
234815|Lagos|Glo|mobile
234905|Lagos|Glo|mobile
234915|Lagos|Glo|mobile
234802|Lagos|Airtel Nigeria|mobile
234808|Lagos|Airtel Nigeria|mobile
234812|Lagos|Airtel Nigeria|mobile
234901|Lagos|Airtel Nigeria|mobile
234902|Lagos|Airtel Nigeria|mobile
234904|Lagos|Airtel Nigeria|mobile
234907|Lagos|Airtel Nigeria|mobile
234912|Lagos|Airtel Nigeria|mobile
234809|Lagos|9mobile|mobile
234817|Lagos|9mobile|mobile
234818|Lagos|9mobile|mobile
234908|Lagos|9mobile|mobile
234909|Lagos|9mobile|mobile
234918|Lagos|9mobile|mobile
2341|Lagos|NITEL|landline
2349|Abuja|NITEL|landline"""

for line in ng_data.strip().split("\n"):
    p, city, carrier, lt = line.split("|")
    add(p, "NG", "Nigeria", lt, carrier, region=city, city=city)

# === India ===
in_data = """9170|Delhi|Delhi|Airtel|mobile
9172|Maharashtra|Mumbai|Airtel|mobile
9173|Gujarat|Ahmedabad|Airtel|mobile
9174|Tamil Nadu|Chennai|Airtel|mobile
9175|Andhra Pradesh|Hyderabad|Airtel|mobile
9176|Karnataka|Bangalore|Airtel|mobile
9177|Andhra Pradesh|Hyderabad|Airtel|mobile
9178|West Bengal|Kolkata|Airtel|mobile
9179|Madhya Pradesh|Bhopal|Airtel|mobile
9180|Karnataka|Bangalore|Airtel|mobile
9181|Tamil Nadu|Chennai|Airtel|mobile
9182|Kerala|Kochi|Airtel|mobile
9183|Punjab|Chandigarh|Airtel|mobile
9184|Haryana|Gurgaon|Airtel|mobile
9185|Rajasthan|Jaipur|Airtel|mobile
9186|Uttar Pradesh|Lucknow|Airtel|mobile
9187|Bihar|Patna|Airtel|mobile
9188|Maharashtra|Mumbai|Airtel|mobile
9189|Delhi|Delhi|Airtel|mobile
9190|Tamil Nadu|Chennai|Airtel|mobile
9191|Delhi|Delhi|Airtel|mobile
9192|Maharashtra|Mumbai|Airtel|mobile
9193|West Bengal|Kolkata|Airtel|mobile
9194|Karnataka|Bangalore|Airtel|mobile
9195|Gujarat|Ahmedabad|Airtel|mobile
9196|Andhra Pradesh|Hyderabad|Airtel|mobile
9197|Uttar Pradesh|Lucknow|Airtel|mobile
9198|Kerala|Kochi|Airtel|mobile
9199|Punjab|Chandigarh|Airtel|mobile
9111|Delhi|Delhi|BSNL|landline
9122|Maharashtra|Mumbai|BSNL|landline
9133|West Bengal|Kolkata|BSNL|landline
9144|Tamil Nadu|Chennai|BSNL|landline"""

for line in in_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "IN", "India", lt, carrier, region=region, city=city)

# === Germany ===
de_data = """4930|Berlin|Berlin|Deutsche Telekom|landline
4940|Hamburg|Hamburg|Deutsche Telekom|landline
4969|Hesse|Frankfurt|Deutsche Telekom|landline
4989|Bavaria|Munich|Deutsche Telekom|landline
49221|North Rhine-Westphalia|Cologne|Deutsche Telekom|landline
49151|Germany|Mobile|Telekom|mobile
49152|Germany|Mobile|Vodafone|mobile
49157|Germany|Mobile|O2|mobile
49160|Germany|Mobile|Telekom|mobile
49162|Germany|Mobile|Vodafone|mobile
49163|Germany|Mobile|E-Plus|mobile
49170|Germany|Mobile|Telekom|mobile
49171|Germany|Mobile|Telekom|mobile
49172|Germany|Mobile|Vodafone|mobile
49173|Germany|Mobile|Vodafone|mobile
49174|Germany|Mobile|O2|mobile
49175|Germany|Mobile|Telekom|mobile
49176|Germany|Mobile|O2|mobile
49177|Germany|Mobile|E-Plus|mobile
49178|Germany|Mobile|E-Plus|mobile
49179|Germany|Mobile|O2|mobile"""
for line in de_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "DE", "Germany", lt, carrier, region=region, city=city)

# === France ===
fr_data = """331|Ile-de-France|Paris|Orange|landline
332|Northwest|Rennes|Orange|landline
333|Northeast|Strasbourg|Orange|landline
334|Southeast|Lyon|Orange|landline
335|Southwest|Bordeaux|Orange|landline
336|France|Mobile|Orange|mobile
337|France|Mobile|Various|mobile"""
for line in fr_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "FR", "France", lt, carrier, region=region, city=city)

# === Spain ===
es_data = """3491|Madrid|Madrid|Telefonica|landline
3493|Catalonia|Barcelona|Telefonica|landline
3496|Valencia|Valencia|Telefonica|landline
346|Spain|Mobile|Movistar|mobile
347|Spain|Mobile|Vodafone|mobile"""
for line in es_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "ES", "Spain", lt, carrier, region=region, city=city)

# === Italy ===
it_data = """3906|Lazio|Rome|Telecom Italia|landline
3902|Lombardy|Milan|Telecom Italia|landline
39081|Campania|Naples|Telecom Italia|landline
393|Italy|Mobile|TIM|mobile"""
for line in it_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "IT", "Italy", lt, carrier, region=region, city=city)

# === Brazil ===
br_data = """5511|Sao Paulo|Sao Paulo|Telefonica|landline
5521|Rio de Janeiro|Rio de Janeiro|Oi|landline
5531|Minas Gerais|Belo Horizonte|Telemar|landline
5561|Distrito Federal|Brasilia|Telefonica|landline"""
for line in br_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "BR", "Brazil", lt, carrier, region=region, city=city)

# === Mexico ===
mx_data = """5255|Mexico City|Mexico City|Telmex|landline
5233|Jalisco|Guadalajara|Telmex|landline
5281|Nuevo Leon|Monterrey|Telmex|landline"""
for line in mx_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "MX", "Mexico", lt, carrier, region=region, city=city)

# === Australia ===
au_data = """612|New South Wales|Sydney|Telstra|landline
613|Victoria|Melbourne|Telstra|landline
617|Queensland|Brisbane|Telstra|landline
618|Western Australia|Perth|Telstra|landline
614|Australia|Mobile|Various|mobile"""
for line in au_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "AU", "Australia", lt, carrier, region=region, city=city)

# === China ===
cn_data = """8610|Beijing|Beijing|China Telecom|landline
8621|Shanghai|Shanghai|China Telecom|landline
8620|Guangdong|Guangzhou|China Telecom|landline
8613|China|Mobile|China Mobile|mobile
8615|China|Mobile|China Mobile|mobile
8618|China|Mobile|China Mobile|mobile"""
for line in cn_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "CN", "China", lt, carrier, region=region, city=city)

# === Japan ===
jp_data = """813|Tokyo|Tokyo|NTT|landline
816|Osaka|Osaka|NTT|landline
8190|Japan|Mobile|NTT Docomo|mobile
8180|Japan|Mobile|SoftBank|mobile
8170|Japan|Mobile|KDDI|mobile"""
for line in jp_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "JP", "Japan", lt, carrier, region=region, city=city)

# === South Korea ===
kr_data = """822|Seoul|Seoul|KT|landline
8251|Busan|Busan|KT|landline
8210|South Korea|Mobile|SK Telecom|mobile
8211|South Korea|Mobile|KT|mobile"""
for line in kr_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "KR", "South Korea", lt, carrier, region=region, city=city)

# === Russia ===
ru_data = """7495|Moscow|Moscow|Rostelecom|landline
7499|Moscow|Moscow|Rostelecom|landline
7812|St. Petersburg|St. Petersburg|Rostelecom|landline
790|Russia|Mobile|MTS|mobile
791|Russia|Mobile|MTS|mobile
792|Russia|Mobile|MegaFon|mobile
796|Russia|Mobile|Beeline|mobile"""
for line in ru_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "RU", "Russia", lt, carrier, region=region, city=city)

# === South Africa ===
za_data = """2711|Gauteng|Johannesburg|Telkom|landline
2721|Western Cape|Cape Town|Telkom|landline
276|South Africa|Mobile|Vodacom|mobile
277|South Africa|Mobile|MTN|mobile
2782|South Africa|Mobile|Vodacom|mobile
2783|South Africa|Mobile|MTN|mobile"""
for line in za_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "ZA", "South Africa", lt, carrier, region=region, city=city)

# === Egypt ===
eg_data = """202|Cairo|Cairo|Telecom Egypt|landline
203|Alexandria|Alexandria|Telecom Egypt|landline
2010|Egypt|Mobile|Vodafone|mobile
2011|Egypt|Mobile|Etisalat|mobile
2012|Egypt|Mobile|Orange|mobile
2015|Egypt|Mobile|WE|mobile"""
for line in eg_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "EG", "Egypt", lt, carrier, region=region, city=city)

# === Kenya ===
ke_data = """25420|Nairobi|Nairobi|Telkom Kenya|landline
25470|Kenya|Mobile|Safaricom|mobile
25471|Kenya|Mobile|Safaricom|mobile
25472|Kenya|Mobile|Safaricom|mobile
25473|Kenya|Mobile|Airtel|mobile
25479|Kenya|Mobile|Safaricom|mobile"""
for line in ke_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "KE", "Kenya", lt, carrier, region=region, city=city)

# === Philippines ===
ph_data = """632|Metro Manila|Manila|PLDT|landline
639|Philippines|Mobile|Globe|mobile"""
for line in ph_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "PH", "Philippines", lt, carrier, region=region, city=city)

# === Indonesia ===
id_data = """6221|Jakarta|Jakarta|Telkom|landline
628|Indonesia|Mobile|Telkomsel|mobile"""
for line in id_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "ID", "Indonesia", lt, carrier, region=region, city=city)

# === Pakistan ===
pk_data = """9221|Sindh|Karachi|PTCL|landline
9242|Punjab|Lahore|PTCL|landline
923|Pakistan|Mobile|Jazz|mobile"""
for line in pk_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "PK", "Pakistan", lt, carrier, region=region, city=city)

# === Bangladesh ===
bd_data = """8802|Dhaka|Dhaka|BTCL|landline
8801|Bangladesh|Mobile|Grameenphone|mobile"""
for line in bd_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "BD", "Bangladesh", lt, carrier, region=region, city=city)

# === Vietnam ===
vn_data = """8424|Hanoi|Hanoi|VNPT|landline
8428|Ho Chi Minh City|Ho Chi Minh City|VNPT|landline
849|Vietnam|Mobile|Viettel|mobile
843|Vietnam|Mobile|Viettel|mobile"""
for line in vn_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "VN", "Vietnam", lt, carrier, region=region, city=city)

# === Thailand ===
th_data = """662|Bangkok|Bangkok|TOT|landline
668|Thailand|Mobile|AIS|mobile
669|Thailand|Mobile|TrueMove|mobile"""
for line in th_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "TH", "Thailand", lt, carrier, region=region, city=city)

# === Malaysia ===
my_data = """603|Kuala Lumpur|Kuala Lumpur|Telekom Malaysia|landline
601|Malaysia|Mobile|Maxis|mobile"""
for line in my_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "MY", "Malaysia", lt, carrier, region=region, city=city)

# === Singapore ===
add("65", "SG", "Singapore", "landline", "Singtel", region="Singapore", city="Singapore")

# === UAE ===
ae_data = """9712|Abu Dhabi|Abu Dhabi|Etisalat|landline
9714|Dubai|Dubai|Du|landline
97150|UAE|Mobile|Etisalat|mobile
97155|UAE|Mobile|Du|mobile"""
for line in ae_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "AE", "United Arab Emirates", lt, carrier, region=region, city=city)

# === Saudi Arabia ===
sa_data = """96611|Riyadh|Riyadh|STC|landline
96612|Makkah|Jeddah|STC|landline
9665|Saudi Arabia|Mobile|STC|mobile"""
for line in sa_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "SA", "Saudi Arabia", lt, carrier, region=region, city=city)

# === Turkey ===
tr_data = """90212|Istanbul|Istanbul|Turk Telekom|landline
90312|Ankara|Ankara|Turk Telekom|landline
905|Turkey|Mobile|Turkcell|mobile"""
for line in tr_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "TR", "Turkey", lt, carrier, region=region, city=city)

# === Poland ===
pl_data = """4822|Mazovia|Warsaw|Orange|landline
4812|Lesser Poland|Krakow|Orange|landline
485|Poland|Mobile|Play|mobile
486|Poland|Mobile|T-Mobile|mobile
487|Poland|Mobile|Plus|mobile
488|Poland|Mobile|Orange|mobile"""
for line in pl_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "PL", "Poland", lt, carrier, region=region, city=city)

# === Netherlands ===
nl_data = """3120|North Holland|Amsterdam|KPN|landline
3110|South Holland|Rotterdam|KPN|landline
316|Netherlands|Mobile|KPN|mobile"""
for line in nl_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "NL", "Netherlands", lt, carrier, region=region, city=city)

# === Belgium ===
be_data = """322|Brussels|Brussels|Proximus|landline
323|Flanders|Antwerp|Proximus|landline
329|Flanders|Ghent|Proximus|landline
324|Belgium|Mobile|Proximus|mobile"""
for line in be_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "BE", "Belgium", lt, carrier, region=region, city=city)

# === Switzerland ===
ch_data = """4144|Zurich|Zurich|Swisscom|landline
4122|Geneva|Geneva|Swisscom|landline
4178|Switzerland|Mobile|Swisscom|mobile
4179|Switzerland|Mobile|Sunrise|mobile"""
for line in ch_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "CH", "Switzerland", lt, carrier, region=region, city=city)

# === Austria ===
at_data = """431|Vienna|Vienna|A1|landline
43660|Austria|Mobile|Drei|mobile
43664|Austria|Mobile|A1|mobile
43676|Austria|Mobile|Magenta|mobile"""
for line in at_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "AT", "Austria", lt, carrier, region=region, city=city)

# === Sweden ===
se_data = """468|Stockholm|Stockholm|Telia|landline
4631|Vastra Gotaland|Gothenburg|Telia|landline
4670|Sweden|Mobile|Telia|mobile
4673|Sweden|Mobile|Tele2|mobile"""
for line in se_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "SE", "Sweden", lt, carrier, region=region, city=city)

# === Norway ===
no_data = """472|Oslo|Oslo|Telenor|landline
474|Norway|Mobile|Telenor|mobile
479|Norway|Mobile|Telia|mobile"""
for line in no_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "NO", "Norway", lt, carrier, region=region, city=city)

# === Denmark ===
dk_data = """453|Copenhagen|Copenhagen|TDC|landline
452|Denmark|Mobile|TDC|mobile"""
for line in dk_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "DK", "Denmark", lt, carrier, region=region, city=city)

# === Finland ===
fi_data = """3589|Uusimaa|Helsinki|Elisa|landline
3584|Finland|Mobile|Elisa|mobile
3585|Finland|Mobile|DNA|mobile"""
for line in fi_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "FI", "Finland", lt, carrier, region=region, city=city)

# === Ireland ===
ie_data = """3531|Dublin|Dublin|Eir|landline
35321|Cork|Cork|Eir|landline
35383|Ireland|Mobile|Three|mobile
35385|Ireland|Mobile|Vodafone|mobile
35386|Ireland|Mobile|O2|mobile
35387|Ireland|Mobile|Eir|mobile
35389|Ireland|Mobile|Three|mobile"""
for line in ie_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "IE", "Ireland", lt, carrier, region=region, city=city)

# === Portugal ===
pt_data = """35121|Lisbon|Lisbon|MEO|landline
35122|Porto|Porto|MEO|landline
35191|Portugal|Mobile|Vodafone|mobile
35192|Portugal|Mobile|MEO|mobile
35193|Portugal|Mobile|NOS|mobile
35196|Portugal|Mobile|Vodafone|mobile"""
for line in pt_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "PT", "Portugal", lt, carrier, region=region, city=city)

# === Greece ===
gr_data = """3021|Attica|Athens|OTE|landline
30231|Central Macedonia|Thessaloniki|OTE|landline
3069|Greece|Mobile|Cosmote|mobile"""
for line in gr_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "GR", "Greece", lt, carrier, region=region, city=city)

# === Argentina ===
ar_data = """5411|Buenos Aires|Buenos Aires|Telecom Argentina|landline
54911|Buenos Aires|Mobile|Personal|mobile"""
for line in ar_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "AR", "Argentina", lt, carrier, region=region, city=city)

# === Colombia ===
co_data = """571|Bogota|Bogota|Telecom|landline
573|Colombia|Mobile|Claro|mobile"""
for line in co_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "CO", "Colombia", lt, carrier, region=region, city=city)

# === Chile ===
cl_data = """562|Santiago|Santiago|Entel|landline
569|Chile|Mobile|Entel|mobile"""
for line in cl_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "CL", "Chile", lt, carrier, region=region, city=city)

# === New Zealand ===
nz_data = """649|Auckland|Auckland|Spark|landline
644|Wellington|Wellington|Spark|landline
642|New Zealand|Mobile|Spark|mobile"""
for line in nz_data.strip().split("\n"):
    p, region, city, carrier, lt = line.split("|")
    add(p, "NZ", "New Zealand", lt, carrier, region=region, city=city)

# === VoIP / Burner prefixes (US-based, after NANPA country code) ===
voip_data = """1201300|US|VoIP|Google Voice|voip
1201301|US|VoIP|Google Voice|voip
1206488|US|VoIP|Google Voice|voip
1312335|US|VoIP|Google Voice|voip
1347738|US|VoIP|Google Voice|voip
1424364|US|VoIP|Google Voice|voip
1424777|US|VoIP|Google Voice|voip
1442200|US|VoIP|Google Voice|voip
1469499|US|VoIP|Google Voice|voip
1484373|US|VoIP|Google Voice|voip
1503900|US|VoIP|Google Voice|voip
1510290|US|VoIP|Google Voice|voip
1513272|US|VoIP|Google Voice|voip
1516218|US|VoIP|Google Voice|voip
1520352|US|VoIP|Google Voice|voip
1530330|US|VoIP|Google Voice|voip
1540205|US|VoIP|Google Voice|voip
1570543|US|VoIP|Google Voice|voip
1608420|US|VoIP|Google Voice|voip
1614401|US|VoIP|Google Voice|voip
1617468|US|VoIP|Google Voice|voip
1628232|US|VoIP|Google Voice|voip
1646571|US|VoIP|Google Voice|voip
1661216|US|VoIP|Google Voice|voip
1678698|US|VoIP|Google Voice|voip
1702472|US|VoIP|Google Voice|voip
1713263|US|VoIP|Google Voice|voip
1720534|US|VoIP|Google Voice|voip
1727302|US|VoIP|Google Voice|voip
1747200|US|VoIP|Google Voice|voip
1760542|US|VoIP|Google Voice|voip
1773707|US|VoIP|Google Voice|voip
1781514|US|VoIP|Google Voice|voip
1804394|US|VoIP|Google Voice|voip
1813358|US|VoIP|Google Voice|voip
1818203|US|VoIP|Google Voice|voip
1828384|US|VoIP|Google Voice|voip
1856404|US|VoIP|Google Voice|voip
1863592|US|VoIP|Google Voice|voip
1904238|US|VoIP|Google Voice|voip
1909493|US|VoIP|Google Voice|voip
1916603|US|VoIP|Google Voice|voip
1919752|US|VoIP|Google Voice|voip
1925236|US|VoIP|Google Voice|voip
1929205|US|VoIP|Google Voice|voip
1949380|US|VoIP|Google Voice|voip"""
for line in voip_data.strip().split("\n"):
    parts = line.split("|")
    p, country, lt, carrier = parts[0], parts[1], parts[2], parts[3]
    add(p, country, "United States", lt, carrier)

# === TextNow VoIP prefixes ===
textnow_data = """1336200|US|VoIP|TextNow|voip
1336201|US|VoIP|TextNow|voip
1424245|US|VoIP|TextNow|voip
1442229|US|VoIP|TextNow|voip
1469266|US|VoIP|TextNow|voip
1503994|US|VoIP|TextNow|voip
1617944|US|VoIP|TextNow|voip
1628227|US|VoIP|TextNow|voip
1682218|US|VoIP|TextNow|voip
1720496|US|VoIP|TextNow|voip
1747225|US|VoIP|TextNow|voip
1760933|US|VoIP|TextNow|voip
1781496|US|VoIP|TextNow|voip
1813797|US|VoIP|TextNow|voip
1818660|US|VoIP|TextNow|voip
1857274|US|VoIP|TextNow|voip
1862240|US|VoIP|TextNow|voip
1904999|US|VoIP|TextNow|voip
1917280|US|VoIP|TextNow|voip
1929366|US|VoIP|TextNow|voip"""
for line in textnow_data.strip().split("\n"):
    parts = line.split("|")
    p, country, lt, carrier = parts[0], parts[1], parts[2], parts[3]
    add(p, country, "United States", lt, carrier)

# === Additional VoIP/Burner ===
add("1500", "US", "United States", "voip", "Bandwidth.com")
add("1521", "US", "United States", "voip", "Vonage")
add("1522", "US", "United States", "voip", "Twilio")
add("1523", "US", "United States", "voip", "Twilio")

# === Country-code-only fallbacks (least specific) ===
