package com.example.signtr2

class NLG {
    private val unluler = "aeıioöuü"

    private val genisUyum = mapOf(
        'a' to 'a', 'ı' to 'a', 'o' to 'a', 'u' to 'a',
        'e' to 'e', 'i' to 'e', 'ö' to 'e', 'ü' to 'e'
    )

    private val darUyum = mapOf(
        'a' to 'ı', 'ı' to 'ı', 'o' to 'u', 'u' to 'u',
        'e' to 'i', 'i' to 'i', 'ö' to 'ü', 'ü' to 'ü'
    )

    private val ozneKelimeleri = mapOf(
        "ben" to "Ben", "sen" to "Sen", "o" to "O",
        "biz" to "Biz", "siz" to "Siz", "onlar" to "Onlar"
    )

    private val soruKelimeleri = setOf("nasıl", "neden", "nerede", "kim")
    private val olumsuzKelimeleri = setOf("hayır")
    private val gecmisZamanlari = setOf("dun", "geçmiş")
    private val gelecekZamanlari = setOf("yarın")

    private val kaliplar = mapOf(
        "teşekkür" to "Teşekkür ederim.",
        "geçmiş olsun" to "Geçmiş olsun.",
        "afiyet olsun" to "Afiyet olsun.",
        "hayırlı olsun" to "Hayırlı olsun.",
        "selam" to "Selam.",
        "hoşçakal" to "Hoşça kal.",
        "rica etmek" to "Rica ederim.",
        "maşallah" to "Maşallah.",
        "özür dilemek" to "Özür dilerim.",
        "bilgi vermek" to "Bilgi vereyim."
    )

    private val fiilKokleri = mapOf(
        "acıkmak" to "acık", "ağlamak" to "ağla", "bakmak" to "bak",
        "beklemek" to "bekle", "bilgi vermek" to "ver", "çalışmak" to "çalış",
        "değiştirmek" to "değiştir", "devirmek" to "devir", "ezberlemek" to "ezbele",
        "getirmek" to "getir", "görmek" to "gör", "göstermek" to "göster",
        "gülmek" to "gül", "içmek" to "iç", "ilgilenmemek" to "ilgilenme",
        "itmek" to "it", "kaçmak" to "kaç", "memnun olmak" to "ol",
        "özür dilemek" to "dile", "rica etmek" to "et", "sevmek" to "sev",
        "söylemek" to "söyle", "yapmak" to "yap", "yemek pişirmek" to "pişir"
    )

    private val otomatikGecmis = setOf("acıkmak")
    private val yonelmeFiilleri = setOf("getirmek", "kaçmak")

    private val mekanlar = setOf(
        "ev", "okul", "hastane", "eczane", "bahçe", "Türkiye",
        "pazar", "orman", "oda", "tuvalet", "kavsak", "köprü", "yol"
    )

    private val sifatlar = setOf(
        "iyi", "kotu", "çirkin", "hafif", "ağır", "dolu", "akıllı", "akılsız",
        "üzgün", "hasta", "yorgun", "evli", "bekar", "serbest", "yanlış",
        "haklı", "yalnız", "emekli", "tatlı", "turuncu", "yasak", "uzak",
        "yakın", "zor", "hep", "hiç", "beraber", "ayni", "keşke", "tamam",
        "evet", "olur", "olmaz", "var", "yok", "yavaş", "helal"
    )

    private val nesneIsimleri = setOf(
        "bardak", "kalem", "kitap", "para", "telefon", "anahtar", "ilaç",
        "havlu", "sabun", "masa", "hediye", "yastık", "yatak", "tarak", "makas",
        "kemer", "ayakkabı", "gömlek", "pantolon", "şapka", "eldiven", "şemsiye",
        "cüzdan", "kimlik", "senet", "fotoğraf", "bayrak", "çaydanlık", "çatal",
        "çekiç", "tornavida", "iğne", "yarabandı", "mendil", "kolonya", "pamuk",
        "leke", "koku", "sut", "çay", "et", "kıyma", "patates", "yumurta", "bal",
        "salca", "un", "pastırma", "tatlı", "çorba", "seker", "yemek", "odun",
        "kömür", "benzin", "vergi", "kira", "maaş"
    )

    private val simdikiSahis = mapOf("Ben" to "um", "Sen" to "sun", "O" to "", "Biz" to "uz", "Siz" to "sunuz", "Onlar" to "lar")
    private val gecmisSahis = mapOf("Ben" to "m", "Sen" to "n", "O" to "", "Biz" to "k", "Onlar" to "")

    private fun sonUnlu(s: String): Char {
        return s.lastOrNull { it in unluler } ?: 'a'
    }

    private fun sertMi(s: String) = s.isNotEmpty() && s.last() in "fstkçşhp"
    private fun kokAl(fiil: String) = fiilKokleri[fiil] ?: fiil

    private fun simdiki(kok: String, ozne: String): String {
        var islenmisKok = kok
        if (islenmisKok.isNotEmpty() && islenmisKok.last() in unluler) {
            islenmisKok = islenmisKok.dropLast(1)
        }
        if (islenmisKok.endsWith("t") && islenmisKok != "it") {
            islenmisKok = islenmisKok.dropLast(1) + "d"
        }

        val u = darUyum[sonUnlu(islenmisKok)]
        val ek = "${u}yor"

        return if (ozne == "Onlar") "$islenmisKok${ek}lar" else "$islenmisKok$ek${simdikiSahis[ozne] ?: ""}"
    }

    private fun gecmis(kok: String, ozne: String): String {
        val u = darUyum[sonUnlu(kok)]
        val d = if (sertMi(kok)) "t" else "d"
        val ek = "$d$u"

        if (ozne == "Onlar") {
            val cogul = if (genisUyum[sonUnlu(kok + ek)] == 'a') "lar" else "ler"
            return "$kok$ek$cogul"
        }
        if (ozne == "Siz") return "$kok${ek}n${u}z"
        return "$kok$ek${gecmisSahis[ozne] ?: ""}"
    }

    private fun gelecek(kok: String, ozne: String): String {
        var islenmisKok = kok
        if (islenmisKok.isNotEmpty() && islenmisKok.last() in unluler) {
            islenmisKok += "y"
        }
        if (islenmisKok.endsWith("t") && islenmisKok != "it") {
            islenmisKok = islenmisKok.dropLast(1) + "d"
        }

        val u = genisUyum[sonUnlu(islenmisKok)]
        val govde = islenmisKok + (if (u == 'a') "acak" else "ecek")

        if (ozne == "O") return govde
        if (ozne == "Onlar") return govde + (if (u == 'a') "lar" else "ler")

        val dar = darUyum[sonUnlu(govde)]
        return when (ozne) {
            "Ben" -> govde.dropLast(1) + "ğ${dar}m"
            "Biz" -> govde.dropLast(1) + "ğ${dar}z"
            "Sen" -> govde + "s${dar}n"
            "Siz" -> govde + "s${dar}n${dar}z"
            else -> govde
        }
    }

    private fun olumsuzKok(kok: String, zaman: String): String {
        val uGenis = genisUyum[sonUnlu(kok)]
        val ma = if (uGenis == 'a') "ma" else "me"

        return if (zaman == "simdiki") {
            kok + "m" + darUyum[sonUnlu(kok)]
        } else {
            kok + ma + "y"
        }
    }

    private fun fiilCek(fiil: String, ozne: String, zaman: String, olumsuz: Boolean = false): String {
        val gercekZaman = if (fiil in otomatikGecmis) "gecmis" else zaman
        var kok = kokAl(fiil)

        if (olumsuz) kok = olumsuzKok(kok, gercekZaman)

        return when (gercekZaman) {
            "gecmis" -> gecmis(kok, ozne)
            "gelecek" -> gelecek(kok, ozne)
            else -> simdiki(kok, ozne)
        }
    }

    private fun lokatif(mekan: String): String {
        val ek = if (genisUyum[sonUnlu(mekan)] == 'a') "a" else "e"
        val d = if (sertMi(mekan)) "t" else "d"
        return "$mekan$d$ek"
    }

    private fun yonelme(mekan: String): String {
        val ek = if (genisUyum[sonUnlu(mekan)] == 'a') "a" else "e"
        return if (mekan.isNotEmpty() && mekan.last() in unluler) "${mekan}y$ek" else "$mekan$ek"
    }

    private fun belirtme(isim: String): String {
        val u = darUyum[sonUnlu(isim)]
        if (isim.isNotEmpty() && isim.last() in unluler) return "${isim}y$u"

        val yumusama = mapOf('p' to 'b', 'ç' to 'c', 't' to 'd', 'k' to 'ğ')
        if (isim.isNotEmpty() && isim.last() in yumusama.keys) {
            return "${isim.dropLast(1)}${yumusama[isim.last()]}$u"
        }
        return "$isim$u"
    }

    private fun isimCumlesi(yuklem: String, ozne: String, zaman: String, olumsuz: Boolean): String {
        if (olumsuz) return isimCumlesiDegil(ozne, zaman)

        val dar = darUyum[sonUnlu(yuklem)]
        val kaynastirma = if (yuklem.isNotEmpty() && yuklem.last() in unluler) "y" else ""
        val cogulEk = if (genisUyum[sonUnlu(yuklem)] == 'a') "lar" else "ler"

        if (zaman == "gecmis") {
            val ek = "${kaynastirma}d$dar"
            return when (ozne) {
                "Ben" -> "$yuklem${ek}m"
                "Sen" -> "$yuklem${ek}n"
                "O" -> "$yuklem$ek"
                "Biz" -> "$yuklem${ek}k"
                "Siz" -> "$yuklem${ek}n${dar}z"
                "Onlar" -> "$yuklem$cogulEk$ek"
                else -> yuklem
            }
        } else {
            return when (ozne) {
                "O" -> yuklem
                "Onlar" -> "$yuklem$cogulEk"
                "Ben" -> "$yuklem$kaynastirma${dar}m"
                "Sen" -> "${yuklem}s${dar}n"
                "Biz" -> "$yuklem$kaynastirma${dar}z"
                "Siz" -> "${yuklem}s${dar}n${dar}z"
                else -> yuklem
            }
        }
    }

    private fun isimCumlesiDegil(ozne: String, zaman: String): String {
        val ekler = if (zaman == "gecmis") {
            mapOf("Ben" to "dim", "Sen" to "din", "O" to "di", "Biz" to "dik", "Siz" to "diniz", "Onlar" to "lerdi")
        } else {
            mapOf("Ben" to "im", "Sen" to "sin", "O" to "", "Biz" to "iz", "Siz" to "siniz", "Onlar" to "ler")
        }
        return "değil${ekler[ozne] ?: ""}"
    }

    fun cumleKur(kelimelerInput: List<String>): String {
        if (kelimelerInput.isEmpty()) return ""

        val cokKelimeli = (fiilKokleri.keys.filter { " " in it } + kaliplar.keys.filter { " " in it })
            .sortedByDescending { it.length }

        var i = 0
        val kelimeler = mutableListOf<String>()
        while (i < kelimelerInput.size) {
            val eslesen = cokKelimeli.firstOrNull { ck ->
                val parcalar = ck.split(" ")
                i + parcalar.size <= kelimelerInput.size && kelimelerInput.subList(i, i + parcalar.size) == parcalar
            }

            if (eslesen != null) {
                kelimeler.add(eslesen)
                i += eslesen.split(" ").size
            } else {
                kelimeler.add(kelimelerInput[i])
                i++
            }
        }

        for (k in kelimeler) {
            if (k in kaliplar) return kaliplar[k]!!
        }

        val birlesmis = kelimeler.joinToString(" ")
        val tamKalip = kaliplar.entries.firstOrNull { birlesmis == it.key || birlesmis.startsWith(it.key) }
        if (tamKalip != null) return tamKalip.value

        val ozne = kelimeler.firstNotNullOfOrNull { ozneKelimeleri[it.lowercase()] } ?: "O"
        val zaman = kelimeler.firstNotNullOfOrNull {
            if (it in gecmisZamanlari) "gecmis" else if (it in gelecekZamanlari) "gelecek" else null
        } ?: "simdiki"

        val soruVar = kelimeler.any { it in soruKelimeleri }
        val olumsuz = kelimeler.any { it in olumsuzKelimeleri }

        val atlanacaklar = ozneKelimeleri.keys + olumsuzKelimeleri + kaliplar.keys

        val ogeler = mutableMapOf<String, MutableList<String>>(
            "soru" to mutableListOf(), "zaman_zarfi" to mutableListOf(),
            "mekan" to mutableListOf(), "sifat" to mutableListOf(),
            "nesne" to mutableListOf(), "fiil" to mutableListOf()
        )

        for (k in kelimeler) {
            val kl = k.lowercase()
            if (kl in atlanacaklar || k in atlanacaklar) continue

            when {
                k in soruKelimeleri -> ogeler["soru"]!!.add(k)
                k == "dun" -> ogeler["zaman_zarfi"]!!.add("dün")
                k == "yarın" -> ogeler["zaman_zarfi"]!!.add("yarın")
                k == "geçmiş" -> ogeler["zaman_zarfi"]!!.add("geçmişte")
                k in fiilKokleri -> {
                    val cekimli = fiilCek(k, ozne, zaman, olumsuz)
                    if (" " in k) {
                        val onKelime = k.substringBeforeLast(" ")
                        ogeler["fiil"]!!.add("$onKelime $cekimli")
                    } else {
                        ogeler["fiil"]!!.add(cekimli)
                    }
                }
                k in mekanlar || k == "Türkiye" -> {
                    if (soruVar) ogeler["mekan"]!!.add(k)
                    else {
                        val yonelmeMi = kelimeler.any { it in yonelmeFiilleri }
                        ogeler["mekan"]!!.add(if (yonelmeMi) yonelme(k) else lokatif(k))
                    }
                }
                k in sifatlar -> ogeler["sifat"]!!.add(k)
                k in nesneIsimleri -> ogeler["nesne"]!!.add(if (soruVar) k else belirtme(k))
                else -> ogeler["nesne"]!!.add(k)
            }
        }

        if (ogeler["fiil"]!!.isEmpty()) {
            val potansiyelYuklemler = ogeler["sifat"]!!.ifEmpty { ogeler["nesne"]!!.ifEmpty { ogeler["mekan"]!! } }
            if (potansiyelYuklemler.isNotEmpty() && !soruVar) {
                val yuklem = potansiyelYuklemler.last()

                ogeler["sifat"]!!.remove(yuklem)
                ogeler["nesne"]!!.remove(yuklem)
                ogeler["mekan"]!!.remove(yuklem)

                if (olumsuz && yuklem != "yok") {
                    ogeler["fiil"]!!.add(yuklem)
                    ogeler["fiil"]!!.add(isimCumlesiDegil(ozne, zaman))
                } else {
                    ogeler["fiil"]!!.add(isimCumlesi(yuklem, ozne, zaman, false))
                }
            }
        }

        val sonucListesi = mutableListOf<String>()
        if (ozne != "O") sonucListesi.add(ozne)

        sonucListesi.addAll(ogeler["zaman_zarfi"]!!)
        sonucListesi.addAll(ogeler["mekan"]!!)
        sonucListesi.addAll(ogeler["sifat"]!!)
        sonucListesi.addAll(ogeler["nesne"]!!)
        sonucListesi.addAll(ogeler["fiil"]!!)
        sonucListesi.addAll(ogeler["soru"]!!)

        if (sonucListesi.isEmpty()) return ""

        val ilkKelime = sonucListesi[0]
        if (ilkKelime.isNotEmpty()) {
            val ilkHarf = if (ilkKelime[0] == 'i') "İ" else ilkKelime[0].uppercase()
            sonucListesi[0] = ilkHarf + ilkKelime.drop(1)
        }

        return sonucListesi.joinToString(" ") + (if (soruVar) "?" else ".")
    }
}